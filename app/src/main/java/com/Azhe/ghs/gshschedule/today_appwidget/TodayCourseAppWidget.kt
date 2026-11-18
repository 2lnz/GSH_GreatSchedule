package com.Azhe.ghs.gshschedule.today_appwidget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.Azhe.ghs.gshschedule.AppDatabase
import com.Azhe.ghs.gshschedule.R
import com.Azhe.ghs.gshschedule.SplashActivity
import com.Azhe.ghs.gshschedule.bean.TableBean
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
            "WAKEUP_BACK_TIME",
            "WAKEUP_PERIODIC_REFRESH" -> handleRefresh(context)
            "WAKEUP_NEXT_DAY" -> handleNextDay(context)
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

        // 应用「桌面小部件外观」中的字号与文字颜色设置
        // （格子类设置仅对「一周课程」周视图有意义，日视图不适用）
        val table = try {
            AppDatabase.getDatabase(context).tableDao().getDefaultTableSync()
        } catch (_: Exception) {
            null
        }

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

        // Show up to 2 courses
        val display = activeCourses.take(2)

        // 根据小部件实际高度选择字号/信息密度；两节课放不下时按「课名 > 地点 > 时间」取舍
        val appearance = pickAppearance(context, table, awm, appWidgetId, display.size)
        applyAppearance(rv, table, appearance)

        if (data?.semesterEnded == true) {
            // 学期结束提示
            rv.setViewVisibility(R.id.ll_courses, android.view.View.GONE)
            rv.setViewVisibility(R.id.tv_empty, android.view.View.VISIBLE)
            rv.setTextViewText(R.id.tv_empty, context.getString(R.string.widget_semester_ended))
        } else if (activeCourses.isEmpty()) {
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

            for (i in 0..1) {
                val itemId = if (i == 0) R.id.course_item_1 else R.id.course_item_2
                val nameId = if (i == 0) R.id.tv_name_1 else R.id.tv_name_2
                val locId  = if (i == 0) R.id.tv_location_1 else R.id.tv_location_2
                val timeId = if (i == 0) R.id.tv_time_1 else R.id.tv_time_2
                val barId  = if (i == 0) R.id.iv_bar_1 else R.id.iv_bar_2

                if (i < display.size) {
                    val c = display[i]
                    rv.setViewVisibility(itemId, android.view.View.VISIBLE)
                    rv.setInt(nameId, "setMaxLines", appearance.nameMaxLines)
                    rv.setTextViewText(nameId, c.name)
                    // 优先级：课名 > 地点 > 时间。地点始终显示，时间放不下时整行隐藏
                    val locText = buildString {
                        append(c.location.ifEmpty { context.getString(R.string.widget_no_location) })
                        if (!c.teacher.isNullOrBlank()) {
                            append("    ")
                            append(c.teacher)
                        }
                    }
                    rv.setTextViewText(locId, locText)
                    rv.setViewVisibility(timeId,
                        if (appearance.showTime) android.view.View.VISIBLE
                        else android.view.View.GONE)
                    // 去掉时间两端的空格，窄小部件里也能完整显示
                    rv.setTextViewText(timeId,
                        if (c.startTime == c.endTime) c.startTime
                        else "${c.startTime}-${c.endTime}")
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

    /**
     * 小部件尺寸变化（或首次添加时桌面回报尺寸）后立即重绘，
     * 让排版跟着高度走，避免内容溢出被裁切。
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        awm: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, awm, appWidgetId, newOptions)
        goAsync {
            try {
                renderWidget(context, awm, appWidgetId)
                scheduleRefreshAlarms(context)
            } catch (_: Exception) {}
        }
    }

    // ── Appearance ─────────────────────────────────────────────

    /** 一次渲染选定的文字大小与信息密度。 */
    private data class WidgetAppearance(
        val primarySp: Float,
        val secondarySp: Float,
        val nameMaxLines: Int,
        val showTime: Boolean
    )

    /**
     * 按小部件实际高度从「信息最全」到「最紧凑」挑一种排版。
     *
     * 优先级：课名 > 地点 > 时间。以前固定用 +2sp/-1sp、课程名两行、地点+时间
     * 各占一行，两节课时总高度会超出 2×2 小部件的可用高度，溢出部分被桌面裁掉
     * ——表现就是第二节课的上课时间只显示一半。这里先缩小字号，空间还不够时
     * 整行隐藏时间，而不是让它被裁成半截；课名和地点始终保留。
     */
    private fun pickAppearance(
        context: Context,
        table: TableBean?,
        awm: AppWidgetManager,
        appWidgetId: Int,
        courseCount: Int
    ): WidgetAppearance {
        val base = (table?.widgetItemTextSize ?: 12).toFloat()
        val heightDp = widgetHeightDp(awm, appWidgetId)

        val roomyPrimary = base + 2
        val roomySecondary = (base - 1).coerceAtLeast(8f)
        val compactPrimary = base + 1
        val compactSecondary = (base - 2).coerceAtLeast(8f)
        val tinyPrimary = minOf(compactPrimary, 12f)
        val tinySecondary = minOf(compactSecondary, 9f).coerceAtLeast(8f)

        val candidates = if (courseCount <= 1) {
            listOf(
                WidgetAppearance(roomyPrimary, roomySecondary, 2, true),
                WidgetAppearance(roomyPrimary, roomySecondary, 1, true),
                WidgetAppearance(compactPrimary, compactSecondary, 1, true),
                WidgetAppearance(compactPrimary, compactSecondary, 1, false),
                WidgetAppearance(tinyPrimary, tinySecondary, 1, false)
            )
        } else {
            listOf(
                WidgetAppearance(compactPrimary, compactSecondary, 1, true),
                WidgetAppearance(compactPrimary, compactSecondary, 1, false),
                WidgetAppearance(tinyPrimary, tinySecondary, 1, false)
            )
        }
        return candidates.firstOrNull { fitsInAvailableHeight(context, it, courseCount, heightDp) }
            ?: candidates.last()
    }

    /**
     * 判断排版能否放进每个条目。
     * 布局里两个条目按 weight 均分日期栏以下的剩余高度，所以这里算的是
     * 「每个条目分到的高度」是否够放 课名 + 地点 + 时间，宁可多算一点。
     */
    private fun fitsInAvailableHeight(
        context: Context,
        appearance: WidgetAppearance,
        courseCount: Int,
        heightDp: Float
    ): Boolean {
        val topBar = lineHeightDp(context, appearance.primarySp) + 6f
        val itemGap = if (courseCount > 1) 4f else 0f
        val available = heightDp - 16f /* 上下 padding 8dp */ - topBar - itemGap
        if (available <= 0f) return false
        val itemShare = available / courseCount.coerceAtLeast(1)
        val itemNeeded = lineHeightDp(context, appearance.primarySp) * appearance.nameMaxLines +
                lineHeightDp(context, appearance.secondarySp) * (if (appearance.showTime) 2 else 1) + 2f
        return itemNeeded <= itemShare
    }

    /** 单行文字的大致高度（含字体留白），用于估算。 */
    private fun lineHeightDp(context: Context, textSizeSp: Float): Float {
        val dm = context.resources.displayMetrics
        val paint = android.graphics.Paint().apply { textSize = textSizeSp * dm.scaledDensity }
        return paint.fontSpacing / dm.density * 1.15f
    }

    /** 小部件当前高度（dp）；取不到时按 2×2 最小高度 110dp 估算。 */
    private fun widgetHeightDp(awm: AppWidgetManager, appWidgetId: Int): Float {
        val options = try {
            awm.getAppWidgetOptions(appWidgetId)
        } catch (_: Exception) {
            null
        } ?: return 110f

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            sizes?.minOfOrNull { it.height }?.let { if (it > 0f) return it }
        }

        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        return when {
            minH > 0 -> minH.toFloat()
            maxH > 0 -> maxH.toFloat()
            else -> 110f
        }
    }

    /**
     * 把「桌面小部件外观」中的字号/颜色应用到日视图小部件：
     * - 字号由 [pickAppearance] 按可用高度决定
     * - 小部件标题颜色：始终作用于全部文字（与设置项描述「对于日视图则是全部文字的颜色」一致）
     * - 小部件课程颜色：覆盖课程名/地点/时间的颜色
     *
     * 标题颜色不再把纯黑（#FF000000）当作「未设置」：用户主动选黑色也是一次有效设置。
     * 课程颜色的默认值仍是白色，白色只用于周视图彩色格子，若不屏蔽会在日视图的
     * 白色卡片上变成白字白底，因此仍以白色作为「未自定义」的占位值。
     */
    private fun applyAppearance(rv: RemoteViews, table: TableBean?, appearance: WidgetAppearance) {
        val primaryIds = intArrayOf(
            R.id.tv_date_num, R.id.tv_name_1, R.id.tv_name_2
        )
        val secondaryIds = intArrayOf(
            R.id.tv_semester, R.id.tv_date_week, R.id.tv_empty,
            R.id.tv_location_1, R.id.tv_location_2, R.id.tv_time_1, R.id.tv_time_2
        )
        primaryIds.forEach {
            rv.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_SP, appearance.primarySp)
        }
        secondaryIds.forEach {
            rv.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_SP, appearance.secondarySp)
        }

        if (table == null) return

        // 标题颜色始终生效（包括用户主动选择的纯黑色）
        (primaryIds + secondaryIds).forEach { rv.setTextColor(it, table.widgetTextColor) }

        val courseColor = table.widgetCourseTextColor
        if (courseColor != 0xffffffff.toInt()) {
            intArrayOf(R.id.tv_name_1, R.id.tv_name_2).forEach {
                rv.setTextColor(it, courseColor)
            }
            val secondaryColor = ColorUtils.setAlphaComponent(
                    courseColor, (Color.alpha(courseColor) * 0.72f).toInt())
            intArrayOf(
                R.id.tv_location_1, R.id.tv_location_2,
                R.id.tv_time_1, R.id.tv_time_2
            ).forEach { rv.setTextColor(it, secondaryColor) }
        }
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

    }
}
