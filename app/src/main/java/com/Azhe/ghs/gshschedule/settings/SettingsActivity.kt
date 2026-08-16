package com.Azhe.ghs.gshschedule.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.Azhe.ghs.gshschedule.AppDatabase
import com.Azhe.ghs.gshschedule.DonateActivity
import com.Azhe.ghs.gshschedule.R
import com.Azhe.ghs.gshschedule.base_view.BaseListActivity
import com.Azhe.ghs.gshschedule.dao.TableDao
import com.Azhe.ghs.gshschedule.schedule_settings.ScheduleSettingsActivity
import com.Azhe.ghs.gshschedule.settings.items.*
import com.Azhe.ghs.gshschedule.utils.Const
import com.Azhe.ghs.gshschedule.utils.getPrefer
import com.Azhe.ghs.gshschedule.widget.colorpicker.ColorPickerFragment
import splitties.activities.start
import splitties.resources.color
import splitties.snackbar.longSnack
import splitties.snackbar.snack

class SettingsActivity : BaseListActivity(), ColorPickerFragment.ColorPickerDialogListener {

    override fun onColorSelected(dialogId: Int, color: Int) {
        getPrefer().edit {
            putInt(Const.KEY_THEME_COLOR, color)
        }
        mRecyclerView.longSnack("重启App后生效哦~")
    }

    private lateinit var dataBase: AppDatabase
    private lateinit var tableDao: TableDao
    private val dayNightTheme by lazy(LazyThreadSafetyMode.NONE) {
        resources.getStringArray(R.array.day_night_setting)
    }
    private var dayNightIndex = 2

    private val mAdapter = SettingItemAdapter()

    override fun onSetupSubButton(tvButton: AppCompatTextView): AppCompatTextView? {
        tvButton.text = "捐赠"
        tvButton.setTextColor(color(R.color.colorAccent))
        tvButton.setOnClickListener {
            start<DonateActivity>()
        }
        return tvButton
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dataBase = AppDatabase.getDatabase(application)
        tableDao = dataBase.tableDao()
        dayNightIndex = getPrefer().getInt(Const.KEY_DAY_NIGHT_THEME, 2)

        val items = mutableListOf<BaseSettingItem>()
        onItemsCreated(items)
        mAdapter.data = items
        mRecyclerView.layoutManager = LinearLayoutManager(this)
        mRecyclerView.itemAnimator?.changeDuration = 250
        mRecyclerView.adapter = mAdapter
        mAdapter.addChildClickViewIds(R.id.anko_check_box)
        mAdapter.setOnItemChildClickListener { _, view, position ->
            when (val item = items[position]) {
                is SwitchItem -> onSwitchItemCheckChange(item, view.findViewById<SwitchCompat>(R.id.anko_check_box).isChecked)
            }
        }
        mAdapter.setOnItemClickListener { _, view, position ->
            when (val item = items[position]) {
                is HorizontalItem -> onHorizontalItemClick(item, position)
                is VerticalItem -> onVerticalItemClick(item)
                is SwitchItem -> view.findViewById<SwitchCompat>(R.id.anko_check_box).performClick()
            }
        }
    }

    private fun onItemsCreated(items: MutableList<BaseSettingItem>) {
        items.add(CategoryItem("常规", true))
        items.add(HorizontalItem("设置当前课表", "点这里！"))
        items.add(HorizontalItem("外观", "课表及小部件外观设置"))
        items.add(VerticalItem("主题颜色", "调整大部分标签和虚拟键的颜色。"))
        items.add(SwitchItem("节数栏显示具体时间", getPrefer().getBoolean(Const.KEY_SCHEDULE_DETAIL_TIME, true), ""))
        items.add(SwitchItem("页面预加载", getPrefer().getBoolean(Const.KEY_SCHEDULE_PRE_LOAD, true), "开启后，滑动界面后会马上显示课表。关闭后，滑动界面后需要短暂的时间加载课表，不过理论上内存占用会更小，App启动速度也会更快。"))
        items.add(HorizontalItem("显示主题", dayNightTheme[dayNightIndex]))
        items.add(HorizontalItem("❤捐赠入口❤", "感谢支持！"))
        items.add(VerticalItem("", "\n\n\n"))
    }

    private fun onSwitchItemCheckChange(item: SwitchItem, isChecked: Boolean) {
        when (item.title) {
            "页面预加载" -> {
                getPrefer().edit {
                    putBoolean(Const.KEY_SCHEDULE_PRE_LOAD, isChecked)
                }
                mRecyclerView.snack("重启App后生效哦")
            }
            "节数栏显示具体时间" -> {
                getPrefer().edit {
                    putBoolean(Const.KEY_SCHEDULE_DETAIL_TIME, isChecked)
                }
                mRecyclerView.snack("重启App后生效哦")
            }
        }
        item.checked = isChecked
    }

    private fun onHorizontalItemClick(item: HorizontalItem, position: Int) {
        when (item.title) {
            "设置当前课表" -> {
                launch {
                    val table = tableDao.getDefaultTable()
                    startActivityForResult(
                            Intent(this@SettingsActivity, ScheduleSettingsActivity::class.java).apply {
                                putExtra("tableData", table)
                            }, 180)
                }
            }
            "外观" -> {
                launch {
                    val table = tableDao.getDefaultTable()
                    startActivityForResult(
                            Intent(this@SettingsActivity, ScheduleSettingsActivity::class.java).apply {
                                putExtra("tableData", table)
                                putExtra("appearanceOnly", true)
                            }, 180)
                }
            }
            "❤捐赠入口❤" -> {
                start<DonateActivity>()
            }
            "显示主题" -> {
                MaterialAlertDialogBuilder(this)
                        .setTitle("显示主题")
                        .setPositiveButton("确定") { _, _ ->
                            getPrefer().edit {
                                putInt(Const.KEY_DAY_NIGHT_THEME, dayNightIndex)
                            }
                            item.value = dayNightTheme[dayNightIndex]
                            mAdapter.notifyItemChanged(position)
                            when (dayNightIndex) {
                                0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                                1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                                2 -> {
                                    when {
                                        Build.VERSION.SDK_INT >= 29 -> {
                                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                                        }
                                        Build.VERSION.SDK_INT >= 23 -> {
                                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
                                        }
                                        else -> {
                                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                                        }
                                    }
                                }
                            }
                        }
                        .setSingleChoiceItems(dayNightTheme, dayNightIndex) { _, which ->
                            dayNightIndex = which
                        }
                        .show()
            }
        }
    }

    private fun onVerticalItemClick(item: VerticalItem) {
        when (item.title) {
            "主题颜色" -> {
                ColorPickerFragment.newBuilder()
                        .setShowAlphaSlider(true)
                        .setColor(getPrefer().getInt(Const.KEY_THEME_COLOR, color(R.color.colorAccent)))
                        .show(this)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 180) {
            setResult(RESULT_OK)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
