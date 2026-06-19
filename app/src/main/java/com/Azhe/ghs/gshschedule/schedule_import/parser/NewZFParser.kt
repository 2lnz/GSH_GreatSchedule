package com.Azhe.ghs.gshschedule.schedule_import.parser

import com.Azhe.ghs.gshschedule.schedule_import.Common
import com.Azhe.ghs.gshschedule.schedule_import.bean.Course
import org.jsoup.Jsoup

class NewZFParser(source: String) : Parser(source) {

    override fun generateCourseList(): List<Course> {
        val courseList = arrayListOf<Course>()
        val doc = Jsoup.parse(source)
        val table1 = doc.getElementById("table1")
        val trs = table1.getElementsByTag("tr")
        var rowNode = 0
        var day = 0
        var teacher = ""
        var room = ""
        var timeStr = ""
        for (tr in trs) {
            val nodeStr = tr.getElementsByClass("festival").text()
            if (nodeStr.isEmpty()) {
                continue
            }
            try {
                rowNode = nodeStr.toInt()
            } catch (e: NumberFormatException) {
                // 跳过非数字的 festival（如"早晨"/"上午"/"下午"/"晚上"）
                continue
            }

            val tds = tr.getElementsByTag("td")
            for (td in tds) {
                val divs = td.getElementsByTag("div")
                for (div in divs) {
                    val courseValue = div.text().trim()

                    if (courseValue.length <= 1) {
                        continue
                    }

                    val courseName = div.getElementsByClass("title").text()
                    if (courseName.isEmpty()) {
                        continue
                    }

                    day = td.attr("id")[0].toString().toInt()

                    // 每个课程使用独立的 node/step，从 rowNode 初始化
                    var courseNode = rowNode
                    var courseStep = 1

                    val pList = div.getElementsByTag("p")
                    val weekList = arrayListOf<String>()
                    pList.forEach { e ->
                        val titleEls = e.getElementsByAttribute("title")
                        val titleVal = titleEls.attr("title").trim()
                        when (titleVal) {
                            "教师" -> teacher = e.text().trim()
                            "上课地点" -> room = e.text().trim()
                            "节/周", "周/节" -> {
                                timeStr = e.text().trim()
                                android.util.Log.d("NewZFParser", "原始时间字符串: $timeStr")
                                // 优先使用带括号的格式 (X-Y节) 或 (X节)
                                var result = Common.nodePattern.find(timeStr)
                                // 回退到不带括号的格式 X-Y节 或 X节
                                if (result == null) {
                                    result = Common.nodePattern1.find(timeStr)
                                }
                                if (result != null) {
                                    val nodeInfo = result.value
                                    android.util.Log.d("NewZFParser", "匹配到节点: $nodeInfo")
                                    // 根据匹配模式去除前缀/后缀
                                    val cleanNode = if (nodeInfo.startsWith("(")) {
                                        // (X-Y节 → 去掉首尾
                                        nodeInfo.substring(1, nodeInfo.length - 1)
                                    } else {
                                        // X-Y节 → 只去掉尾部的"节"
                                        nodeInfo.substring(0, nodeInfo.length - 1)
                                    }
                                    val nodes = cleanNode.split(Regex("[-~]"))
                                        .dropLastWhile { it.isEmpty() }

                                    if (nodes.isNotEmpty()) {
                                        try {
                                            courseNode = nodes[0].toInt()
                                        } catch (_: NumberFormatException) {}
                                    }
                                    if (nodes.size > 1) {
                                        try {
                                            val endNode = nodes[1].toInt()
                                            courseStep = endNode - courseNode + 1
                                        } catch (_: NumberFormatException) {}
                                    }
                                    // 从时间字符串中移除已匹配的节点部分，保留周次信息
                                    timeStr = timeStr.replace(result.value, "").trim()
                                    // 移除节点后可能残留的前导字符（逗号、顿号、反括号等）
                                    timeStr = timeStr.trimStart(',', '，', ' ', ')')
                                }
                                android.util.Log.d("NewZFParser", "清理后周次字符串: $timeStr, courseNode=$courseNode, courseStep=$courseStep")
                                weekList.clear()
                                weekList.addAll(timeStr.split(Regex("[,，]")).filter { it.isNotBlank() })
                            }
                        }
                    }

                    weekList.forEach {
                        // 跳过不包含"周"的条目（可能是未清理干净的节点信息）
                        if (!it.contains('周')) {
                            android.util.Log.d("NewZFParser", "跳过非周次条目: $it")
                            return@forEach
                        }
                        var weekStart = 1
                        var weekEnd = 20
                        var weekType = 0

                        if (it.contains('-')) {
                            val weeks = it.substring(0, it.indexOf('周')).split('-')
                            if (weeks.isNotEmpty()) {
                                try { weekStart = weeks[0].toInt() } catch (_: Exception) {}
                            }
                            if (weeks.size > 1) {
                                try { weekEnd = weeks[1].toInt() } catch (_: Exception) {}
                            }

                            weekType = when {
                                it.contains('单') -> 1
                                it.contains('双') -> 2
                                else -> 0
                            }
                        } else {
                            try {
                                weekStart = it.substring(0, it.indexOf('周')).toInt()
                                weekEnd = it.substring(0, it.indexOf('周')).toInt()
                            } catch (e: Exception) {
                                weekStart = 1
                                weekEnd = 20
                            }
                        }

                        android.util.Log.d("NewZFParser", "添加课程: $courseName, day=$day, node=$courseNode-${courseNode + courseStep - 1}, week=$weekStart-$weekEnd")

                        courseList.add(
                                Course(
                                        name = courseName, room = room,
                                        teacher = teacher, day = day,
                                        startNode = courseNode, endNode = courseNode + courseStep - 1,
                                        startWeek = weekStart, endWeek = weekEnd,
                                        type = weekType
                                )
                        )
                    }
                }
            }
        }
        android.util.Log.d("NewZFParser", "解析完成，共 ${courseList.size} 门课程")
        return courseList
    }

}