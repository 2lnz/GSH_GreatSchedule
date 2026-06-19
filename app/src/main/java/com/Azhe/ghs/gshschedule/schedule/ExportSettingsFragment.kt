package com.Azhe.ghs.gshschedule.schedule

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.BaseDialogFragment
import androidx.fragment.app.activityViewModels
import com.Azhe.ghs.gshschedule.R
import com.Azhe.ghs.gshschedule.utils.Const
import es.dmoral.toasty.Toasty

class ExportSettingsFragment : BaseDialogFragment() {

    override val layoutId: Int
        get() = R.layout.fragment_export_settings

    private val viewModel by activityViewModels<ScheduleViewModel>()

    val tableName by lazy(LazyThreadSafetyMode.NONE) {
        if (viewModel.table.tableName == "") "我的课表" else viewModel.table.tableName
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false

        val tvExport = view.findViewById<TextView>(R.id.tv_export)
        val tvExportIcs = view.findViewById<TextView>(R.id.tv_export_ics)
        val tvCancel = view.findViewById<TextView>(R.id.tv_cancel)

        // 程序化居中 — 不依赖 XML 属性，不被任何主题覆盖
        tvExport.gravity = Gravity.CENTER
        tvExportIcs.gravity = Gravity.CENTER
        tvCancel.gravity = Gravity.CENTER

        tvExport.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, "$tableName.wakeup_schedule")
            }
            Toasty.info(activity!!, "请自行选择导出的地方\n不要修改文件的扩展名哦", Toasty.LENGTH_LONG).show()
            activity?.startActivityForResult(intent, Const.REQUEST_CODE_EXPORT)
            dismiss()
        }

        tvExportIcs.setOnLongClickListener {
            Toasty.info(activity!!, "ICS 文件可导入到系统日历应用中", Toasty.LENGTH_LONG).show()
            true
        }

        tvExportIcs.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/calendar"
                putExtra(Intent.EXTRA_TITLE, "日历-$tableName")
            }
            Toasty.info(activity!!, "请自行选择导出的地方\n不要修改文件的扩展名哦", Toasty.LENGTH_LONG).show()
            activity?.startActivityForResult(intent, Const.REQUEST_CODE_EXPORT_ICS)
            dismiss()
        }

        tvCancel.setOnClickListener {
            dismiss()
        }
    }
}
