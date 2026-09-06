package com.lis.wear.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.lis.wear.data.LisRepository
import com.lis.wear.model.Book
import com.lis.wear.model.BookProgress
import com.lis.wear.model.PlayerState
import com.lis.wear.model.SleepTimer
import com.lis.wear.tts.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Playback brain: owns the book, chapter and sentence cursor, and drives
 * [TtsEngine] one sentence at a time.
 *
 * Threading: every mutation runs on the main thread. TTS callbacks arrive on the
 * engine's own thread and are posted back here, so [state] and the Media3 player
 * never observe a torn state.
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
    /** Cached once per book: rebuilding this on every publish caused heavy GC churn. */
    private var chapterTitlesCache: List<String> = emptyList()
    private var chapterIndex = 0
    private var sentenceIndex = 0
    private var playing = false
    private var preparing = false
    private var speed = 1.0f
    private var pitch = 1.0f
    private var initStarted = false
    private var pendingPlay = false
    private var currentUtteranceId: String? = null
    private var lastError: String? = null

    private var sleepTimer = SleepTimer.OFF
    private var sleepTimerEndsAt = 0L
    private var sleepJob: Job? = null

    /** Watchdog: if TTS never reports back, resume the next sentence anyway. */
    private var watchdog: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Fired on every state change (used to refresh the MediaSession + tile). */
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

    val isPlaying: Boolean get() = playing

    // region lifecycle

    suspend fun ensureTts(): Boolean {
        if (tts.isReady) return true
        if (initStarted) return tts.isReady
        initStarted = true
        val ok = tts.init()
        tts.setSpeed(speed)
        tts.setPitch(pitch)
        publish()
        return ok
    }

    fun release() {
        persistProgress()
        watchdog?.cancel()
        sleepJob?.cancel()
        tts.setCallbacks(null)
        tts.shutdown()
    }

    // endregion

    // region book selection

    suspend fun openBook(bookId: String, autoPlay: Boolean) {
        if (book?.id == bookId) {
            if (autoPlay && !playing) play()
            return
        }
        preparing = true
        publish()

        val loaded = repository.loadBook(bookId)
        if (loaded == null || loaded.chapters.isEmpty()) {
            preparing = false
            lastError = "书籍数据丢失"
            publish()
            return
        }
        val progress = repository.progressFor(bookId)
        stopSpeaking()

        book = loaded
        chapterTitlesCache = loaded.chapters.map { it.title }
        chapterIndex = progress.chapterIndex.coerceIn(0, loaded.chapters.lastIndex)
        sentenceIndex = progress.sentenceIndex
            .coerceIn(0, maxOf(0, loaded.chapters[chapterIndex].sentences.lastIndex))
        preparing = false
        lastError = null
        publish()

        if (autoPlay) play()
    }

    suspend fun resumeLast(autoPlay: Boolean): Boolean {
        val id = repository.currentLastBookId() ?: return false
        openBook(id, autoPlay)
        return book != null
    }

    // endregion

    // region transport

    fun play() {
        val b = book ?: return
        if (b.chapters.isEmpty()) return

        if (!tts.isReady) {
            // Optimistically flip to playing so the UI reacts on the first tap.
            pendingPlay = true
            preparing = true
            playing = true
            publish()
            scope.launch { ensureTts() }
            return
        }
        playing = true
        preparing = false
        publish()
        speakCurrent()
    }

    fun pause() {
        pendingPlay = false
        playing = false
        preparing = false
        stopSpeaking()
        publish()
        persistProgress()
    }

    fun togglePlayPause() {
        if (playing) pause() else play()
    }

    fun stop() {
        pendingPlay = false
        playing = false
        preparing = false
        stopSpeaking()
        publish()
        persistProgress()
    }

    fun nextChapter(): Boolean = jumpChapter(chapterIndex + 1)

    fun previousChapter(): Boolean =
        if (sentenceIndex > 2) {
            seekToSentence(0); true
        } else {
            jumpChapter(chapterIndex - 1)
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

    // endregion

    // region settings

    fun setSpeed(value: Float) {
        speed = value.coerceIn(0.5f, 2.5f)
        tts.setSpeed(speed)
        scope.launch { repository.setSpeed(speed) }
        publish()
        if (playing && tts.isReady) speakCurrent()
    }

    fun setPitch(value: Float) {
        pitch = value.coerceIn(0.6f, 1.6f)
        tts.setPitch(pitch)
        scope.launch { repository.setPitch(pitch) }
        publish()
        if (playing && tts.isReady) speakCurrent()
    }

    fun setSleepTimer(timer: SleepTimer) {
        sleepTimer = timer
        sleepJob?.cancel()
        sleepJob = null
        sleepTimerEndsAt = 0L

        if (timer.minutes > 0) {
            val durationMs = timer.minutes * 60_000L
            sleepTimerEndsAt = System.currentTimeMillis() + durationMs
            sleepJob = scope.launch {
                delay(durationMs)
                sleepTimer = SleepTimer.OFF
                sleepTimerEndsAt = 0L
                pause()
            }
        }
        publish()
    }

    // endregion

    // region TtsEngine.Callbacks

    override fun onUtteranceDone(id: String) {
        main.post {
            if (id != currentUtteranceId || !playing) return@post
            advance()
        }
    }

    override fun onUtteranceError(id: String) {
        main.post {
            if (id != currentUtteranceId) return@post
            Log.w(TAG, "utterance failed at $chapterIndex/$sentenceIndex, skipping")
            if (playing) advance()
        }
    }

    override fun onReady(success: Boolean) {
        main.post {
            preparing = false
            if (!success) {
                playing = false
                pendingPlay = false
                lastError = "系统 TTS 不可用"
                publish()
                return@post
            }
            tts.setSpeed(speed)
            tts.setPitch(pitch)
            if (pendingPlay || playing) {
                pendingPlay = false
                playing = true
                publish()
                speakCurrent()
            } else {
                publish()
            }
        }
    }

    // endregion

    private fun advance() {
        val chapter = currentChapter() ?: return
        if (sentenceIndex + 1 <= chapter.sentences.lastIndex) {
            sentenceIndex++
            publish()
            persistProgress()
            speakCurrent()
            return
        }

        // chapter finished
        if (sleepTimer == SleepTimer.CHAPTER_END) {
            sleepTimer = SleepTimer.OFF
            pause()
            return
        }
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
            lastError = "朗读失败"
            publish()
            return
        }
        armWatchdog(id, text)
    }

    /**
     * TTS engines occasionally swallow onDone (especially after audio focus
     * changes). Estimate the utterance length and move on if nothing came back.
     */
    private fun armWatchdog(id: String, text: String) {
        watchdog?.cancel()
        val estimateMs = (text.length.coerceAtLeast(4) * 260L / speed.coerceAtLeast(0.3f)).toLong()
        val timeoutMs = (estimateMs + 6_000L).coerceAtMost(90_000L)
        watchdog = scope.launch {
            delay(timeoutMs)
            if (playing && currentUtteranceId == id) {
                Log.w(TAG, "watchdog fired for $id after ${timeoutMs}ms")
                advance()
            }
        }
    }

    private fun stopSpeaking() {
        currentUtteranceId = null
        watchdog?.cancel()
        watchdog = null
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
            chapterTitles = chapterTitlesCache,
            sentenceIndex = sentenceIndex,
            sentences = chapter?.sentences ?: emptyList(),
            isPlaying = playing,
            isPreparing = preparing,
            speed = speed,
            pitch = pitch,
            sleepTimer = sleepTimer,
            sleepTimerEndsAt = sleepTimerEndsAt,
            error = lastError,
        )
        onStateChanged?.invoke()
    }

    companion object {
        private const val TAG = "LisEngine"
    }
}