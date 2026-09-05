package com.lis.wear.playback

import android.app.PendingIntent
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground media service. Media3 publishes the MediaStyle notification for us,
 * which is exactly what Wear OS needs to show the playback indicator on the watch
 * face and to let the system media controls (and Samsung's Now bar) drive us.
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

        // Any state change in the TTS engine must be pushed to the session so
        // the system UI stays in sync (title, play/pause, chapter).
        engine.onStateChanged = { sessionPlayer.invalidateStatePublic() }

        session = MediaSession.Builder(this, sessionPlayer)
            .setId("lis")
            .setSessionActivity(openAppIntent())
            .build()

        scope.launch {
            engine.ensureTts()
        }
        scope.launch {
            // Keep the tile fresh while playback moves along.
            engine.state.collectLatest { LisTileService.requestUpdate(this@LisPlaybackService) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Watch apps get swiped away often; keep playing unless paused.
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
    }
}

/**
 * The engine must be shared between the UI process components and the service,
 * so it lives in a small holder rather than being owned by either.
 */
object EngineHolder {
    @Volatile
    private var engine: TtsBookEngine? = null

    fun getOrCreate(
        context: android.content.Context,
        repository: LisRepository,
    ): TtsBookEngine = engine ?: synchronized(this) {
        engine ?: TtsBookEngine(context.applicationContext, repository).also { engine = it }
    }

    fun peek(): TtsBookEngine? = engine
}