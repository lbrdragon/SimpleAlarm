package com.example.simplealarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

object AlarmScheduler {

    private const val PREFS = "alarm_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_DAYS = "days"
    private const val KEY_NEXT = "next_trigger"

    private const val RC_ALARM = 1001
    private const val RC_SHOW = 1002
    private const val TAG = "AlarmScheduler"

    data class Config(
        val enabled: Boolean,
        val hour: Int,
        val minute: Int,
        val days: Set<Int>
    )

    fun loadConfig(ctx: Context): Config {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = p.getStringSet(KEY_DAYS, emptySet()) ?: emptySet()
        return Config(
            enabled = p.getBoolean(KEY_ENABLED, false),
            hour = p.getInt(KEY_HOUR, 7),
            minute = p.getInt(KEY_MINUTE, 0),
            days = raw.mapNotNull { it.toIntOrNull() }.toSet()
        )
    }

    fun saveConfig(ctx: Context, hour: Int, minute: Int, days: Set<Int>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, true)
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .putStringSet(KEY_DAYS, days.map { it.toString() }.toSet())
            .apply()
    }

    fun setEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) {
            cancelSystemAlarm(ctx)
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_NEXT).apply()
        }
    }

    fun getNextTrigger(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_NEXT, 0L)

    fun scheduleNext(ctx: Context) {
        val cfg = loadConfig(ctx)
        if (!cfg.enabled || cfg.days.isEmpty()) {
            cancelSystemAlarm(ctx)
            return
        }
        val next = calculateNext(cfg.hour, cfg.minute, cfg.days, System.currentTimeMillis())
        if (next == null) {
            cancelSystemAlarm(ctx)
            return
        }
        setSystemAlarm(ctx, next)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_NEXT, next).apply()
    }

    fun calculateNext(hour: Int, minute: Int, days: Set<Int>, fromMillis: Long): Long? {
        if (days.isEmpty()) return null
        val cal = Calendar.getInstance().apply {
            timeInMillis = fromMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= fromMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        repeat(8) {
            if (cal.get(Calendar.DAY_OF_WEEK) in days) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    private fun setSystemAlarm(ctx: Context, triggerAt: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val op = alarmPendingIntent(ctx)
        val showIntent = PendingIntent.getActivity(
            ctx, RC_SHOW,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), op)
        } catch (e: SecurityException) {
            Log.w(TAG, "精确闹钟不可用，降级到 setAndAllowWhileIdle", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, op)
        }
    }

    private fun cancelSystemAlarm(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(alarmPendingIntent(ctx))
    }

    private fun alarmPendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            ctx, RC_ALARM, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}