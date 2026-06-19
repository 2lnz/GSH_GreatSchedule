package com.Azhe.ghs.gshschedule.schedule_appwidget

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.TextView
import androidx.core.view.setPadding
import com.Azhe.ghs.gshschedule.AppDatabase
import com.Azhe.ghs.gshschedule.R
import com.Azhe.ghs.gshschedule.bean.CourseBean
import com.Azhe.ghs.gshschedule.bean.TableBean
import com.Azhe.ghs.gshschedule.bean.TimeDetailBean
import com.Azhe.ghs.gshschedule.utils.Const
import com.Azhe.ghs.gshschedule.utils.CourseUtils
import com.Azhe.ghs.gshschedule.utils.CourseUtils.countWeek
import com.Azhe.ghs.gshschedule.utils.ViewUtils
import com.Azhe.ghs.gshschedule.utils.getPrefer
import com.Azhe.ghs.gshschedule.widget.TipTextView
import splitties.dimensions.dip
import java.text.ParseException
import kotlin.math.roundToInt

class ScheduleAppWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        if (intent != null) {
            val list = intent.data?.schemeSpecificPart?.split(",")
                    ?: return ScheduleRemoteViewsFactory()
            if (list.size < 2) {
                return ScheduleRemoteViewsFactory(nextWeek = (list[0] == "1"))
            }
            return if (list[0] == "1") {
                ScheduleRemoteViewsFactory(list[1].toInt(), true)
            } else {
                ScheduleRemoteViewsFactory(list[1].toInt(), false)
            }
        } else {
            return ScheduleRemoteViewsFactory()
        }
    }

    private inner class ScheduleRemoteViewsFactory(val tableId: Int = -1, val nextWeek: Boolean = false) : RemoteViewsFactory {
        private lateinit var table: TableBean
        private var week = 0
        private var widgetItemHeight = 0
        private var marTop = 0
        private var alphaInt = 255
        private val dataBase = AppDatabase.getDatabase(applicationContext)
        private val tableDao = dataBase.tableDao()
        private val courseDao = dataBase.courseDao()
        private val timeDao = dataBase.timeDetailDao()
        private val timeList = arrayListOf<TimeDetailBean>()
        private val allCourseList = Array(7) { listOf<CourseBean>() }

        override fun onCreate() {

        }

        override fun onDataSetChanged() {
            table = if (tableId == -1) {
                tableDao.getDefaultTableSync()
            } else {
                tableDao.getTableByIdSync(tableId) ?: tableDao.getDefaultTableSync()
            }

            try {
                week = if (nextWeek) countWeek(table.startDate, table.sundayFirst) + 1
                else countWeek(table.startDate, table.sundayFirst)
            } catch (e: ParseException) {
                e.printStackTrace()
            }
            if (week <= 0) {
                week = 1
            }

            widgetItemHeight = dip(table.widgetItemHeight)
            marTop = resources.getDimensionPixelSize(R.dimen.weekItemMarTop)
            alphaInt = (255 * (table.widgetItemAlpha.toFloat() / 100)).roundToInt()

            for (i in 1..7) {
                allCourseList[i - 1] = courseDao.getCourseByDayOfTableSync(i, table.id)
            }

            timeList.clear()
            timeList.addAll(timeDao.getTimeListSync(table.timeTable))
        }

        override fun onDestroy() {
            timeList.clear()
        }

        override fun getCount(): Int {
            // One row per node, rendered individually to stay under IPC size limit
            return table.nodes
        }

        override fun getViewAt(position: Int): RemoteViews {
            val mRemoteViews = RemoteViews(applicationContext.packageName, R.layout.item_schedule_widget)
            val nodeNumber = position + 1 // 1-based node number
            val bmp = renderNodeRow(nodeNumber)
            if (bmp != null) {
                mRemoteViews.setImageViewBitmap(R.id.iv_schedule, bmp)
            }
            return mRemoteViews
        }

        override fun getLoadingView(): RemoteViews? {
            return null
        }

        override fun getViewTypeCount(): Int {
            return 1
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun hasStableIds(): Boolean {
            return false
        }

        /**
         * Render a single node row at fixed height [widgetItemHeight].
         * Multi-node courses span consecutive rows; TipTextViews use flatTop /
         * flatBottom + noStroke so the block looks continuous.
         */
        private fun renderNodeRow(nodeNumber: Int): Bitmap? {
            return try {
                val nodeIdx = nodeNumber - 1
                val showTimeDetail = applicationContext.getPrefer().getBoolean(Const.KEY_SCHEDULE_DETAIL_TIME, true)
                val dayList = if (table.sundayFirst && table.showSun) listOf(7, 1, 2, 3, 4, 5, 6) else listOf(1, 2, 3, 4, 5, 6, 7)

                val row = LinearLayout(applicationContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                val timeColWidth = dip(36)
                val timeTextSize = (table.widgetItemTextSize - 2).toFloat().coerceAtLeast(7f)
                val hasTime = showTimeDetail && timeList.isNotEmpty() && nodeIdx < timeList.size

                // ── Time cell: fixed height, compact 3‑label stack ──
                val timeCell = LinearLayout(applicationContext).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(timeColWidth, widgetItemHeight)
                }
                timeCell.addView(TextView(applicationContext).apply {
                    text = "$nodeNumber"
                    setTextColor(table.widgetTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    gravity = Gravity.CENTER; maxLines = 1
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                if (hasTime) {
                    timeCell.addView(TextView(applicationContext).apply {
                        text = timeList[nodeIdx].startTime
                        setTextColor(table.widgetTextColor)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, timeTextSize)
                        gravity = Gravity.CENTER; maxLines = 1
                    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                    timeCell.addView(TextView(applicationContext).apply {
                        text = timeList[nodeIdx].endTime
                        setTextColor(table.widgetTextColor)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, timeTextSize)
                        gravity = Gravity.CENTER; maxLines = 1
                    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                }
                row.addView(timeCell)

                // ── Day columns: fixed height, one card per day ──
                for (d in dayList) {
                    if (d == 6 && !table.showSat) continue
                    if (d == 7 && !table.showSun) continue

                    var activeCourse: CourseBean? = null
                    var activeIsOtherWeek = false
                    var activeIsStart = false
                    val dayCourses = allCourseList[d - 1]

                    for (c in dayCourses) {
                        if (c.endWeek < week) continue
                        val isOtherWeek = (week % 2 == 0 && c.type == 1) || (week % 2 == 1 && c.type == 2) || (c.startWeek > week)
                        if (!table.showOtherWeekCourse && isOtherWeek) continue
                        if (c.startNode == nodeNumber) {
                            activeCourse = c; activeIsOtherWeek = isOtherWeek; activeIsStart = true; break
                        }
                        if (c.startNode < nodeNumber && c.startNode + c.step > nodeNumber) {
                            if (activeCourse == null) {
                                activeCourse = c; activeIsOtherWeek = isOtherWeek; activeIsStart = false
                            }
                        }
                    }

                    val cell = FrameLayout(applicationContext).apply {
                        layoutParams = LinearLayout.LayoutParams(0, widgetItemHeight, 1f)
                    }

                    if (activeCourse != null) {
                        val c = activeCourse
                        val origStep = c.step
                        val origStart = c.startNode
                        val actualStep = origStep.coerceIn(1, table.nodes - origStart + 1)
                        val isFirstRow = (nodeNumber == origStart)
                        val isLastRow = (nodeNumber == origStart + actualStep - 1)
                        val isMultiNode = actualStep > 1

                        if (isFirstRow || !isMultiNode) {
                            // First row (or single-node): TipTextView with text.
                            val courseView = buildCourseView(c, activeIsOtherWeek)
                            if (isMultiNode) {
                                courseView.flatBottom = true
                                courseView.noStroke = true
                            }
                            cell.addView(courseView, FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            ))
                        } else {
                            // Continuation row: plain coloured block.
                            cell.addView(buildContinuationBlock(c, isLastRow, activeIsOtherWeek),
                                FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                ).apply {
                                    leftMargin = dip(1); rightMargin = dip(1)
                                })
                            // Show course end time on the LAST row so it doesn't overlap with course name text.
                            if (isLastRow && hasTime) {
                                cell.addView(TextView(applicationContext).apply {
                                    text = timeList[nodeIdx].endTime
                                    setTextColor(table.widgetCourseTextColor)
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, timeTextSize)
                                    alpha = 0.75f; maxLines = 1; gravity = Gravity.END
                                    setPadding(dip(2), dip(1), dip(4), dip(2))
                                }, FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    gravity = Gravity.BOTTOM or Gravity.END
                                })
                            }
                        }
                    }
                    row.addView(cell)
                }

                // ── Measure at fixed row height ──
                val screenWidth = ViewUtils.getScreenInfo(applicationContext)[0]
                val wSpec = View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY)
                val hSpec = View.MeasureSpec.makeMeasureSpec(widgetItemHeight, View.MeasureSpec.EXACTLY)
                row.measure(wSpec, hSpec)
                row.layout(0, 0, row.measuredWidth, row.measuredHeight)

                if (row.measuredWidth <= 0 || row.measuredHeight <= 0) return null

                val bmp = Bitmap.createBitmap(row.measuredWidth, row.measuredHeight, Bitmap.Config.ARGB_8888)
                row.draw(Canvas(bmp))
                bmp
            } catch (e: Exception) {
                val errMsg = "${e.javaClass.simpleName}: ${e.message}"
                val errorView = TextView(applicationContext).apply {
                    text = errMsg
                    setTextColor(Color.RED)
                    textSize = 12f
                    setPadding(dip(8), dip(8), dip(8), dip(8))
                }
                val wSpec = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
                val hSpec = View.MeasureSpec.makeMeasureSpec(widgetItemHeight, View.MeasureSpec.EXACTLY)
                errorView.measure(wSpec, hSpec)
                errorView.layout(0, 0, errorView.measuredWidth, errorView.measuredHeight)
                val bmp = Bitmap.createBitmap(maxOf(errorView.measuredWidth, 1), maxOf(errorView.measuredHeight, 1), Bitmap.Config.ARGB_8888)
                errorView.draw(Canvas(bmp))
                bmp
            }
        }

        /** Build a single TipTextView course card. */
        private fun buildCourseView(c: CourseBean, isOtherWeek: Boolean): TipTextView {
            var isError = false
            val strBuilder = StringBuilder()

            if (c.step <= 0) { c.step = 1; isError = true }
            if (c.startNode <= 0) { c.startNode = 1; isError = true }
            if (c.startNode > table.nodes) { c.startNode = table.nodes; isError = true }
            if (c.startNode + c.step - 1 > table.nodes) {
                c.step = table.nodes - c.startNode + 1; isError = true
            }

            val bgColor = try {
                if (c.color.isNotEmpty() && c.color.startsWith("#")) {
                    Color.parseColor(c.color)
                } else {
                    ViewUtils.getCustomizedColor(applicationContext, c.id % 16)
                }
            } catch (_: Exception) {
                ViewUtils.getCustomizedColor(applicationContext, c.id % 16)
            }

            if (table.showTime && timeList.isNotEmpty() && c.startNode - 1 < timeList.size) {
                strBuilder.append(timeList[c.startNode - 1].startTime).append(" ")
            }
            strBuilder.append(c.courseName)
            // 地点 + 教师（二者之间有间距）
            if (c.room != "" || !c.teacher.isNullOrBlank()) {
                strBuilder.append("\n")
                if (c.room != "") strBuilder.append("@${c.room}")
                if (c.room != "" && !c.teacher.isNullOrBlank()) strBuilder.append("  ")
                if (!c.teacher.isNullOrBlank()) strBuilder.append(c.teacher)
            }

            // Compact week-type label: "单"/"双" saves horizontal space vs "单周"/"双周".
            // [非本周] is omitted — the 30 % alpha already identifies other-week courses.
            when (c.type) {
                1 -> strBuilder.append("\n单")
                2 -> strBuilder.append("\n双")
            }
            if (isOtherWeek) {
                // Append a subtle marker only when truly needed (still faded, but adds context)
                strBuilder.append("·非本周")
            }

            val view = TipTextView(applicationContext)
            // Top padding 1 dp instead of 2 dp to give text more room vertically
            view.setPadding(dip(2), dip(1), dip(2), dip(2))
            if (isError) view.tipVisibility = TipTextView.TIP_ERROR
            if (isOtherWeek) view.tipVisibility = TipTextView.TIP_OTHER_WEEK

            // Use one sp smaller than the main-app item size so text fits the fixed‑height
            // widget row even with 3-4 lines (name + room + week label).
            val compactSize = (table.widgetItemTextSize - 1).coerceAtLeast(7)
            view.init(
                text = strBuilder.toString(),
                txtSize = compactSize,
                txtColor = table.widgetCourseTextColor,
                bgColor = bgColor,
                bgAlpha = alphaInt,
                stroke = table.widgetStrokeColor
            )
            return view
        }

        /**
         * Build a plain coloured block for continuation rows of a multi-node course.
         * No text, no stroke — just the background colour so the block looks continuous
         * with the TipTextView in the first row.  The last row gets rounded bottom corners.
         */
        private fun buildContinuationBlock(c: CourseBean, isLastRow: Boolean, isOtherWeek: Boolean): View {
            val bgColor = try {
                if (c.color.isNotEmpty() && c.color.startsWith("#")) {
                    Color.parseColor(c.color)
                } else {
                    ViewUtils.getCustomizedColor(applicationContext, c.id % 16)
                }
            } catch (_: Exception) {
                ViewUtils.getCustomizedColor(applicationContext, c.id % 16)
            }
            // Match TipTextView's other-week alpha: 30 % of normal
            val a = if (isOtherWeek) (alphaInt * 0.3).toInt() else alphaInt
            val combined = Color.argb(a, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
            return if (isLastRow) {
                val cr = dip(4).toFloat()
                View(applicationContext).apply {
                    background = GradientDrawable().apply {
                        setColor(combined)
                        cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, cr, cr, cr, cr)
                    }
                }
            } else {
                View(applicationContext).apply { setBackgroundColor(combined) }
            }
        }
    }

}