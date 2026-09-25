package com.tomjxyz.shipinfo.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tomjxyz.shipinfo.MainActivity
import com.tomjxyz.shipinfo.R

object Notifications {
    const val CHANNEL_RECORDING = "recording"
    const val CHANNEL_PINS = "pins"
    const val ID_RECORDING = 1
    const val ID_ROLL_WATCH = 2
    const val ID_PIN = 3

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDING, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while ShipInfo is recording"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PINS, "Position pins", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Automatic position pins"
            },
        )
    }

    fun openAppIntent(context: Context, tab: String): PendingIntent = PendingIntent.getActivity(
        context,
        tab.hashCode(),
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, tab)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun serviceIntent(context: Context, cls: Class<*>, action: String): PendingIntent = PendingIntent.getService(
        context,
        action.hashCode(),
        Intent(context, cls).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun ongoing(
        context: Context,
        title: String,
        text: String,
        tab: String,
        stopIntent: PendingIntent,
    ) = NotificationCompat.Builder(context, CHANNEL_RECORDING)
        .setSmallIcon(R.drawable.ic_stat_ship)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(openAppIntent(context, tab))
        .addAction(0, "Stop", stopIntent)
        .build()
}
