package com.lis.wear.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lis.wear.model.PlayerState

/**
 * Bridges the TTS engine to Media3 so the watch's system media controls, the
 * playback indicator and external media controllers can drive Lis.
 *
 * One chapter == one playlist item, which makes previous/next behave like
 * "previous/next episode". Position inside a chapter is estimated from the
 * sentence cursor, which is enough for a progress bar and coarse seeking.
 */
@UnstableApi
class TtsSessionPlayer(
    private val engine: TtsBookEngine,
    private val looper: Looper,
) : SimpleBasePlayer(looper) {

    private val handler = Handler(looper)

    // A 900-chapter book would otherwise rebuild ~900 MediaItemData objects on
    // every sentence change. Cache per book; durations are coarse anyway.
    private var cachedBookId: String? = null
    private var cachedCount: Int = 0
    private var cachedPlaylist: List<MediaItemData> = emptyList()

    private val commands: Player.Commands = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_RELEASE,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_SET_SPEED_AND_PITCH,
        )
        .build()

    /** Ask Media3 to re-read [getState]; safe to call from any thread. */
    fun requestStateRefresh() {
        if (Looper.myLooper() == looper) {
            runCatching { invalidateState() }
        } else {
            handler.post { runCatching { invalidateState() } }
        }
    }

    override fun getState(): State {
        val s = engine.state.value
        val builder = State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(s.isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackParameters(PlaybackParameters(s.speed, s.pitch))
            .setSeekBackIncrementMs(SENTENCE_STEP_MS)
            .setSeekForwardIncrementMs(SENTENCE_STEP_MS)

        if (!s.hasBook || s.chapterCount == 0) {
            return builder.setPlaybackState(Player.STATE_IDLE).build()
        }

        val playlist = buildPlaylist(s)
        return builder
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(s.chapterIndex.coerceIn(0, playlist.lastIndex))
            .setPlaybackState(
                if (s.isPreparing) Player.STATE_BUFFERING else Player.STATE_READY
            )
            .setContentPositionMs(positionMsFor(s))
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) engine.play() else engine.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        engine.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        engine.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(
        playbackParameters: PlaybackParameters
    ): ListenableFuture<*> {
        engine.setSpeed(playbackParameters.speed)
        engine.setPitch(playbackParameters.pitch)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        val s = engine.state.value
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> engine.nextChapter()

            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> engine.previousChapter()

            Player.COMMAND_SEEK_BACK -> engine.previousSentence()

            Player.COMMAND_SEEK_FORWARD -> engine.nextSentence()

            else -> {
                if (mediaItemIndex != C.INDEX_UNSET && mediaItemIndex != s.chapterIndex) {
                    engine.jumpChapter(mediaItemIndex)
                }
                if (positionMs > 0) {
                    engine.seekToSentence(sentenceIndexForPosition(engine.state.value, positionMs))
                } else if (mediaItemIndex == s.chapterIndex) {
                    engine.seekToSentence(0)
                }
            }
        }
        return Futures.immediateVoidFuture()
    }

    private fun buildPlaylist(s: PlayerState): List<MediaItemData> {
        if (cachedBookId == s.bookId &&
            cachedCount == s.chapterCount &&
            cachedPlaylist.isNotEmpty()
        ) {
            return cachedPlaylist
        }
        val items = (0 until s.chapterCount).map { index ->
            val title = s.titleForChapter(index)
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(s.bookTitle)
                .setAlbumTitle(s.bookTitle)
                .setDisplayTitle(title)
                .setSubtitle(s.bookTitle)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
            MediaItemData.Builder("${s.bookId}#$index")
                .setMediaItem(
                    MediaItem.Builder()
                        .setMediaId("${s.bookId}#$index")
                        .setMediaMetadata(metadata)
                        .build()
                )
                .setMediaMetadata(metadata)
                .setDurationUs(DEFAULT_CHAPTER_MS * 1000)
                .setIsSeekable(true)
                .setIsDynamic(false)
                .build()
        }
        cachedBookId = s.bookId
        cachedCount = s.chapterCount
        cachedPlaylist = items
        return items
    }

    private fun chapterDurationMs(s: PlayerState): Long =
        if (s.sentences.isEmpty()) DEFAULT_CHAPTER_MS
        else s.sentences.sumOf { sentenceDurationMs(it, s.speed) }

    private fun positionMsFor(s: PlayerState): Long {
        if (s.sentences.isEmpty()) return 0
        var acc = 0L
        for (i in 0 until s.sentenceIndex.coerceAtMost(s.sentences.size)) {
            acc += sentenceDurationMs(s.sentences[i], s.speed)
        }
        return acc
    }

    private fun sentenceIndexForPosition(s: PlayerState, positionMs: Long): Int {
        if (s.sentences.isEmpty()) return 0
        var acc = 0L
        s.sentences.forEachIndexed { index, sentence ->
            acc += sentenceDurationMs(sentence, s.speed)
            if (positionMs < acc) return index
        }
        return s.sentences.lastIndex
    }

    /** ~4 characters per second at rate 1.0; good enough for a progress bar. */
    private fun sentenceDurationMs(sentence: String, speed: Float): Long {
        val safeSpeed = if (speed <= 0f) 1f else speed
        return ((sentence.length.coerceAtLeast(1)) * MS_PER_CHAR / safeSpeed).toLong()
    }

    companion object {
        private const val MS_PER_CHAR = 240f
        private const val DEFAULT_CHAPTER_MS = 5 * 60 * 1000L
        private const val SENTENCE_STEP_MS = 5_000L
    }
}