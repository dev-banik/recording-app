package com.callrecorder.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.callrecorder.app.R
import com.callrecorder.app.ui.MainActivity

object NotificationUtils {

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_RECORDING,
                "Recording In Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while a call is being recorded"
                setShowBadge(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_ALERTS,
                "Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Recording errors and storage warnings"
            }
        )
    }

    fun buildRecordingNotification(
        context: Context,
        callerLabel: String,
        callTypeLabel: String
    ): Notification {
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, Constants.CHANNEL_RECORDING)
            .setContentTitle("Recording $callTypeLabel")
            .setContentText(callerLabel)
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
