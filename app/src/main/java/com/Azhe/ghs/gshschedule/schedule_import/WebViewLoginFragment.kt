package com.Azhe.ghs.gshschedule.schedule_import

import android.app.Activity.RESULT_OK
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.Azhe.ghs.gshschedule.R
import com.Azhe.ghs.gshschedule.apply_info.ApplyInfoActivity
import com.Azhe.ghs.gshschedule.base_view.BaseFragment
import com.Azhe.ghs.gshschedule.databinding.FragmentWebViewLoginBinding
import com.Azhe.ghs.gshschedule.utils.Const
import com.Azhe.ghs.gshschedule.utils.Utils
import com.Azhe.ghs.gshschedule.utils.ViewUtils
import com.Azhe.ghs.gshschedule.utils.getPrefer
import es.dmoral.toasty.Toasty
import splitties.activities.start
import splitties.snackbar.longSnack

class WebViewLoginFragment : BaseFragment() {

    private var _binding: FragmentWebViewLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var url: String
    private val viewModel by activityViewModels<ImportViewModel>()
    private var isRefer = false
    // HTTPS 打不开时自动降级 HTTP 的目标教务主机（成都银杏酒店管理学院），只降级一次避免循环
    private val httpFallbackHost = "jwxt.gingkoc.edu.cn"
    private var httpFallbackUsed = false
    private val hostRegex = Regex("""(http|https)://.*?/""")
    private var tips = "1. 在上方输入教务网址，部分学校需要连接校园网\n2. 登录后点击到个人课表的页面，注意选择自己需要导入的学期\n3. 点击右下角的按钮完成导入\n4. 如果遇到总是提示密码错误或者网页错位等问题，可以取消底栏的「电脑模式」或者调节字体缩放"
    private var zoom = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            url = it.getString("url")!!
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        _binding = FragmentWebViewLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    @JavascriptInterface
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewUtils.resizeStatusBar(context!!.applicationContext, view.findViewById(R.id.v_status))

        if (url != "") {
            binding.etUrl.setText(url)
            startVisit()
        } else {
            val url = context!!.getPrefer().getString(Const.KEY_SCHOOL_URL, "")
            if (url != "") {
                binding.etUrl.setText(url)
            } else {
                binding.etUrl.setText("https://www.baidu.com")
            }
            startVisit()
        }

        if (viewModel.importType == "apply") {
            tips = "1. 在上方输入教务网址，部分学校需要连接校园网\n2. 登录后点击到个人课表或者相关的页面\n3. 点击右下角的按钮抓取源码，并上传到服务器"
        }

        if (viewModel.school == "强智教务") {
            binding.cgQz.visibility = View.VISIBLE
            binding.chipQz1.isChecked = true
        } else {
            binding.cgQz.visibility = View.GONE
        }

        if (viewModel.school == "正方教务") {
            binding.cgZf.visibility = View.VISIBLE
            binding.chipZf1.isChecked = true
            tips = "1. 在上方输入教务网址，部分学校需要连接校园网\n2. 登录后点击到「个人课表」的页面，注意不是「班级课表」！注意选择自己需要导入的学期。正方教务目前仅支持个人课表的导入\n3. 点击右下角的按钮完成导入\n" +
                    "4. 如果遇到总是提示密码错误或者网页错位等问题，可以取消底栏的「电脑模式」或者调节字体缩放"
        } else {
            binding.cgZf.visibility = View.GONE
        }

        if (viewModel.importType == Common.TYPE_HNUST) {
            binding.cgOldQz.visibility = View.VISIBLE
            binding.chipOldQz2.isChecked = true
            viewModel.oldQzType = 1
        } else {
            binding.cgOldQz.visibility = View.GONE
        }

        MaterialAlertDialogBuilder(requireActivity())
                .setTitle("注意事项")
                .setMessage(tips)
                .setPositiveButton("我知道啦", null)
                .setNeutralButton("如何正确选择教务？") { _, _ ->
                    // 已移除在线帮助链接
                }
                .setCancelable(false)
                .show()

        binding.wvCourse.settings.javaScriptEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            binding.wvCourse.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        binding.wvCourse.addJavascriptInterface(InJavaScriptLocalObj(), "local_obj")
        binding.wvCourse.webViewClient = object : WebViewClient() {

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                MaterialAlertDialogBuilder(requireActivity())
                        .setMessage("SSL证书验证失败")
                        .setPositiveButton("继续浏览") { _, _ ->
                            handler.proceed()
                        }
                        .setNegativeButton("取消") { _, _ ->
                            handler.cancel()
                        }
                        .setCancelable(false)
                        .show()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                // HTTPS 主页面打不开时（运营商封堵/连接超时等），自动切换到 HTTP 备用链接
                if (!httpFallbackUsed && request.isForMainFrame &&
                        request.url.host == httpFallbackHost &&
                        request.url.toString().startsWith("https://")) {
                    httpFallbackUsed = true
                    val httpUrl = request.url.toString().replaceFirst("https://", "http://")
                    binding.etUrl.setText(httpUrl)
                    Toasty.warning(requireContext(), "HTTPS 无法访问，已自动切换到 HTTP 备用链接").show()
                    view.loadUrl(httpUrl)
                }
            }

        }
        binding.wvCourse.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress == 100) {
                    binding.pbLoad.progress = newProgress
                    binding.pbLoad.visibility = View.GONE
                    // Toasty.info(activity!!, wv_course.url, Toast.LENGTH_LONG).show()
                } else {
                    binding.pbLoad.progress = newProgress * 5
                    binding.pbLoad.visibility = View.VISIBLE
                }
            }
        }
        // 设置自适应屏幕，两者合用
        binding.wvCourse.settings.useWideViewPort = true //将图片调整到适合WebView的大小
        binding.wvCourse.settings.loadWithOverviewMode = true // 缩放至屏幕的大小
        // 缩放操作
        binding.wvCourse.settings.setSupportZoom(true) //支持缩放，默认为true。是下面那个的前提。
        binding.wvCourse.settings.builtInZoomControls = true //设置内置的缩放控件。若为false，则该WebView不可缩放
        binding.wvCourse.settings.displayZoomControls = false //隐藏原生的缩放控件wvCourse.settings
        binding.wvCourse.settings.javaScriptCanOpenWindowsAutomatically = true
        binding.wvCourse.settings.domStorageEnabled = true
        binding.wvCourse.settings.userAgentString = binding.wvCourse.settings.userAgentString.replace("Mobile", "eliboM").replace("Android", "diordnA")
        binding.wvCourse.settings.textZoom = 100
        initEvent()
    }

    private fun initEvent() {

        binding.chipMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.wvCourse.settings.userAgentString = binding.wvCourse.settings.userAgentString.replace("Mobile", "eliboM").replace("Android", "diordnA")
            } else {
                binding.wvCourse.settings.userAgentString = binding.wvCourse.settings.userAgentString.replace("eliboM", "Mobile").replace("diordnA", "Android")
            }
            binding.wvCourse.reload()
        }

        binding.chipZoom.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(requireActivity())
                    .setTitle("设置缩放")
                    .setView(R.layout.dialog_edit_text)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.sure, null)
                    .create()
            dialog.show()
            val inputLayout = dialog.findViewById<TextInputLayout>(R.id.text_input_layout)
            val editText = dialog.findViewById<TextInputEditText>(R.id.edit_text)
            inputLayout?.helperText = "范围 10 ~ 200"
            inputLayout?.suffixText = "%"
            editText?.inputType = InputType.TYPE_CLASS_NUMBER
            val valueStr = zoom.toString()
            editText?.setText(valueStr)
            editText?.setSelection(valueStr.length)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = editText?.text
                if (value.isNullOrBlank()) {
                    inputLayout?.error = "数值不能为空哦>_<"
                    return@setOnClickListener
                }
                val valueInt = try {
                    value.toString().toInt()
                } catch (e: Exception) {
                    inputLayout?.error = "输入异常>_<"
                    return@setOnClickListener
                }
                if (valueInt < 10 || valueInt > 200) {
                    inputLayout?.error = "注意范围 10 ~ 200"
                    return@setOnClickListener
                }
                zoom = valueInt
                binding.wvCourse.settings.textZoom = zoom
                binding.chipZoom.text = "文字缩放 $zoom%"
                binding.wvCourse.reload()
                dialog.dismiss()
            }
        }

        var qzChipId = R.id.chip_qz1
        binding.cgQz.setOnCheckedChangeListener { chipGroup, id ->
            when (id) {
                R.id.chip_qz1 -> {
                    qzChipId = id
                    viewModel.qzType = 0
                }
                R.id.chip_qz2 -> {
                    qzChipId = id
                    viewModel.qzType = 1
                }
                R.id.chip_qz3 -> {
                    qzChipId = id
                    viewModel.qzType = 2
                }
                R.id.chip_qz4 -> {
                    qzChipId = id
                    viewModel.qzType = 3
                }
                else -> {
                    chipGroup.findViewById<Chip>(qzChipId).isChecked = true
                }
            }
        }

        var zfChipId = R.id.chip_zf1
        binding.cgZf.setOnCheckedChangeListener { chipGroup, id ->
            when (id) {
                R.id.chip_zf1 -> {
                    zfChipId = id
                    viewModel.zfType = 0
                }
                R.id.chip_zf2 -> {
                    zfChipId = id
                    viewModel.zfType = 1
                }
                else -> {
                    chipGroup.findViewById<Chip>(zfChipId).isChecked = true
                }
            }
        }

        var oldQZChipId = R.id.chip_old_qz2
        binding.cgOldQz.setOnCheckedChangeListener { chipGroup, id ->
            when (id) {
                R.id.chip_old_qz1 -> {
                    oldQZChipId = id
                    viewModel.oldQzType = 0
                }
                R.id.chip_old_qz2 -> {
                    oldQZChipId = id
                    viewModel.oldQzType = 1
                }
                else -> {
                    chipGroup.findViewById<Chip>(oldQZChipId).isChecked = true
                }
            }
        }

        binding.tvGo.setOnClickListener {
            startVisit()
        }

        binding.etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                startVisit()
            }
            return@setOnEditorActionListener false
        }

        val js = "javascript:var ifrs=document.getElementsByTagName(\"iframe\");" +
                "var iframeContent=\"\";" +
                "for(var i=0;i<ifrs.length;i++){" +
                "iframeContent=iframeContent+ifrs[i].contentDocument.body.parentElement.outerHTML;" +
                "}\n" +
                "var frs=document.getElementsByTagName(\"frame\");" +
                "var frameContent=\"\";" +
                "for(var i=0;i<frs.length;i++){" +
                "frameContent=frameContent+frs[i].contentDocument.body.parentElement.outerHTML;" +
                "}\n" +
                "window.local_obj.showSource(document.getElementsByTagName('html')[0].innerHTML + iframeContent + frameContent);"

        binding.fabImport.setOnClickListener {
            if (viewModel.importType == Common.TYPE_HNUST) {
                if (!isRefer) {
                    val referUrl = when (viewModel.school) {
                        "湖南科技大学" -> "http://kdjw.hnust.cn/kdjw/tkglAction.do?method=goListKbByXs&istsxx=no"
                        "湖南科技大学潇湘学院" -> "http://xxjw.hnust.cn:8080/xxjw/tkglAction.do?method=goListKbByXs&istsxx=no"
                        else -> getHostUrl() + "tkglAction.do?method=goListKbByXs&istsxx=no"
                    }
                    binding.wvCourse.loadUrl(referUrl)
                    it.longSnack("请在看到网页加载完成后，再点一次右下角按钮")
                    isRefer = true
                } else {
                    binding.wvCourse.loadUrl(js)
                }
            } else if (viewModel.importType == Common.TYPE_CF) {
                if (!isRefer) {
                    val referUrl = getHostUrl() + "xsgrkbcx!getXsgrbkList.action"
                    binding.wvCourse.loadUrl(referUrl)
                    it.longSnack("请重新选择一下学期再点按钮导入，要记得选择全部周，记得点查询按钮")
                    isRefer = true
                } else {
                    binding.wvCourse.loadUrl(js)
                }
            } else if (viewModel.importType == Common.TYPE_URP || viewModel.isUrp) {
                if (!isRefer) {
                    val referUrl = getHostUrl() + "xkAction.do?actionType=6"
                    binding.wvCourse.loadUrl(referUrl)
                    it.longSnack("请在看到网页加载完成后，再点一次右下角按钮")
                    isRefer = true
                } else {
                    binding.wvCourse.loadUrl(js)
                }
            } else if (viewModel.importType == Common.TYPE_URP_NEW) {
                if (!isRefer) {
                    val referUrl = getHostUrl() + "student/courseSelect/thisSemesterCurriculum/callback"
                    binding.wvCourse.loadUrl(referUrl)
                    it.longSnack("请在看到网页加载完成后，再点一次右下角按钮")
                    isRefer = true
                } else {
                    binding.wvCourse.loadUrl("javascript:window.local_obj.showSource(document.getElementsByTagName('html')[0].innerText);")
                }
            } else {
                binding.wvCourse.loadUrl(js)
            }
        }

        binding.btnBack.setOnClickListener {
            if (binding.wvCourse.canGoBack()) {
                binding.wvCourse.goBack()
            }
        }
    }

    private fun getHostUrl(): String {
        var url = binding.wvCourse.url ?: ""
        if (!url.endsWith('/')) {
            url += "/"
        }
        return hostRegex.find(binding.wvCourse.url ?: "")?.value ?: (binding.wvCourse.url ?: "")
    }

    private fun startVisit() {
        binding.wvCourse.visibility = View.VISIBLE
        binding.llError.visibility = View.GONE
        val url = if (binding.etUrl.text.toString().startsWith("http://") || binding.etUrl.text.toString().startsWith("https://"))
            binding.etUrl.text.toString() else "http://" + binding.etUrl.text.toString()
        if (URLUtil.isHttpUrl(url) || URLUtil.isHttpsUrl(url)) {
            binding.wvCourse.loadUrl(url)
            context!!.getPrefer().edit {
                putString(Const.KEY_SCHOOL_URL, url)
            }
        } else {
            Toasty.error(context!!, "请输入正确的网址╭(╯^╰)╮").show()
        }
    }

    internal inner class InJavaScriptLocalObj {
        @JavascriptInterface
        fun showSource(html: String) {
            // Log.d("源码", html)
            if (viewModel.importType != "apply") {
                launch {
                    try {
                        val result = viewModel.importSchedule(html)
                        Toasty.success(activity!!,
                                "成功导入 $result 门课程(ﾟ▽ﾟ)/\n请在右侧栏切换后查看").show()
                        activity!!.setResult(RESULT_OK)
                        activity!!.finish()
                    } catch (e: Exception) {
                        Toasty.error(activity!!,
                                "导入失败>_<\n${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                launch {
                    try {
                        viewModel.postHtml(
                                school = viewModel.schoolInfo[0],
                                type = if (viewModel.isUrp) "URP" else viewModel.schoolInfo[1],
                                qq = viewModel.schoolInfo[2],
                                html = html)
                        Toasty.success(activity!!.applicationContext, "上传源码成功~请等待适配哦", Toast.LENGTH_LONG).show()
                        activity!!.start<ApplyInfoActivity>()
                        activity!!.finish()
                    } catch (e: Exception) {
                        Toasty.error(activity!!.applicationContext, "上传失败>_<\n" + e.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.wvCourse?.clearCache(true)
        binding.wvCourse?.clearHistory()
        binding.wvCourse?.removeAllViews()
        binding.wvCourse?.destroy()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance(url: String = "") =
                WebViewLoginFragment().apply {
                    arguments = Bundle().apply {
                        putString("url", url)
                    }
                }
    }
}
