package com.Azhe.ghs.gshschedule.today_appwidget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.Azhe.ghs.gshschedule.R
import com.Azhe.ghs.gshschedule.SplashActivity
import com.Azhe.ghs.gshschedule.utils.*
import java.text.SimpleDateFormat
import java.util.*

class TodayCourseAppWidget : AppWidgetProvider() {

    private val calendar = Calendar.getInstance()
    private val dateFmt = SimpleDateFormat("M.d", Locale.getDefault())
    private val weekFmt = SimpleDateFormat("E", Locale.getDefault())

    // ── onReceive ──────────────────────────────────────────────

    @SuppressLint("NewApi")
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "WAKEUP_REMIND_COURSE" -> handleRemind(context, intent)
            "WAKEUP_BACK_TIME",
            "WAKEUP_PERIODIC_REFRESH" -> handleRefresh(context)
            "WAKEUP_NEXT_DAY" -> handleNextDay(context)
            "WAKEUP_CANCEL_REMINDER" -> {
                val nm = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(intent.getIntExtra("index", 0))
            }
        }
        super.onReceive(context, intent)
    }

    // ── onUpdate ───────────────────────────────────────────────

    @SuppressLint("NewApi")
    override fun onUpdate(context: Context, awm: AppWidgetManager, ids: IntArray) {
        goAsync {
            try {
                // Refresh JSON data from Room DB
                TodayWidgetDataManager.convertAndStore(context)
                // Render all instances
                val component = ComponentName(context, TodayCourseAppWidget::class.java)
                for (id in awm.getAppWidgetIds(component)) {
                    renderWidget(context, awm, id)
                }
                // Schedule refresh alarms
                scheduleRefreshAlarms(context)
            } catch (_: Exception) {}
        }
    }

    // ── onDeleted ──────────────────────────────────────────────

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val db = com.Azhe.ghs.gshschedule.AppDatabase.getDatabase(context)
        goAsync {
            for (id in appWidgetIds) {
                db.appWidgetDao().deleteAppWidget(id)
            }
        }
    }

    // ── Handlers ───────────────────────────────────────────────

    private fun handleRemind(context: Context, intent: Intent) {
        if (!context.getPrefer().getBoolean(Const.KEY_COURSE_REMIND, false)) return
        val courseName = intent.getStringExtra("courseName") ?: return
        val room = intent.getStringExtra("room")?.ifEmpty { "??" } ?: "??"
        val time = intent.getStringExtra("time") ?: ""
        val weekDay = intent.getStringExtra("weekDay") ?: ""
        val index = intent.getIntExtra("index", 0)

        val nm = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val cancelPi = PendingIntent.getBroadcast(context, index,
            Intent(context, TodayCourseAppWidget::class.java).apply {
                action = "WAKEUP_CANCEL_REMINDER"
                putExtra("index", index)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val openPi = PendingIntent.getActivity(context, 0,
            Intent(context, SplashActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)

        nm.notify(index, NotificationCompat.Builder(context, "schedule_reminder")
            .setContentTitle("$time $courseName")
            .setSubText(context.getString(R.string.widget_remind_title))
            .setContentText("$weekDay  ${context.getString(R.string.widget_location_label)}??$room")
            .setWhen(System.currentTimeMillis())
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher))
            .setSmallIcon(R.drawable.wakeup)
            .setAutoCancel(false)
            .setOngoing(context.getPrefer().getBoolean(Const.KEY_REMINDER_ON_GOING, false))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            .setVibrate(longArrayOf(0, 5000, 500, 5000))
            .addAction(R.drawable.wakeup, context.getString(R.string.widget_remind_action), cancelPi)
            .setContentIntent(openPi)
            .build())
    }

    private fun handleRefresh(context: Context) {
        goAsync {
            try {
                val awm = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, TodayCourseAppWidget::class.java)
                for (id in awm.getAppWidgetIds(component)) {
                    renderWidget(context, awm, id)
                }
                scheduleRefreshAlarms(context)
            } catch (_: Exception) {}
        }
    }

    private fun handleNextDay(context: Context) {
        goAsync {
            try {
                TodayWidgetDataManager.convertAndStore(context)
                val awm = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, TodayCourseAppWidget::class.java)
                for (id in awm.getAppWidgetIds(component)) {
                    renderWidget(context, awm, id)
                }
                scheduleRefreshAlarms(context)
            } catch (_: Exception) {}
        }
    }

    // ── Widget rendering ───────────────────────────────────────

    private fun renderWidget(context: Context, awm: AppWidgetManager, appWidgetId: Int) {
        val data = TodayWidgetDataManager.loadData(context)
        val rv = RemoteViews(context.packageName, R.layout.today_course_app_widget)

        // Semester name (left, gray, from JSON — empty string if missing)
        val semester = data?.semesterName ?: ""
        rv.setTextViewText(R.id.tv_semester, semester)

        // Date: "M.d" (bold black) + weekday "E" (blue) — from system clock
        val now = Calendar.getInstance()
        rv.setTextViewText(R.id.tv_date_num, dateFmt.format(now.time))
        rv.setTextViewText(R.id.tv_date_week, weekFmt.format(now.time))

        // Filter active courses: current time < endTime
        val nowMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val activeCourses = data?.courses?.filter { course ->
            val endParts = course.endTime.split(":")
            val endMins = (endParts.getOrElse(0) { "0" }.toIntOrNull() ?: 0) * 60 +
                          (endParts.getOrElse(1) { "0" }.toIntOrNull() ?: 0)
            nowMins < endMins
        } ?: emptyList()

        if (activeCourses.isEmpty()) {
            // Empty state
            rv.setViewVisibility(R.id.ll_courses, android.view.View.GONE)
            rv.setViewVisibility(R.id.tv_empty, android.view.View.VISIBLE)
            rv.setTextViewText(R.id.tv_empty,
                if (data != null && data.courses.isNotEmpty())
                    context.getString(R.string.widget_all_ended)
                else
                    context.getString(R.string.widget_no_course))
        } else {
            rv.setViewVisibility(R.id.ll_courses, android.view.View.VISIBLE)
            rv.setViewVisibility(R.id.tv_empty, android.view.View.GONE)

            // Show up to 2 courses
            val display = activeCourses.take(2)

            for (i in 0..1) {
                val itemId = if (i == 0) R.id.course_item_1 else R.id.course_item_2
                val nameId = if (i == 0) R.id.tv_name_1 else R.id.tv_name_2
                val locId  = if (i == 0) R.id.tv_location_1 else R.id.tv_location_2
                val timeId = if (i == 0) R.id.tv_time_1 else R.id.tv_time_2
                val barId  = if (i == 0) R.id.iv_bar_1 else R.id.iv_bar_2

                if (i < display.size) {
                    val c = display[i]
                    rv.setViewVisibility(itemId, android.view.View.VISIBLE)
                    rv.setTextViewText(nameId, c.name)
                    val locText = buildString {
                        append(c.location.ifEmpty { context.getString(R.string.widget_no_location) })
                        if (!c.teacher.isNullOrBlank()) {
                            append("    ")
                            append(c.teacher)
                        }
                    }
                    rv.setTextViewText(locId, locText)
                    rv.setTextViewText(timeId,
                        if (c.startTime == c.endTime) c.startTime
                        else "${c.startTime} - ${c.endTime}")
                    // Tiny color bar bitmap (4dp rounded rect)
                    val barBmp = TodayWidgetDataManager.createColorBarBitmap(context, c.color)
                    if (barBmp != null) {
                        rv.setImageViewBitmap(barId, barBmp)
                    }
                } else {
                    rv.setViewVisibility(itemId, android.view.View.GONE)
                }
            }
        }

        // Entire widget clickable → open app
        val openPi = PendingIntent.getActivity(context, 0,
            Intent(context, SplashActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        rv.setOnClickPendingIntent(R.id.widget_root, openPi)

        awm.updateAppWidget(appWidgetId, rv)
    }

    // ── Alarm scheduling ───────────────────────────────────────

    @SuppressLint("NewApi")
    private fun scheduleRefreshAlarms(context: Context) {
        val data = TodayWidgetDataManager.loadData(context) ?: return
        val manager = context.getSystemService(ALARM_SERVICE) as AlarmManager

        val now = Calendar.getInstance()
        val nowMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        // 1. End-time alarms for active courses
        data.courses.forEachIndexed { index, course ->
            val endParts = course.endTime.split(":")
            val endMins = (endParts.getOrElse(0) { "0" }.toIntOrNull() ?: 0) * 60 +
                          (endParts.getOrElse(1) { "0" }.toIntOrNull() ?: 0)
            if (nowMins >= endMins) return@forEachIndexed

            calendar.timeInMillis = System.currentTimeMillis()
            calendar.set(Calendar.HOUR_OF_DAY,
                endParts.getOrElse(0) { "0" }.toIntOrNull() ?: 0)
            calendar.set(Calendar.MINUTE,
                endParts.getOrElse(1) { "0" }.toIntOrNull() ?: 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            if (calendar.timeInMillis <= System.currentTimeMillis()) return@forEachIndexed

            val pi = PendingIntent.getBroadcast(context, index + 10000,
                Intent(context, TodayCourseAppWidget::class.java).apply {
                    action = "WAKEUP_BACK_TIME"
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            when {
                Build.VERSION.SDK_INT >= 23 ->
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pi)
                Build.VERSION.SDK_INT in 19..22 ->
                    manager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pi)
            }
        }

        // 2. Periodic refresh alarm (5 min later, only during class hours 7:00-22:00)
        val hour = now.get(Calendar.HOUR_OF_DAY)
        if (hour in 7..21) {
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.MINUTE, 5)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            val periodicPi = PendingIntent.getBroadcast(context, 20000,
                Intent(context, TodayCourseAppWidget::class.java).apply {
                    action = "WAKEUP_PERIODIC_REFRESH"
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            when {
                Build.VERSION.SDK_INT >= 23 ->
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, periodicPi)
                Build.VERSION.SDK_INT in 19..22 ->
                    manager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, periodicPi)
            }
        }

        // 3. Midnight alarm for next-day data refresh
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 1)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val midnightPi = PendingIntent.getBroadcast(context, 30000,
            Intent(context, TodayCourseAppWidget::class.java).apply {
                action = "WAKEUP_NEXT_DAY"
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        when {
            Build.VERSION.SDK_INT >= 23 ->
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, midnightPi)
            Build.VERSION.SDK_INT in 19..22 ->
                manager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, midnightPi)
        }

        // 4. Also schedule start-reminder notifications
        scheduleReminderAlarms(context, data, manager, nowMins)
    }

    @SuppressLint("NewApi")
    private fun scheduleReminderAlarms(context: Context, data: TodayWidgetData, manager: AlarmManager, nowMins: Int) {
        if (!context.getPrefer().getBoolean(Const.KEY_COURSE_REMIND, false)) return
        val before = context.getPrefer().getInt(Const.KEY_REMINDER_TIME, 20)
        val weekDay = CourseUtils.getWeekday()

        data.courses.forEachIndexed { index, course ->
            val startParts = course.startTime.split(":")
            val startMins = (startParts.getOrElse(0) { "0" }.toIntOrNull() ?: 0) * 60 +
                           (startParts.getOrElse(1) { "0" }.toIntOrNull() ?: 0)
            if (nowMins >= startMins) return@forEachIndexed

            calendar.timeInMillis = System.currentTimeMillis()
            calendar.set(Calendar.HOUR_OF_DAY,
                startParts.getOrElse(0) { "0" }.toIntOrNull() ?: 0)
            calendar.set(Calendar.MINUTE,
                startParts.getOrElse(1) { "0" }.toIntOrNull() ?: 0)
            calendar.add(Calendar.MINUTE, -before)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            if (calendar.timeInMillis <= System.currentTimeMillis()) return@forEachIndexed

            val pi = PendingIntent.getBroadcast(context, index,
                Intent(context, TodayCourseAppWidget::class.java).apply {
                    action = "WAKEUP_REMIND_COURSE"
                    putExtra("courseName", course.name)
                    putExtra("room", course.location)
                    putExtra("weekDay", weekDay)
                    putExtra("index", index)
                    putExtra("time", course.startTime)
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            when {
                Build.VERSION.SDK_INT >= 23 ->
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pi)
                Build.VERSION.SDK_INT in 19..22 ->
                    manager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pi)
            }
        }
    }
}
