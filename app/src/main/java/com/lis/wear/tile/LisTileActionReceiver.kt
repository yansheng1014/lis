package com.lis.wear.tile

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lis.wear.playback.EngineHolder
import com.lis.wear.playback.LisPlaybackService
import com.lis.wear.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the tile's transport buttons. The engine is created on demand, so the
 * tile works even if the app UI hasn't been opened since boot: it restores the
 * last book and starts reading.
 */
class LisTileActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val engine = EngineHolder.require(appContext)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        when (intent.action) {
            ACTION_TOGGLE -> scope.launch {
                if (!engine.state.value.hasBook) {
                    engine.resumeLast(autoPlay = true)
                } else {
                    engine.togglePlayPause()
                }
                LisPlaybackService.ensureRunning(appContext)
                LisTileService.requestUpdate(appContext)
            }

            ACTION_NEXT -> scope.launch {
                if (!engine.state.value.hasBook) engine.resumeLast(autoPlay = false)
                engine.nextChapter()
                LisTileService.requestUpdate(appContext)
            }

            ACTION_PREV -> scope.launch {
                if (!engine.state.value.hasBook) engine.resumeLast(autoPlay = false)
                engine.previousChapter()
                LisTileService.requestUpdate(appContext)
            }

            ACTION_OPEN -> {
                val open = Intent(appContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                }
                appContext.startActivity(open)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.lis.wear.tile.TOGGLE"
        const val ACTION_NEXT = "com.lis.wear.tile.NEXT"
        const val ACTION_PREV = "com.lis.wear.tile.PREV"
        const val ACTION_OPEN = "com.lis.wear.tile.OPEN"

        fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, LisTileActionReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context,
                90,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}