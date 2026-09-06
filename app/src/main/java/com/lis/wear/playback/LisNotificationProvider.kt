package com.lis.wear.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.lis.wear.LisApp
import com.lis.wear.R
import com.lis.wear.ui.MainActivity
import com.google.common.collect.ImmutableList

/**
 * Wraps Media3's notification provider and attaches a Wear [OngoingActivity].
 *
 * Wear OS derives the watch-face playback indicator (and Samsung's Now bar entry)
 * from an ongoing notification. Media3 alone posts a MediaStyle notification, but
 * attaching an OngoingActivity explicitly is what guarantees the animated icon at
 * the bottom of the watch face plus a Recents launcher chip with live status text.
 */
@OptIn(UnstableApi::class)
class LisNotificationProvider(
    private val context: Context,
    private val engine: TtsBookEngine,
) : MediaNotification.Provider {

    private val delegate = DefaultMediaNotificationProvider.Builder(context)
        .setChannelId(LisApp.CHANNEL_PLAYBACK)
        .setChannelName(R.string.notification_channel_playback)
        .setNotificationId(NOTIFICATION_ID)
        .build()
        .apply { setSmallIcon(R.drawable.ic_play_arrow) }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val base = delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            onNotificationChangedCallback,
        )

        val state = engine.state.value
        val builder = NotificationCompat.Builder(context, base.notification)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val touchIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val statusText = buildString {
            if (state.chapterTitle.isNotBlank()) append(state.chapterTitle) else append("朗读中")
            if (state.sentenceTotal > 0) {
                append(" · ")
                append(state.sentenceIndex + 1)
                append('/')
                append(state.sentenceTotal)
            }
        }

        runCatching {
            OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
                .setStaticIcon(R.drawable.ic_play_arrow)
                .setAnimatedIcon(R.drawable.ic_play_arrow)
                .setTouchIntent(touchIntent)
                .setTitle(state.bookTitle.ifBlank { "Lis 听书" })
                .setStatus(
                    Status.Builder()
                        .addTemplate("#status#")
                        .addPart("status", Status.TextPart(statusText))
                        .build()
                )
                .build()
                .apply(context)
        }

        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: android.os.Bundle,
    ): Boolean = false

    companion object {
        const val NOTIFICATION_ID = 4210
        private const val REQUEST_OPEN = 5001
    }
}