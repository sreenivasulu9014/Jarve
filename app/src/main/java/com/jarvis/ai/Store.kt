package com.jarvis.ai

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("jarrven_prefs", Context.MODE_PRIVATE)

    private fun pi(c: Context, id: String, title: String, msg: String): PendingIntent =
        PendingIntent.getBroadcast(
            c,
            id.hashCode(),
            Intent(c, AlarmReceiver::class.java)
                .putExtra("title", title)
                .putExtra("msg", msg),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    @SuppressLint("ScheduleExactAlarm", "NewApi")
    fun scheduleAlarm(c: Context, id: String, title: String, msg: String, timestampMs: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
        val intent = pi(c, id, title, msg)
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestampMs, intent)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestampMs, intent)
        }
    }

    @Synchronized
    fun setAlarms(c: Context, json: String) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val old = JSONArray(prefs(c).getString("alarms", "[]"))
        for (i in 0 until old.length()) {
            val item = old.getJSONObject(i)
            am.cancel(pi(c, item.getString("id"), "", ""))
        }
        val now = JSONArray(json)
        for (i in 0 until now.length()) {
            val a = now.getJSONObject(i)
            val id = a.getString("id")
            val title = a.optString("title", "Jarrven Alert")
            val msg = a.optString("msg", "Alarm Triggered")
            val at = a.getLong("at")
            scheduleAlarm(c, id, title, msg, at)
        }
        prefs(c).edit().putString("alarms", json).apply()
    }

    fun reschedule(c: Context) {
        val a = JSONArray(prefs(c).getString("alarms", "[]"))
        val t = System.currentTimeMillis()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val at = o.getLong("at")
            if (at > t) {
                scheduleAlarm(
                    c,
                    o.getString("id"),
                    o.optString("title", "Jarrven Alert"),
                    o.optString("msg", "Alarm Triggered"),
                    at
                )
            }
        }
    }

    fun drain(c: Context): String = "[]"

    fun setApps(c: Context, csv: String) {
        prefs(c).edit().putString("apps", csv).apply()
    }

    fun saveSetting(c: Context, key: String, value: String) {
        prefs(c).edit().putString(key, value).apply()
    }

    fun getSetting(c: Context, key: String, def: String = ""): String {
        return prefs(c).getString(key, def) ?: def
    }
}
