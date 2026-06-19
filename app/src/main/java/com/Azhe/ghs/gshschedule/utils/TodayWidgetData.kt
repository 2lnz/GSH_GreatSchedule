package com.Azhe.ghs.gshschedule.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.Azhe.ghs.gshschedule.AppDatabase
import com.Azhe.ghs.gshschedule.bean.CourseBean
import com.Azhe.ghs.gshschedule.bean.TimeDetailBean
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.text.ParseException

/**
 * Data model for the 2×2 today-course widget.
 * All data comes from JSON — no hardcoded course names, locations, or times.
 */
data class TodayWidgetCourse(
    @SerializedName("name") val name: String,
    @SerializedName("location") val location: String,
    @SerializedName("teacher") val teacher: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String,
    @SerializedName("color") val color: String
)

data class TodayWidgetData(
    @SerializedName("semesterName") val semesterName: String,
    @SerializedName("courses") val courses: List<TodayWidgetCourse>
)

/**
 * Manages today-widget JSON data in SharedPreferences.
 */
object TodayWidgetDataManager {

    private const val PREF_NAME = "today_widget_data"
    private const val KEY_JSON = "widget_json"

    private val gson = Gson()

    fun storeData(context: Context, data: TodayWidgetData) {
        val json = gson.toJson(data)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, json)
            .apply()
    }

    fun loadData(context: Context): TodayWidgetData? {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null) ?: return null
        return try {
            gson.fromJson(json, TodayWidgetData::class.java)
        } catch (_: Exception) { null }
    }

    /**
     * Convert today's Room DB courses to JSON and store for widget use.
     * Called from SplashActivity and widget onUpdate.
     * Returns the data that was stored, or null on failure.
     */
    suspend fun convertAndStore(context: Context): TodayWidgetData? {
        return try {
            val db = AppDatabase.getDatabase(context)
            val table = db.tableDao().getDefaultTable()
            val courseDao = db.courseDao()
            val timeDao = db.timeDetailDao()

            val week = try {
                CourseUtils.countWeek(table.startDate, table.sundayFirst)
            } catch (_: ParseException) { -1 }

            val cal = java.util.Calendar.getInstance()
            val month = cal.get(java.util.Calendar.MONTH) + 1
            val year = cal.get(java.util.Calendar.YEAR)
            val half = if (month in 3..8) "上" else "下"
            val semesterName = "${(year % 100).toString().padStart(2, '0')}年${half}半"

            val timeList = timeDao.getTimeList(table.timeTable)

            val allCourses = if (week >= 0) {
                courseDao.getCourseByDayOfTable(
                    CourseUtils.getWeekdayInt(),
                    week,
                    if (week % 2 == 0) 2 else 1,
                    table.id
                )
            } else emptyList()

            val filtered = allCourses.filter { c ->
                c.startWeek <= week && c.endWeek >= week &&
                    (c.type == 0 || c.type == (if (week % 2 == 0) 2 else 1))
            }

            val courses = mutableListOf<TodayWidgetCourse>()
            for (c in filtered) {
                val maxIdx = (timeList.size - 1).coerceAtLeast(0)
                val startIdx = (c.startNode - 1).coerceIn(0, maxIdx)
                val endIdx = (c.startNode + c.step - 2).coerceIn(0, maxIdx)
                val startT = timeList[startIdx].startTime
                val endT = timeList[endIdx].endTime
                val color = if (c.color.isNotEmpty() && c.color.startsWith("#")) c.color
                           else "#3B82F6"

                courses.add(TodayWidgetCourse(
                    name = c.courseName,
                    location = c.room ?: "",
                    teacher = c.teacher ?: "",
                    startTime = startT,
                    endTime = endT,
                    color = color
                ))
            }

            val data = TodayWidgetData(semesterName = semesterName, courses = courses)
            storeData(context, data)
            data
        } catch (_: Exception) { null }
    }

    /**
     * Create a tiny rounded-rectangle bitmap for the course color bar.
     * Width: 4dp, height: 48dp (scaled). Used in RemoteViews via setImageViewBitmap.
     */
    fun createColorBarBitmap(context: Context, colorHex: String): Bitmap? {
        return try {
            val density = context.resources.displayMetrics.density
            val w = (4f * density).toInt().coerceAtLeast(1)
            val h = (48f * density).toInt().coerceAtLeast(1)
            val barColor = try {
                Color.parseColor(colorHex)
            } catch (_: Exception) { Color.parseColor("#3B82F6") }
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = barColor
                style = Paint.Style.FILL
            }
            val radius = 2f * density
            canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), radius, radius, paint)
            bitmap
        } catch (_: Exception) { null }
    }
}
