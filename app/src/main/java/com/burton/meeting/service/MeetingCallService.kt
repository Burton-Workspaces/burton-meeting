package com.burton.meeting.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.burton.meeting.MainActivity
import com.burton.meeting.R

class MeetingCallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Meeting" }
        val voiceOnly = intent?.getBooleanExtra(EXTRA_VOICE, false) == true
        ensureChannel()
        val notification = buildNotification(title, voiceOnly)
        val types = if (Build.VERSION.SDK_INT >= 30) {
            if (voiceOnly) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Meetings", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun buildNotification(title: String, voiceOnly: Boolean): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_meeting)
            .setContentTitle(title)
            .setContentText(if (voiceOnly) "Voice meeting in progress" else "Video meeting in progress")
            .setContentIntent(launch)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_VOICE = "voice"
        private const val CHANNEL_ID = "meetings"
        private const val NOTIFICATION_ID = 47
    }
}
