package com.Azhe.ghs.gshschedule.utils

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences

fun Context.getPrefer(name: String = "config"): SharedPreferences = getSharedPreferences(name, MODE_PRIVATE)

object Const {

    const val REQUEST_CODE_EXPORT = 100
    const val REQUEST_CODE_IMPORT = 101
    const val REQUEST_CODE_SCHEDULE_SETTING = 102
    const val REQUEST_CODE_EXPORT_ICS = 103
    const val REQUEST_CODE_IMPORT_FILE = 104
    const val REQUEST_CODE_IMPORT_HTML = 105
    const val REQUEST_CODE_IMPORT_CSV = 106
    const val REQUEST_CODE_CHOOSE_SCHOOL = 107
    const val REQUEST_CODE_ADD_COURSE = 108

    const val KEY_OLD_VERSION_COURSE = "course"
    const val KEY_OLD_VERSION_BG_URI = "pic_uri"
    const val KEY_OLD_VERSION_TERM_START = "termStart"
    const val KEY_HAS_ADJUST = "has_adjust"

    const val KEY_IMPORT_SCHOOL = "import_school"
    const val KEY_SCHOOL_URL = "school_url"
    const val KEY_DAY_NIGHT_THEME = "day_night_theme"
    const val KEY_HAS_COUNT = "has_count"
    const val KEY_CHECK_UPDATE = "s_update"
    const val KEY_SHOW_SUDA_LIFE = "suda_life"
    const val KEY_THEME_COLOR = "nav_bar_color"
    const val KEY_OPEN_TIMES = "open_times"
    const val KEY_HAS_INTRO = "has_intro"
    const val KEY_SCHEDULE_PRE_LOAD = "schedule_pre_load"
    const val KEY_SCHEDULE_DETAIL_TIME = "schedule_detail_time"
    const val KEY_SKIP_PERMISSION_DIALOG = "skip_permission_dialog"

    // ── 公告相关 ──
    /** 公告 JSON 的远程地址（GitHub Pages），可通过设置页覆盖 */
    const val KEY_ANNOUNCEMENT_URL = "announcement_url"
    /** 已读公告 ID 集合（以逗号分隔存储） */
    const val KEY_ANNOUNCEMENT_READ_IDS = "announcement_read_ids"

    // ── 公告页跳转 requestCode ──
    const val REQUEST_CODE_ANNOUNCEMENT = 200

}