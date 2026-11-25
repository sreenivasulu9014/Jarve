package com.jarvis.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val title = i.getStringExtra("title") ?: "JARVIS Alert"
        val msg = i.getStringExtra("msg") ?: "Time for scheduled activity"
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "jarvis_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "JARVIS Alerts", NotificationManager.IMPORTANCE_HIGH)
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(c, channelId)
        } else {
            Notification.Builder(c)
        }.setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(msg)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Store.reschedule(c)
    }
}
