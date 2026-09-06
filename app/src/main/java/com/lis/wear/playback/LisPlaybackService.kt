package com.lis.wear.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lis.wear.data.LisRepository
import com.lis.wear.tile.LisTileService
import com.lis.wear.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground media service.
 *
 * Media3 posts the MediaStyle notification, and [LisNotificationProvider] attaches
 * a Wear OngoingActivity to it — together these drive the watch's system media
 * controls, the watch-face playback indicator and Samsung's Now bar.
 *
 * The engine itself lives in [EngineHolder] so the UI and this service share one
 * instance; the service only owns the session.
 */
@UnstableApi
class LisPlaybackService : MediaSessionService() {

    private lateinit var engine: TtsBookEngine
    private var session: MediaSession? = null
    private var player: TtsSessionPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val repository = LisRepository.get(this)
        engine = EngineHolder.getOrCreate(this, repository)

        val sessionPlayer = TtsSessionPlayer(engine, Looper.getMainLooper())
        player = sessionPlayer

        setMediaNotificationProvider(LisNotificationProvider(this, engine))

        session = MediaSession.Builder(this, sessionPlayer)
            .setId("lis")
            .setSessionActivity(openAppIntent())
            .build()

        // Push engine changes into the session so system UI stays in sync.
        engine.onStateChanged = { sessionPlayer.requestStateRefresh() }

        scope.launch { engine.ensureTts() }

        // Refresh the tile when the chapter/sentence/playing state actually changes.
        scope.launch {
            engine.state
                .distinctUntilChangedBy {
                    listOf(it.bookId, it.chapterIndex, it.sentenceIndex, it.isPlaying)
                }
                .collectLatest { LisTileService.requestUpdate(this@LisPlaybackService) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Watch apps are swiped away constantly; only die when actually idle.
        if (!engine.isPlaying) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        engine.onStateChanged = null
        session?.release()
        session = null
        player = null
        scope.cancel()
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val REQUEST_OPEN_APP = 1001

        /** Start (or wake) the service so playback survives leaving the app. */
        fun ensureRunning(context: Context) {
            runCatching {
                val intent = Intent(context, LisPlaybackService::class.java)
                context.startService(intent)
            }
        }
    }
}

/**
 * Process-wide holder for the playback engine, shared by the UI, the tile
 * receiver and the media service.
 */
object EngineHolder {
    @Volatile
    private var engine: TtsBookEngine? = null

    fun getOrCreate(
        context: Context,
        repository: LisRepository,
    ): TtsBookEngine = engine ?: synchronized(this) {
        engine ?: TtsBookEngine(context.applicationContext, repository).also { engine = it }
    }

    /** Create on demand so the tile/receiver work even before the UI ran. */
    fun require(context: Context): TtsBookEngine =
        getOrCreate(context, LisRepository.get(context))

    fun peek(): TtsBookEngine? = engine
}