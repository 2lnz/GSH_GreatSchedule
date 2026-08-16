package com.Azhe.ghs.gshschedule.schedule

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import biweekly.Biweekly
import biweekly.ICalVersion
import biweekly.ICalendar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.Azhe.ghs.gshschedule.App
import com.Azhe.ghs.gshschedule.AppDatabase
import com.Azhe.ghs.gshschedule.R
import com.Azhe.ghs.gshschedule.bean.*
import com.Azhe.ghs.gshschedule.schedule_import.Common
import com.Azhe.ghs.gshschedule.utils.Const
import com.Azhe.ghs.gshschedule.utils.CourseUtils
import com.Azhe.ghs.gshschedule.utils.ICalUtils
import com.Azhe.ghs.gshschedule.utils.getPrefer
import java.text.SimpleDateFormat
import java.util.*

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val dataBase = AppDatabase.getDatabase(application)
    private val courseDao = dataBase.courseDao()
    private val tableDao = dataBase.tableDao()
    private val widgetDao = dataBase.appWidgetDao()
    private val timeTableDao = dataBase.timeTableDao()
    private val timeDao = dataBase.timeDetailDao()

    var table: TableBean by mutableStateOf(TableBean(id = 0, tableName = ""))
    var timeList: List<TimeDetailBean> by mutableStateOf(emptyList())
    var showTimeDetail by mutableStateOf(true)
    var dataVersion by mutableIntStateOf(0)
    var selectedWeek by mutableIntStateOf(1)
    val marTop = application.resources.getDimensionPixelSize(R.dimen.weekItemMarTop)
    var itemHeight = 0
    var alphaInt = 225
    val tableSelectList = arrayListOf<TableSelectBean>()
    val allCourseList = Array(7) { MutableLiveData<List<CourseBean>>() }
    val daysArray = arrayOf("日", "一", "二", "三", "四", "五", "六", "日")
    var currentWeek by mutableIntStateOf(1)

    // ── CourseCard state cache (pre-computed on background thread) ──
    // Key: "week_day", Value: cached card states for that week/day
    val cardStateCache = mutableStateMapOf<String, List<CourseCardState>>()
    private var precomputeJob: kotlinx.coroutines.Job? = null

    /** Pre-compute card states for the current week ± 1 on a background thread. */
    fun precomputeCardStates(week: Int) {
        precomputeJob?.cancel()
        val ctx = getApplication<App>()
        val weeksToCompute = ((week - 1).coerceAtLeast(1)..(week + 1).coerceAtMost(table.maxWeek)).toList()
        precomputeJob = viewModelScope.launch(Dispatchers.Default) {
            for (w in weeksToCompute) {
                for (day in 1..7) {
                    val key = "${w}_$day"
                    if (key in cardStateCache) continue  // already cached
                    val courses = allCourseList[day - 1].value ?: emptyList()
                    if (courses.isEmpty()) continue
                    val states = buildCourseCardStates(
                        courses = courses,
                        week = w,
                        day = day,
                        table = table,
                        alphaInt = alphaInt,
                        timeList = timeList,
                        context = ctx
                    )
                    withContext(Dispatchers.Main) {
                        cardStateCache[key] = states
                    }
                }
            }
        }
    }

    /** Invalidate the cache when course data or table config changes. */
    fun invalidateCardStateCache() {
        precomputeJob?.cancel()
        cardStateCache.clear()
    }

    fun initTableSelectList(): LiveData<List<TableSelectBean>> {
        return tableDao.getTableSelectListLiveData()
    }

    fun getMultiCourse(week: Int, day: Int, startNode: Int): List<CourseBean> {
        return allCourseList[day - 1].value!!.filter {
            it.inWeek(week) && it.startNode == startNode
        }
    }

    suspend fun getDefaultTable(): TableBean {
        return tableDao.getDefaultTable()
    }

    suspend fun getTimeList(timeTableId: Int): List<TimeDetailBean> {
        return timeDao.getTimeList(timeTableId)
    }

    suspend fun addBlankTable(tableName: String) {
        tableDao.insertTable(TableBean(id = 0, tableName = tableName))
    }

    suspend fun changeDefaultTable(id: Int) {
        tableDao.changeDefaultTable(table.id, id)
    }

    suspend fun getScheduleWidgetIds(): List<AppWidgetBean> {
        return widgetDao.getWidgetsByBaseType(0)
    }

    fun getRawCourseByDay(day: Int, tableId: Int): LiveData<List<CourseBean>> {
        return courseDao.getCourseByDayOfTableLiveData(day, tableId)
    }

    fun getShowCourseNumber(week: Int): LiveData<Int> {
        return if (table.showOtherWeekCourse) {
            courseDao.getShowCourseNumberWithOtherWeek(table.id, week)
        } else {
            courseDao.getShowCourseNumber(table.id, week)
        }
    }

    suspend fun deleteCourseBean(courseBean: CourseBean) {
        courseDao.deleteCourseDetail(CourseUtils.courseBean2DetailBean(courseBean))
    }

    suspend fun deleteCourseBaseBean(id: Int, tableId: Int) {
        courseDao.deleteCourseBaseBeanOfTable(id, tableId)
    }

    suspend fun updateFromOldVer(json: String) {
        val gson = Gson()
        val list = gson.fromJson<List<CourseOldBean>>(json, object : TypeToken<List<CourseOldBean>>() {
        }.type)
        val lastId = tableDao.getLastId()
        val tableId = if (lastId != null) {
            lastId + 1
        } else {
            1
        }
        oldBean2CourseBean(list, tableId)
    }

    private suspend fun oldBean2CourseBean(list: List<CourseOldBean>, tableId: Int) {
        val baseList = arrayListOf<CourseBaseBean>()
        val detailList = arrayListOf<CourseDetailBean>()
        var id = 0
        for (oldBean in list) {
            val flag = Common.findExistedCourseId(baseList, oldBean.name)
            if (flag == -1) {
                baseList.add(CourseBaseBean(id, oldBean.name, "", tableId))
                detailList.add(CourseDetailBean(
                        id = id, room = oldBean.room,
                        teacher = oldBean.teach, day = oldBean.day,
                        step = oldBean.step, startWeek = oldBean.startWeek, endWeek = oldBean.endWeek,
                        type = oldBean.isOdd, startNode = oldBean.start,
                        tableId = tableId
                ))
                id++
            } else {
                detailList.add(CourseDetailBean(
                        id = flag, room = oldBean.room,
                        teacher = oldBean.teach, day = oldBean.day,
                        step = oldBean.step, startWeek = oldBean.startWeek, endWeek = oldBean.endWeek,
                        type = oldBean.isOdd, startNode = oldBean.start,
                        tableId = tableId
                ))
            }
        }
        courseDao.insertCourses(baseList, detailList)
        getApplication<App>().getPrefer().edit {
            remove(Const.KEY_OLD_VERSION_COURSE)
        }
    }

    suspend fun exportData(uri: Uri?) {
        if (uri == null) throw Exception("无法获取文件")
        val outputStream = getApplication<App>().contentResolver.openOutputStream(uri)
        val gson = Gson()
        val strBuilder = StringBuilder()
        strBuilder.append(gson.toJson(timeTableDao.getTimeTable(table.timeTable)))
        strBuilder.append("\n${gson.toJson(timeList)}")
        strBuilder.append("\n${gson.toJson(table)}")
        strBuilder.append("\n${gson.toJson(courseDao.getCourseBaseBeanOfTable(table.id))}")
        strBuilder.append("\n${gson.toJson(courseDao.getDetailOfTable(table.id))}")
        withContext(Dispatchers.IO) {
            outputStream?.write(strBuilder.toString().toByteArray())
        }
    }

    suspend fun exportICS(uri: Uri?) {
        if (uri == null) throw Exception("无法获取文件")
        val ical = ICalendar()
        withContext(Dispatchers.Default) {
            ical.setProductId("-//YZune//WakeUpSchedule//EN")
            val startTimeMap = ICalUtils.getClassTime(timeList, true)
            val endTimeMap = ICalUtils.getClassTime(timeList, false)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            val date = sdf.parse(table.startDate)
            allCourseList.forEach {
                it.value?.forEach { course ->
                    try {
                        ICalUtils.getClassEvents(ical, startTimeMap, endTimeMap, table.maxWeek, course, date)
                    } catch (ignored: Exception) {

                    }
                }
            }
        }
        val warnings = ical.validate(ICalVersion.V2_0)
        Log.d("日历", warnings.toString())
        val outputStream = getApplication<App>().contentResolver.openOutputStream(uri)
        withContext(Dispatchers.IO) {
            Biweekly.write(ical).go(outputStream)
        }
    }
}