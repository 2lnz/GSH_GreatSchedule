package com.Azhe.ghs.gshschedule.schedule

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.app.ShareCompat
import androidx.lifecycle.Observer
import com.Azhe.ghs.gshschedule.AppDatabase
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.Azhe.ghs.gshschedule.R
import com.Azhe.ghs.gshschedule.announcement.AnnouncementActivity
import com.Azhe.ghs.gshschedule.base_view.BaseActivity
import com.Azhe.ghs.gshschedule.bean.AnnouncementBean
import com.Azhe.ghs.gshschedule.bean.TableSelectBean
import com.Azhe.ghs.gshschedule.course_add.AddCourseActivity
import com.Azhe.ghs.gshschedule.intro.AboutActivity
import com.Azhe.ghs.gshschedule.schedule_manage.ScheduleManageActivity
import com.Azhe.ghs.gshschedule.schedule_import.LoginWebActivity
import com.Azhe.ghs.gshschedule.schedule_import.SchoolListActivity
import com.Azhe.ghs.gshschedule.schedule_settings.ScheduleSettingsActivity
import com.Azhe.ghs.gshschedule.settings.SettingsActivity
import com.Azhe.ghs.gshschedule.utils.*
import es.dmoral.toasty.Toasty
import splitties.activities.start
import splitties.dimensions.dip
import splitties.resources.styledDimenPxSize
import java.text.ParseException
import kotlin.math.roundToInt

class ScheduleActivity : BaseActivity() {

    private val viewModel by viewModels<ScheduleViewModel>()

    /** 是否有未读公告 — 驱动侧边栏红点 */
    private var hasUnreadAnnouncement by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        // IMPORTANT: invalidate card-state cache BEFORE incrementing dataVersion.
        // LaunchedEffect runs its block AFTER composition, so if we only invalidate
        // there, ScheduleGrid would read the stale cache during the recomposition
        // triggered by dataVersion++ and never hit the remember{} fallback.
        viewModel.invalidateCardStateCache()
        viewModel.dataVersion++
        // 同步刷新所有桌面小部件
        AppWidgetUtils.updateWidget(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val darkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            // 与设置页（XML 主题 colorPrimary=#C98294 + DynamicColors）保持一致的配色：
            // Android 12+ 用壁纸动态色，低版本回退到项目 seed 粉色 #C98294。
            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (darkTheme) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                darkTheme -> darkColorScheme(primary = Color(0xFFC98294))
                else -> lightColorScheme(primary = Color(0xFFC98294))
            }
            MaterialTheme(colorScheme = colorScheme) {
                ScheduleScreen(
                    viewModel = viewModel,
                    onDrawerItemClick = { id -> handleDrawerClick(id) },
                    onExportClick = {
                        ExportSettingsFragment().show(supportFragmentManager, null)
                    },
                    onImportClick = {
                        startActivityForResult(
                            Intent(this, SchoolListActivity::class.java),
                            Const.REQUEST_CODE_IMPORT
                        )
                    },
                    onAddCourseClick = {
                        start<AddCourseActivity> {
                            putExtra("tableId", viewModel.table.id)
                            putExtra("maxWeek", viewModel.table.maxWeek)
                            putExtra("nodes", viewModel.table.nodes)
                            putExtra("id", -1)
                        }
                    },
                    onModifyWeek = {
                        startActivityForResult(
                            Intent(this, ScheduleSettingsActivity::class.java).apply {
                                putExtra("tableData", viewModel.table)
                                putExtra("settingItem", "当前周")
                            },
                            Const.REQUEST_CODE_SCHEDULE_SETTING
                        )
                    },
                    onCreateSchedule = {
                        showCreateScheduleDialog()
                    },
                    onManageSchedule = {
                        startActivityForResult(
                            Intent(this, ScheduleManageActivity::class.java),
                            Const.REQUEST_CODE_SCHEDULE_SETTING
                        )
                    },
                    onTableSwitch = { tableId ->
                        launch {
                            viewModel.changeDefaultTable(tableId)
                            initView()
                            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                            val list = viewModel.getScheduleWidgetIds()
                            val table = viewModel.getDefaultTable()
                            list.forEach {
                                when (it.detailType) {
                                    1 -> AppWidgetUtils.refreshTodayWidget(
                                        applicationContext, appWidgetManager, it.id, table
                                    )
                                }
                            }
                        }
                    },
                    onTimeSettings = {
                        startActivityForResult(
                            Intent(this, ScheduleSettingsActivity::class.java).apply {
                                putExtra("tableData", viewModel.table)
                                putExtra("settingItem", "上课时间")
                            },
                            Const.REQUEST_CODE_SCHEDULE_SETTING
                        )
                    },
                    onChangeBackground = {
                        startActivityForResult(
                            Intent(this, ScheduleSettingsActivity::class.java).apply {
                                putExtra("tableData", viewModel.table)
                                putExtra("appearanceOnly", true)
                            },
                            Const.REQUEST_CODE_SCHEDULE_SETTING
                        )
                    },
                    onCheckCourses = {
                        start<ScheduleManageActivity> {
                            putExtra("selectedTable", TableSelectBean(
                                id = viewModel.table.id,
                                background = viewModel.table.background,
                                tableName = viewModel.table.tableName,
                                maxWeek = viewModel.table.maxWeek,
                                nodes = viewModel.table.nodes,
                                type = viewModel.table.type
                            ))
                        }
                    },
                    onFAQ = {
                        Toasty.info(this, "如有问题请联系开发者 QQ：435292391", Toasty.LENGTH_LONG).show()
                    },
                    hasUnreadAnnouncement = hasUnreadAnnouncement,
                    onAnnouncementClick = {
                        start<AnnouncementActivity>()
                    }
                )
            }
        }

        val json = getPrefer().getString(Const.KEY_OLD_VERSION_COURSE, "")
        if (!json.isNullOrEmpty()) {
            launch {
                try {
                    viewModel.updateFromOldVer(json)
                    Toasty.success(applicationContext, "升级成功~").show()
                } catch (e: Exception) {
                    Toasty.error(applicationContext, "出现异常>_<\n${e.message}").show()
                }
            }
        }

        initView()

        // 拉取公告并弹窗
        checkAndShowAnnouncement()

        viewModel.initTableSelectList().observe(this, Observer {
            if (it == null) return@Observer
            viewModel.tableSelectList.clear()
            viewModel.tableSelectList.addAll(it)
        })
    }

    private fun handleDrawerClick(itemId: Int) {
        when (itemId) {
            R.id.nav_setting -> {
                startActivityForResult(
                    Intent(this, SettingsActivity::class.java),
                    Const.REQUEST_CODE_SCHEDULE_SETTING
                )
            }
            R.id.nav_course -> {
                start<ScheduleManageActivity> {
                    putExtra("selectedTable", TableSelectBean(
                        id = viewModel.table.id,
                        background = viewModel.table.background,
                        tableName = viewModel.table.tableName,
                        maxWeek = viewModel.table.maxWeek,
                        nodes = viewModel.table.nodes,
                        type = viewModel.table.type
                    ))
                }
            }
            R.id.nav_feedback -> {
                Toasty.info(this, "如有问题请联系开发者 QQ：435292391", Toasty.LENGTH_LONG).show()
            }
            R.id.nav_about -> {
                start<AboutActivity>()
            }
        }
    }

    private fun showCreateScheduleDialog() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.setting_schedule_name)
            .setView(R.layout.dialog_edit_text)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.sure, null)
            .create()
        dialog.show()
        val inputLayout = dialog.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.text_input_layout)
        val editText = dialog.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_text)
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = editText?.text
            if (value.isNullOrBlank()) {
                inputLayout?.error = "名称不能为空哦>_<"
            } else {
                launch {
                    try {
                        viewModel.addBlankTable(editText.text.toString())
                        Toasty.success(this@ScheduleActivity, "新建成功~").show()
                    } catch (e: Exception) {
                        Toasty.error(this@ScheduleActivity, "操作失败>_<").show()
                    }
                    dialog.dismiss()
                }
            }
        }
    }

    private fun showImportDialog() {
        val items = arrayOf("从学校教务导入", "从 Excel 导入", "从 Html 文件导入", "从分享/导出文件导入")
        MaterialAlertDialogBuilder(this)
            .setTitle("导入课程")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startActivityForResult(
                        Intent(this, SchoolListActivity::class.java),
                        Const.REQUEST_CODE_IMPORT
                    )
                    1 -> startActivityForResult(
                        Intent(this, LoginWebActivity::class.java).apply {
                            putExtra("import_type", "excel")
                            putExtra("tableId", viewModel.table.id)
                        },
                        Const.REQUEST_CODE_IMPORT
                    )
                    2 -> startActivityForResult(
                        Intent(this, LoginWebActivity::class.java).apply {
                            putExtra("import_type", "html")
                            putExtra("tableId", viewModel.table.id)
                        },
                        Const.REQUEST_CODE_IMPORT
                    )
                    3 -> startActivityForResult(
                        Intent(this, LoginWebActivity::class.java).apply {
                            putExtra("import_type", "file")
                        },
                        Const.REQUEST_CODE_IMPORT
                    )
                }
            }
            .show()
    }

    private fun initView() {
        launch {
            viewModel.table = viewModel.getDefaultTable()
            viewModel.currentWeek = CourseUtils.countWeek(viewModel.table.startDate, viewModel.table.sundayFirst)
            viewModel.selectedWeek = viewModel.currentWeek

            if (viewModel.currentWeek > 0 && viewModel.currentWeek > viewModel.table.maxWeek) {
                // Auto-correct stale start dates (before 2025) to nearest semester start
                val startYear = viewModel.table.startDate.take(4).toIntOrNull() ?: 0
                if (startYear < 2025) {
                    val now = java.util.Calendar.getInstance()
                    val month = now.get(java.util.Calendar.MONTH) + 1 // 1-based
                    val year = now.get(java.util.Calendar.YEAR)
                    // Fall semester starts Sep 1, spring semester starts Mar 1
                    val newDate = if (month >= 9) {
                        "$year-09-01"
                    } else if (month >= 3) {
                        "$year-03-01"
                    } else {
                        "${year - 1}-09-01"
                    }
                    viewModel.table.startDate = newDate
                    launch {
                        val dao = AppDatabase.getDatabase(this@ScheduleActivity).tableDao()
                        dao.updateTable(viewModel.table)
                    }
                    viewModel.currentWeek = CourseUtils.countWeek(viewModel.table.startDate, viewModel.table.sundayFirst)
                    viewModel.selectedWeek = viewModel.currentWeek
                }
            }

            viewModel.timeList = viewModel.getTimeList(viewModel.table.timeTable)
            viewModel.showTimeDetail = getPrefer().getBoolean(Const.KEY_SCHEDULE_DETAIL_TIME, true)
            viewModel.alphaInt = (255 * (viewModel.table.itemAlpha.toFloat() / 100)).roundToInt()
            viewModel.itemHeight = dip(viewModel.table.itemHeight)

            for (i in 1..7) {
                viewModel.getRawCourseByDay(i, viewModel.table.id).observe(this@ScheduleActivity, Observer { list ->
                    if (list == null) return@Observer
                    if (list.isNotEmpty() && list[0].tableId != viewModel.table.id) return@Observer
                    viewModel.allCourseList[i - 1].value = list
                })
            }

            viewModel.dataVersion++
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK) {
            when (requestCode) {
                Const.REQUEST_CODE_EXPORT -> {
                    // Export failed
                }
            }
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        when (requestCode) {
            Const.REQUEST_CODE_SCHEDULE_SETTING -> initView()
            Const.REQUEST_CODE_IMPORT -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle("温馨提示")
                    .setView(AppCompatTextView(this).apply {
                        text = ViewUtils.getHtmlSpannedString("记得<b><font color='#fa6278'>仔细检查</font></b>有没有少课、课程信息对不对哦，不要到时候<b><font color='#fa6278'>一不小心就翘课</font></b>啦<br>解析算法不是100%可靠的哦<br>但会朝这个方向努力")
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        val space = styledDimenPxSize(R.attr.dialogPreferredPadding)
                        setPadding(space, dip(8), space, 0)
                    })
                    .setCancelable(false)
                    .setPositiveButton("我知道啦", null)
                    .show()
            }
            Const.REQUEST_CODE_EXPORT -> {
                val uri = data?.data
                launch {
                    try {
                        viewModel.exportData(uri)
                        showShareDialog("分享课程文件", uri!!)
                    } catch (e: Exception) {
                        Toasty.error(this@ScheduleActivity, "导出失败>_<${e.message}")
                    }
                }
            }
            Const.REQUEST_CODE_EXPORT_ICS -> {
                val uri = data?.data
                launch {
                    try {
                        viewModel.exportICS(uri)
                        showShareDialog("分享日历文件", uri!!)
                    } catch (e: Exception) {
                        Toasty.error(this@ScheduleActivity, "导出失败>_<${e.message}")
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showShareDialog(title: String, uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle("分享")
            .setMessage("成功导出至你指定的路径啦，是否还要分享出去呢？")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("分享") { _, _ ->
                val shareIntent = ShareCompat.IntentBuilder.from(this)
                    .setChooserTitle(title)
                    .setStream(uri)
                    .setType("*/*")
                    .createChooserIntent()
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(shareIntent)
            }
            .setCancelable(false)
            .show()
    }

    // ── 公告弹窗 ─────────────────────────────────────────────

    /**
     * 从远程拉取未读公告，更新红点状态，并弹出第一条公告。
     * 网络异常时静默失败，不影响主流程。
     */
    private fun checkAndShowAnnouncement() {
        launch {
            try {
                val unread = AnnouncementManager.fetchUnreadAnnouncements(this@ScheduleActivity)
                hasUnreadAnnouncement = unread.isNotEmpty()

                if (unread.isNotEmpty()) {
                    showAnnouncementDialog(unread.first())
                }
            } catch (_: Exception) {
                // 静默失败
            }
        }
    }

    /**
     * 显示单条公告弹窗。
     * - "once" 类型：弹窗关闭时自动标记已读并同步红点
     * - "every" 类型：不标记已读
     * - 有 link 时显示「查看详情」按钮
     */
    private fun showAnnouncementDialog(announcement: AnnouncementBean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_announcement, null)
        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tv_announcement_title)
        val tvContent = dialogView.findViewById<android.widget.TextView>(R.id.tv_announcement_content)
        val tvLink = dialogView.findViewById<android.widget.TextView>(R.id.tv_announcement_link)

        tvTitle.text = announcement.title
        tvContent.text = announcement.content

        // 可选链接
        val hasLink = announcement.link.isNotBlank()
        if (hasLink) {
            tvLink.visibility = android.view.View.VISIBLE
            tvLink.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(announcement.link))
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // 关闭时标记已读并同步红点
                if (announcement.type != "every") {
                    AnnouncementManager.markAsRead(this, announcement.id)
                    // 重新检查是否还有未读
                    launch {
                        try {
                            val remaining = AnnouncementManager.fetchUnreadAnnouncements(this@ScheduleActivity)
                            hasUnreadAnnouncement = remaining.isNotEmpty()
                        } catch (_: Exception) {
                            hasUnreadAnnouncement = false
                        }
                    }
                }
            }
            .setNeutralButton("查看全部") { _, _ ->
                start<AnnouncementActivity>()
            }
            .setCancelable(true)
            .create()

        dialog.show()
    }

    override fun onDestroy() {
        AppWidgetUtils.updateWidget(applicationContext)
        super.onDestroy()
    }
}
