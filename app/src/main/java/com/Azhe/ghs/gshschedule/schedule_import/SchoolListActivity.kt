package com.Azhe.ghs.gshschedule.schedule_import

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bigkoo.quicksidebar.listener.OnQuickSideBarTouchListener
import com.google.gson.Gson
import com.Azhe.ghs.gshschedule.AppDatabase
import com.Azhe.ghs.gshschedule.R
import com.Azhe.ghs.gshschedule.databinding.ActivitySchoolListBinding
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_CF
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_GINKGO
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_HELP
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_LOGIN
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_PKU
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_QZ
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_QZ_BR
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_QZ_CRAZY
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_QZ_OLD
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_QZ_WITH_NODE
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_URP
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_URP_NEW
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_ZF
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_ZF_1
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_ZF_NEW
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_BNUZ
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_HNIU
import com.Azhe.ghs.gshschedule.schedule_import.Common.TYPE_HNUST
import com.Azhe.ghs.gshschedule.schedule_import.bean.SchoolInfo
import com.Azhe.ghs.gshschedule.utils.Const
import com.Azhe.ghs.gshschedule.utils.Utils
import com.Azhe.ghs.gshschedule.utils.ViewUtils
import com.Azhe.ghs.gshschedule.utils.getPrefer
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersDecoration
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.launch
import splitties.dimensions.dip
import splitties.resources.color
import splitties.resources.styledColor

class SchoolListActivity : AppCompatActivity(), OnQuickSideBarTouchListener {

    private val letters = HashMap<String, Int>()
    private val showList = arrayListOf<SchoolInfo>()
    private val schools = arrayListOf<SchoolInfo>()
    private lateinit var searchView: AppCompatEditText
    private var fromLocal = false
    private lateinit var binding: ActivitySchoolListBinding

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySchoolListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Add title bar
        (binding.root as LinearLayoutCompat).addView(createTitleBar(), 0)

        try {
            fromLocal = intent.getBooleanExtra("fromLocal", false)
            binding.quickSideBarView.setOnQuickSideBarTouchListener(this)
            initSchoolList()
        } catch (e: Exception) {
            Toasty.error(this, "初始化出错: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createTitleBar() = LinearLayoutCompat(this).apply {
        orientation = LinearLayoutCompat.VERTICAL
        setBackgroundColor(styledColor(R.attr.colorSurface))
        addView(LinearLayoutCompat(context).apply {
            setPadding(0, ViewUtils.getStatusBarHeight(this@SchoolListActivity), 0, 0)
            setBackgroundColor(styledColor(R.attr.colorSurface))
            val outValue = TypedValue()
            context.theme.resolveAttribute(R.attr.selectableItemBackgroundBorderless, outValue, true)

            addView(AppCompatImageButton(context).apply {
                setImageResource(R.drawable.ic_back)
                setBackgroundResource(outValue.resourceId)
                setPadding(dip(8))
                setColorFilter(styledColor(R.attr.colorOnBackground))
                setOnClickListener { onBackPressedDispatcher.onBackPressed() }
            }, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.WRAP_CONTENT, dip(48)))

            val mainTitle = AppCompatTextView(context).apply {
                text = title
                gravity = Gravity.CENTER_VERTICAL
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(mainTitle, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.WRAP_CONTENT, dip(48)).apply {
                weight = 1f
            })

            searchView = AppCompatEditText(context).apply {
                hint = "请输入……"
                textSize = 16f
                background = null
                gravity = Gravity.CENTER_VERTICAL
                visibility = View.GONE
                setLines(1)
                setSingleLine()
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {}
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        showList.clear()
                        if (s.isNullOrBlank()) {
                            showList.addAll(schools)
                        } else {
                            showList.addAll(schools.filter { it.name.contains(s.toString()) })
                        }
                        binding.recyclerView.adapter?.notifyDataSetChanged()
                    }
                })
            }
            addView(searchView, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.WRAP_CONTENT, dip(48)).apply {
                weight = 1f
            })

            val iconFont = ResourcesCompat.getFont(context, R.font.iconfont)
            addView(AppCompatTextView(context).apply {
                textSize = 20f
                typeface = iconFont
                text = ""
                gravity = Gravity.CENTER
                setBackgroundResource(outValue.resourceId)
                setOnClickListener {
                    mainTitle.visibility = View.GONE
                    searchView.visibility = View.VISIBLE
                    setTextColor(color(R.color.colorAccent))
                    searchView.isFocusable = true
                    searchView.isFocusableInTouchMode = true
                    searchView.requestFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(searchView, 0)
                }
            }, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.WRAP_CONTENT, dip(48)).apply {
                marginEnd = dip(24)
            })
        }, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT))
    }

    private fun initSchoolList() {
        val dataBase = AppDatabase.getDatabase(application)
        val tableDao = dataBase.tableDao()
        val gson = Gson()
        schools.apply {
            add(SchoolInfo("*", GINKGO_NAME, GINKGO_URL, TYPE_GINKGO))
            add(SchoolInfo("*", "北京邮电大学新教务", "", TYPE_QZ_WITH_NODE))
            add(SchoolInfo("*", "北京邮电大学新教务（VPN）", "", TYPE_QZ_WITH_NODE))
            add(SchoolInfo("*", "北京邮电大学新教务（WebVPN）", "", TYPE_QZ_WITH_NODE))
            getImportSchoolBean()?.let {
                it.sortKey = "★"
                add(it)
            }
            add(SchoolInfo("通", "如何正确选择教务类型？", "", TYPE_HELP))
            add(SchoolInfo("通", "新 URP 系统", "", TYPE_URP_NEW))
            add(SchoolInfo("通", "URP 系统", "", TYPE_URP))
            add(SchoolInfo("通", "新正方教务", "", TYPE_ZF_NEW))
            add(SchoolInfo("通", "正方教务", "", TYPE_ZF))
            add(SchoolInfo("通", "强智教务", "", TYPE_QZ))
            add(SchoolInfo("通", "旧强智（需要 IE 的那种）", "", TYPE_QZ_OLD))
            add(SchoolInfo("B", "北京邮电大学新教务", "", TYPE_QZ_WITH_NODE))
            add(SchoolInfo("B", "北京邮电大学新教务（VPN）", "", TYPE_QZ_WITH_NODE))
            add(SchoolInfo("B", "北京邮电大学新教务（WebVPN）", "", TYPE_QZ_WITH_NODE))
            add(SchoolInfo("C", GINKGO_NAME, GINKGO_URL, TYPE_GINKGO))
        }

        val layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        binding.recyclerView.layoutManager = layoutManager

        showList.addAll(schools)
        val adapter = SchoolImportListAdapter(R.layout.item_apply_info, showList)
        adapter.setOnItemClickListener { _, _, position ->
            if (showList[position].type == TYPE_HELP) {
                Utils.openUrl(this, showList[position].url)
                return@setOnItemClickListener
            }
            if (fromLocal) {
                setResult(Activity.RESULT_OK, Intent().apply { putExtra("type", showList[position].type) })
                finish()
            } else {
                lifecycleScope.launch {
                    if (showList[position].type == Common.TYPE_MAINTAIN) {
                        Toasty.info(this@SchoolListActivity, "处于维护中哦").show()
                        return@launch
                    }
                    getPrefer().edit {
                        putString(Const.KEY_IMPORT_SCHOOL, gson.toJson(showList[position]))
                    }
                    val tableId = tableDao.getDefaultTableId()
                    importLauncher.launch(Intent(this@SchoolListActivity, LoginWebActivity::class.java).apply {
                        putExtra("school_name", showList[position].name)
                        putExtra("import_type", showList[position].type)
                        putExtra("tableId", tableId)
                        putExtra("url", showList[position].url)
                    })
                }
            }
        }

        val customLetters = arrayListOf<String>()
        for ((position, school) in schools.withIndex()) {
            val letter = school.sortKey
            if (!letters.containsKey(letter)) {
                letters[letter] = position
                customLetters.add(letter)
            }
        }

        binding.quickSideBarView.letters = customLetters
        binding.recyclerView.adapter = adapter
        val headersDecor = StickyRecyclerHeadersDecoration(adapter)
        binding.recyclerView.addItemDecoration(headersDecor)
    }

    private fun getImportSchoolBean(): SchoolInfo? {
        val json = getPrefer().getString(Const.KEY_IMPORT_SCHOOL, null) ?: return null
        val gson = Gson()
        val res = gson.fromJson<SchoolInfo>(json, SchoolInfo::class.java)
        if (!res.type.isNullOrEmpty()) {
            // 只有银杏学院保留网址预设；旧版本保存的"新正方"类型升级为专用类型
            return if (res.name == GINKGO_NAME) {
                res.copy(url = GINKGO_URL, type = TYPE_GINKGO)
            } else {
                res.copy(url = "")
            }
        }
        return null
    }

    override fun onLetterTouching(touching: Boolean) {
        binding.quickSideBarTipsView.visibility = if (touching) View.VISIBLE else View.INVISIBLE
    }

    override fun onLetterChanged(letter: String, position: Int, y: Float) {
        binding.quickSideBarTipsView.setText(letter, position, y)
        if (letters.containsKey(letter)) {
            (binding.recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(letters[letter]!!, 0)
        }
    }

    companion object {
        private const val GINKGO_NAME = "成都银杏酒店管理学院"
        private const val GINKGO_URL = "https://jwxt.gingkoc.edu.cn/xtgl/login_slogin.html"
    }
}
