package com.lis.wear

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.lis.wear.playback.LisPlaybackService

class LisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_PLAYBACK,
            getString(R.string.notification_channel_playback),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Lis 朗读播放状态"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_PLAYBACK = "lis_playback"
    }
}