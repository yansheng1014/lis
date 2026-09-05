package com.lis.wear.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.lis.wear.data.LisRepository
import com.lis.wear.model.Book
import com.lis.wear.model.BookProgress
import com.lis.wear.model.PlayerState
import com.lis.wear.tts.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * The playback brain: owns the current book, chapter and sentence cursor and
 * drives [TtsEngine] one sentence at a time.
 *
 * All mutations happen on the main thread so the state flow and the MediaSession
 * player stay consistent. TTS callbacks are posted back to the main thread.
 */
class TtsBookEngine(
    context: Context,
    private val repository: LisRepository,
) : TtsEngine.Callbacks {

    private val appContext = context.applicationContext
    private val tts = TtsEngine(appContext)
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val utteranceCounter = AtomicLong(0)

    private var book: Book? = null
    private var chapterIndex = 0
    private var sentenceIndex = 0
    private var playing = false
    private var preparing = false
    private var speed = 1.0f
    private var pitch = 1.0f
    private var initialised = false
    private var pendingPlayAfterInit = false
    private var currentUtteranceId: String? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Notified whenever the flattened state changes (used by the media player). */
    var onStateChanged: (() -> Unit)? = null

    init {
        tts.setCallbacks(this)
        scope.launch {
            speed = repository.currentSpeed()
            pitch = repository.currentPitch()
            tts.setSpeed(speed)
            tts.setPitch(pitch)
            publish()
        }
    }

    // region public API

    suspend fun ensureTts(): Boolean {
        if (initialised) return tts.isReady
        initialised = true
        val ok = tts.init()
        tts.setSpeed(speed)
        tts.setPitch(pitch)
        publish()
        return ok
    }

    /** Load a book, restoring the saved chapter/sentence position. */
    suspend fun openBook(bookId: String, autoPlay: Boolean) {
        if (book?.id == bookId) {
            if (autoPlay) play()
            return
        }
        preparing = true
        publish()
        val loaded = repository.loadBook(bookId)
        if (loaded == null) {
            preparing = false
            _state.value = _state.value.copy(error = "书籍数据丢失")
            publish()
            return
        }
        val progress = repository.progressFor(bookId)
        stopSpeaking()
        book = loaded
        chapterIndex = progress.chapterIndex.coerceIn(0, loaded.chapters.lastIndex)
        sentenceIndex = progress.sentenceIndex
            .coerceIn(0, maxOf(0, loaded.chapters[chapterIndex].sentences.lastIndex))
        preparing = false
        publish()
        if (autoPlay) play()
    }

    /** Continue whatever was last played, if anything. */
    suspend fun resumeLast(autoPlay: Boolean): Boolean {
        val id = repository.currentLastBookId() ?: return false
        openBook(id, autoPlay)
        return book != null
    }

    fun play() {
        val b = book ?: return
        if (b.chapters.isEmpty()) return
        if (!tts.isReady) {
            pendingPlayAfterInit = true
            preparing = true
            publish()
            scope.launch { ensureTts() }
            return
        }
        playing = true
        publish()
        speakCurrent()
    }

    fun pause() {
        playing = false
        stopSpeaking()
        publish()
        persistProgress()
    }

    fun togglePlayPause() {
        if (playing) pause() else play()
    }

    fun stop() {
        playing = false
        stopSpeaking()
        publish()
        persistProgress()
    }

    fun nextChapter(): Boolean = jumpChapter(chapterIndex + 1)

    fun previousChapter(): Boolean {
        // Mirror common audiobook behaviour: restart chapter if we're deep into it.
        return if (sentenceIndex > 2) {
            seekToSentence(0)
            true
        } else {
            jumpChapter(chapterIndex - 1)
        }
    }

    fun jumpChapter(target: Int): Boolean {
        val b = book ?: return false
        if (target !in b.chapters.indices) return false
        val wasPlaying = playing
        stopSpeaking()
        chapterIndex = target
        sentenceIndex = 0
        publish()
        persistProgress()
        if (wasPlaying) speakCurrent()
        return true
    }

    fun seekToSentence(target: Int) {
        val chapter = currentChapter() ?: return
        val clamped = target.coerceIn(0, maxOf(0, chapter.sentences.lastIndex))
        val wasPlaying = playing
        stopSpeaking()
        sentenceIndex = clamped
        publish()
        persistProgress()
        if (wasPlaying) speakCurrent()
    }

    fun nextSentence() = seekToSentence(sentenceIndex + 1)

    fun previousSentence() = seekToSentence(sentenceIndex - 1)

    fun setSpeed(value: Float) {
        speed = value.coerceIn(0.4f, 3.0f)
        tts.setSpeed(speed)
        scope.launch { repository.setSpeed(speed) }
        publish()
        // Restart the current sentence so the new rate is audible immediately.
        if (playing) speakCurrent()
    }

    fun setPitch(value: Float) {
        pitch = value.coerceIn(0.5f, 2.0f)
        tts.setPitch(pitch)
        scope.launch { repository.setPitch(pitch) }
        publish()
        if (playing) speakCurrent()
    }

    fun release() {
        persistProgress()
        tts.setCallbacks(null)
        tts.shutdown()
    }

    fun currentChapterSentences(): List<String> = currentChapter()?.sentences ?: emptyList()

    val isPlaying: Boolean get() = playing

    // endregion

    // region TtsEngine.Callbacks

    override fun onUtteranceDone(id: String) {
        main.post {
            if (id != currentUtteranceId || !playing) return@post
            advanceAfterSentence()
        }
    }

    override fun onUtteranceError(id: String) {
        main.post {
            if (id != currentUtteranceId) return@post
            // Skip the offending sentence instead of stalling the whole session.
            Log.w(TAG, "skip sentence after error at $chapterIndex/$sentenceIndex")
            if (playing) advanceAfterSentence()
        }
    }

    override fun onReady(success: Boolean) {
        main.post {
            preparing = false
            if (!success) {
                _state.value = _state.value.copy(error = "系统 TTS 不可用")
                publish()
                return@post
            }
            tts.setSpeed(speed)
            tts.setPitch(pitch)
            if (pendingPlayAfterInit) {
                pendingPlayAfterInit = false
                play()
            } else {
                publish()
            }
        }
    }

    // endregion

    private fun advanceAfterSentence() {
        val chapter = currentChapter() ?: return
        if (sentenceIndex + 1 <= chapter.sentences.lastIndex) {
            sentenceIndex++
            publish()
            persistProgress()
            speakCurrent()
            return
        }
        // chapter finished -> roll into the next one
        val b = book ?: return
        if (chapterIndex + 1 <= b.chapters.lastIndex) {
            chapterIndex++
            sentenceIndex = 0
            publish()
            persistProgress()
            speakCurrent()
        } else {
            playing = false
            publish()
            persistProgress()
        }
    }

    private fun speakCurrent() {
        val chapter = currentChapter() ?: return
        val text = chapter.sentences.getOrNull(sentenceIndex) ?: return
        val id = "u${utteranceCounter.incrementAndGet()}"
        currentUtteranceId = id
        val accepted = tts.speak(text, id)
        if (!accepted) {
            playing = false
            _state.value = _state.value.copy(error = "朗读失败")
            publish()
        }
    }

    private fun stopSpeaking() {
        currentUtteranceId = null
        tts.stop()
    }

    private fun currentChapter() = book?.chapters?.getOrNull(chapterIndex)

    private fun persistProgress() {
        val id = book?.id ?: return
        val snapshot = BookProgress(chapterIndex, sentenceIndex)
        scope.launch { repository.saveProgress(id, snapshot) }
    }

    private fun publish() {
        val b = book
        val chapter = currentChapter()
        _state.value = PlayerState(
            bookId = b?.id,
            bookTitle = b?.title.orEmpty(),
            chapterIndex = chapterIndex,
            chapterCount = b?.chapterCount ?: 0,
            chapterTitle = chapter?.title.orEmpty(),
            sentenceIndex = sentenceIndex,
            sentences = chapter?.sentences ?: emptyList(),
            isPlaying = playing,
            isPreparing = preparing,
            speed = speed,
            pitch = pitch,
            error = _state.value.error.takeIf { b == null },
        )
        onStateChanged?.invoke()
    }

    companion object {
        private const val TAG = "LisEngine"
    }
}