package com.lis.wear.tile

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lis.wear.playback.EngineHolder

/**
 * Receives media commands sent from the tile's PendingIntents and forwards them
 * to the shared TTS engine. The service keeps the engine alive during playback,
 * so the holder is populated whenever a book is being listened to.
 */
class LisTileActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val engine = EngineHolder.peek() ?: return
        when (intent.action) {
            ACTION_TOGGLE -> engine.togglePlayPause()
            ACTION_NEXT -> engine.nextChapter()
            ACTION_PREV -> engine.previousChapter()
            ACTION_OPEN -> {
                val open = Intent(context, com.lis.wear.ui.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(com.lis.wear.ui.MainActivity.EXTRA_OPEN_PLAYER, true)
                }
                context.startActivity(open)
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
    }
}