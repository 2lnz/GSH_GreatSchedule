package com.Azhe.ghs.gshschedule.schedule_import.parser

import android.util.Log
import com.Azhe.ghs.gshschedule.schedule_import.Common
import com.Azhe.ghs.gshschedule.schedule_import.bean.Course
import org.jsoup.Jsoup

/**
 * 成都银杏酒店管理学院（新正方）专用解析器。
 *
 * 与通用的 [NewZFParser] 分开，避免影响其他学校的新正方导入：
 * - 兼容调课记录的 "(1-2节)第3周" 写法，不再把 "第N周" 解析失败后回退成 1-20 周；
 * - 周次完全无法识别时跳过该条，不再产生"幽灵课"；
 * - 去掉【调】前缀并对完全重复的记录去重；
 * - 每个单元格单独取教师/地点，一个单元格内的多个时间段全部保留。
 */
class GinkgoParser(source: String) : Parser(source) {

    /** 解析出的一段上课安排：节次区间 + 所覆盖的所有周次 */
    private data class TimeEntry(val startNode: Int, val step: Int, val weeks: List<WeekRange>)

    /** 周次区间；type：0 每周，1 单周，2 双周 */
    private data class WeekRange(val start: Int, val end: Int, val type: Int)

    override fun generateCourseList(): List<Course> {
        val courseList = arrayListOf<Course>()
        val doc = Jsoup.parse(source)
        val table1 = doc.getElementById("table1") ?: return courseList
        val trs = table1.getElementsByTag("tr")
        var rowNode = 0
        for (tr in trs) {
            val nodeStr = tr.getElementsByClass("festival").text().trim()
            if (nodeStr.isEmpty()) {
                continue
            }
            // 跳过非数字的 festival（如"早晨"/"上午"/"下午"/"晚上"）
            rowNode = nodeStr.toIntOrNull() ?: continue

            for (td in tr.getElementsByTag("td")) {
                // td 的 id 形如 "3" 或 "3-1"，首字符是星期几
                val day = td.attr("id").firstOrNull()?.toString()?.toIntOrNull() ?: continue
                for (div in td.getElementsByTag("div")) {
                    // 只处理直接包住 title 的那个 div，避免外层 div 重复解析出同一门课
                    val titleEl = div.getElementsByClass("title").firstOrNull() ?: continue
                    val container = titleEl.parents().firstOrNull { it.tagName() == "div" } ?: div
                    if (container !== div) {
                        continue
                    }
                    if (div.text().trim().length <= 1) {
                        continue
                    }
                    val courseName = cleanCourseName(titleEl.text())
                    if (courseName.isEmpty()) {
                        continue
                    }

                    var teacher = ""
                    var room = ""
                    val timeEntries = arrayListOf<TimeEntry>()

                    // 教师/地点可能出现在节次之后，先全部收集再生成课程
                    for (p in div.getElementsByTag("p")) {
                        val titleVal = p.getElementsByAttribute("title").attr("title").trim()
                        when (titleVal) {
                            "教师" -> teacher = p.text().trim()
                            "上课地点" -> room = p.text().trim()
                            "节/周", "周/节" -> {
                                parseTimeEntry(p.text().trim(), rowNode)?.let { timeEntries.add(it) }
                            }
                        }
                    }

                    for (entry in timeEntries) {
                        for (week in entry.weeks) {
                            courseList.add(
                                Course(
                                    name = courseName, room = room,
                                    teacher = teacher, day = day,
                                    startNode = entry.startNode,
                                    endNode = entry.startNode + entry.step - 1,
                                    startWeek = week.start, endWeek = week.end,
                                    type = week.type
                                )
                            )
                            Log.d(TAG, "添加课程: $courseName, day=$day, " +
                                    "node=${entry.startNode}-${entry.startNode + entry.step - 1}, " +
                                    "week=${week.start}-${week.end}, type=${week.type}")
                        }
                    }
                }
            }
        }
        // 教务页面会把调课前后、同一门课的相同安排重复渲染，按内容去重
        val result = courseList.distinct()
        Log.d(TAG, "解析完成，共 ${result.size} 条课程安排（去重前 ${courseList.size} 条）")
        return result
    }

    /**
     * 解析 "节/周" 单元格，如 "(1-2节)第3周"、"3-4节 2-4周,7-17周"。
     * 周次完全无法识别时返回 null（跳过该条），不再回退成 1-20 周。
     */
    private fun parseTimeEntry(raw: String, fallbackNode: Int): TimeEntry? {
        val timeStr = normalize(raw)
        var startNode = fallbackNode
        var step = 1

        val nodeMatch = Common.nodePattern.find(timeStr) ?: Common.nodePattern1.find(timeStr)
        var weekText = timeStr
        if (nodeMatch != null) {
            // 形如 "(1-2节" 或 "1-2节"，去掉括号和"节"后按分隔符取起止节次
            val nodes = nodeMatch.value
                .removePrefix("(")
                .removeSuffix("节")
                .split(Regex("[-~]"))
            nodes.getOrNull(0)?.toIntOrNull()?.let { startNode = it }
            nodes.getOrNull(1)?.toIntOrNull()?.let { step = it - startNode + 1 }
            if (step < 1) step = 1
            weekText = timeStr.replace(nodeMatch.value, "")
        }

        val weeks = parseWeeks(weekText)
        if (weeks.isEmpty()) {
            Log.w(TAG, "无法解析周次，跳过该时间段: $raw")
            return null
        }
        return TimeEntry(startNode, step, weeks)
    }

    /**
     * 从任意文本里找出所有周次说明，支持：
     * "第3周"、"3周"、"第1-3周"、"1-16周(单)"、"1-16单周"、"2-4周,7-17周"、"1,3,5周"。
     */
    private fun parseWeeks(text: String): List<WeekRange> {
        val matches = weekSpecPattern.findAll(text).toList()
        if (matches.isEmpty()) {
            return emptyList()
        }
        val result = arrayListOf<WeekRange>()
        matches.forEachIndexed { index, match ->
            val spec = match.groups[1]?.value ?: return@forEachIndexed
            // 单/双标记可能写在"周"之后，如 "1-16周(单)"；只看下一个周次说明之前的内容
            val tailStart = match.range.last + 1
            val nextStart = matches.getOrNull(index + 1)?.range?.first ?: text.length
            val tail = text.substring(tailStart, minOf(nextStart, tailStart + 6))
            val type = when {
                match.value.contains('单') || tail.contains('单') -> 1
                match.value.contains('双') || tail.contains('双') -> 2
                else -> 0
            }
            for (item in spec.split(',')) {
                val bounds = item.split('-')
                val start = bounds.getOrNull(0)?.toIntOrNull() ?: continue
                val end = bounds.getOrNull(1)?.toIntOrNull() ?: start
                if (start in 1..30 && end in start..30) {
                    result.add(WeekRange(start, end, type))
                }
            }
        }
        return result
    }

    /** 全角转半角、去空白，方便后续正则统一处理 */
    private fun normalize(input: String): String {
        val builder = StringBuilder(input.length)
        for (c in input) {
            builder.append(
                when (c) {
                    in '０'..'９' -> '0' + (c - '０')
                    '－', '—', '–', '~', '～', '至' -> '-'
                    '，' -> ','
                    else -> c
                }
            )
        }
        return builder.toString().replace(Regex("""\s+"""), "")
    }

    /** 去掉调课记录名字前的标记，如 "【调】xxx"，让同一门课合并显示 */
    private fun cleanCourseName(raw: String): String {
        return raw.trim()
            .replace(Regex("""^[\[【（(][^\]】）)]*调[^\]】）)]*[\]】）)]\s*"""), "")
            .trim()
    }

    companion object {
        private const val TAG = "GinkgoParser"

        /**
         * 抓取前面带"周"的周次说明：一个或多个数字/区间，用逗号连接，后面可跟单/双和"周"。
         * 例："第3周" → 3；"1-16周" → 1-16；"2-4,7-17周" → 2-4、7-17。
         */
        private val weekSpecPattern =
            Regex("""(\d{1,2}(?:-\d{1,2})?(?:,\d{1,2}(?:-\d{1,2})?)*)[单双]?周""")
    }
}
