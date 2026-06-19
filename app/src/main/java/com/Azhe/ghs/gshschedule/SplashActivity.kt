package com.Azhe.ghs.gshschedule

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.Azhe.ghs.gshschedule.schedule.ScheduleActivity
import com.Azhe.ghs.gshschedule.today_appwidget.TodayCourseAppWidget
import com.Azhe.ghs.gshschedule.utils.AnnouncementManager
import com.Azhe.ghs.gshschedule.utils.AppWidgetUtils
import com.Azhe.ghs.gshschedule.utils.Const
import com.Azhe.ghs.gshschedule.utils.TodayWidgetDataManager
import com.Azhe.ghs.gshschedule.utils.UpdateUtils
import com.Azhe.ghs.gshschedule.utils.getPrefer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import splitties.activities.start

class SplashActivity : AppCompatActivity() {

    // ── Permission-flow state ────────────────────────────────────
    // True after "去设置" is clicked; cleared once we enter main.
    // onResume + a polling loop both watch this flag:
    //   • onResume   → fires if the system dialog *is* a full Activity
    //   • polling    → fires if the system dialog is an overlay (no lifecycle change)
    private var waitingForPermission = false
    private val handler = Handler(Looper.getMainLooper())
    private val pollIntervalMs = 400L   // check every 400 ms
    private val pollTimeoutMs  = 6000L  // give up after 6 s

    // ── Lifecycle ────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            UpdateUtils.tranOldData(applicationContext)
            TodayWidgetDataManager.convertAndStore(applicationContext)
            AppWidgetUtils.updateWidget(applicationContext)

            // 预热公告缓存 — 独立协程，不阻塞启动流程
            launch {
                AnnouncementManager.preload(applicationContext)
            }

            try {
                val awm = AppWidgetManager.getInstance(applicationContext)
                val component = ComponentName(applicationContext, TodayCourseAppWidget::class.java)
                for (id in awm.getAppWidgetIds(component)) {
                    val intent = Intent(applicationContext, TodayCourseAppWidget::class.java)
                    intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
                    sendBroadcast(intent)
                }
            } catch (_: Exception) {}

            if (shouldShowPermissionDialog()) {
                showPermissionDialog()
            } else {
                proceedToMain()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // We came back from a full-Activity system dialog / settings page — go!
        if (waitingForPermission) {
            cancelWaiting()
            proceedToMain()
        }
    }

    // ── Permission dialog ────────────────────────────────────────

    private fun shouldShowPermissionDialog(): Boolean {
        if (getPrefer().getBoolean(Const.KEY_SKIP_PERMISSION_DIALOG, false)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) return false
        }
        return true
    }

    private fun showPermissionDialog() {
        val customView = layoutInflater.inflate(R.layout.dialog_permission, null)

        MaterialAlertDialogBuilder(this)
            .setView(customView)
            .setNeutralButton(R.string.widget_permission_why) { _, _ ->
                showExplanationDialog()
            }
            .setPositiveButton(R.string.widget_permission_go) { _, _ ->
                waitingForPermission = true
                requestBatteryOptimization()
                startPolling()
            }
            .setNegativeButton(R.string.widget_permission_later) { _, _ ->
                proceedToMain()
            }
            .setCancelable(false)
            .show()
    }

    private fun showExplanationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.widget_permission_why)
            .setMessage(R.string.widget_permission_explanation)
            .setPositiveButton(R.string.widget_permission_got_it) { _, _ ->
                showPermissionDialog()
            }
            .setCancelable(false)
            .show()
    }

    // ── Battery-optimisation request ─────────────────────────────

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            openAutoStartSettings()
            return
        }

        val opened = tryStart(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
        )

        if (!opened) {
            openAutoStartSettings()
        }
    }

    private fun openAutoStartSettings() {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""

        val opened = tryManufacturerIntent(manufacturer)
            || tryStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            || tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })

        if (!opened) {
            Toast.makeText(this,
                "请手动前往 系统设置 → 应用管理 → 找到本应用，开启自启动并关闭电池优化",
                Toast.LENGTH_LONG).show()
        }
    }

    // ── Polling — detects permission grant from overlay dialogs ──

    private fun startPolling() {
        val startTime = System.currentTimeMillis()

        val pollRunnable = object : Runnable {
            override fun run() {
                if (!waitingForPermission) return  // already navigated

                val granted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && (getSystemService(Context.POWER_SERVICE) as PowerManager)
                        .isIgnoringBatteryOptimizations(packageName)

                if (granted) {
                    // User tapped ALLOW on the system overlay dialog
                    cancelWaiting()
                    proceedToMain()
                    return
                }

                if (System.currentTimeMillis() - startTime >= pollTimeoutMs) {
                    // Timeout — navigate anyway
                    cancelWaiting()
                    proceedToMain()
                    return
                }

                handler.postDelayed(this, pollIntervalMs)
            }
        }

        handler.postDelayed(pollRunnable, pollIntervalMs)
    }

    private fun cancelWaiting() {
        waitingForPermission = false
        handler.removeCallbacksAndMessages(null)
    }

    // ── Manufacturer intent helpers ──────────────────────────────

    private fun tryManufacturerIntent(manufacturer: String): Boolean {
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
                tryStart(Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    putExtra("extra_pkgname", packageName)
                    addCategory(Intent.CATEGORY_DEFAULT)
                })
            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                tryStart(Intent().apply {
                    setClassName("com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                }) || tryStart(Intent("com.huawei.systemmanager.optimize.process.ProtectActivity"))
            manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme") ->
                tryStart(Intent().apply {
                    setClassName("com.coloros.phonemanager",
                        "com.coloros.phonemanager.BatterySettingsActivity")
                })
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
                tryStart(Intent().apply {
                    setClassName("com.iqoo.secure", "com.iqoo.secure.MainActivity")
                })
            manufacturer.contains("samsung") ->
                tryStart(Intent().apply {
                    setClassName("com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity")
                })
            else -> false
        }
    }

    private fun tryStart(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── Navigation ───────────────────────────────────────────────

    private fun proceedToMain() {
        start<ScheduleActivity>()
        finish()
    }

    override fun onBackPressed() {
    }
}
