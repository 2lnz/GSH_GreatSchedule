package com.Azhe.ghs.gshschedule.schedule_import.parser

import com.Azhe.ghs.gshschedule.schedule_import.bean.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用银杏学院（新正方）导出页面的结构模拟调课格式，覆盖：
 * - "第N周" / "第X-Y周" 周次解析
 * - 解析失败时不回退成 1-20 周
 * - 单/双周标记
 * - 【调】重复记录去重、嵌套 div 不重复解析、一个单元格多个时间段
 */
class GinkgoParserTest {

    private fun cell(day: Int, name: String, week: String, node: String = "1-2节"): String =
        """<td id="$day"><div class="timetable_con">
             <p class="title">$name</p>
             <p title="教师">张三</p>
             <p title="上课地点">1-101</p>
             <p title="节/周">($node)$week</p>
           </div></td>"""

    private fun parse(vararg tds: String): List<Course> {
        val html = """<html><body><table id="table1">
            <tr><td class="festival">1</td>${tds.joinToString("")}</tr>
        </table></body></html>"""
        return GinkgoParser(html).generateCourseList()
    }

    @Test
    fun `第N周 单周条目解析为单周`() {
        val list = parse(cell(7, "商务交际与职业形象", "第3周"))
        assertEquals(1, list.size)
        assertEquals(7, list[0].day)
        assertEquals(3, list[0].startWeek)
        assertEquals(3, list[0].endWeek)
        assertEquals(0, list[0].type)
    }

    @Test
    fun `第X-Y周 区间条目解析正确`() {
        val list = parse(cell(1, "高等数学", "第1-3周"))
        assertEquals(1, list.size)
        assertEquals(1, list[0].startWeek)
        assertEquals(3, list[0].endWeek)
    }

    @Test
    fun `多段周次逐条解析`() {
        val list = parse(cell(1, "商务交际与职业形象", "2-4周,7-17周"))
        assertEquals(2, list.size)
        assertTrue(list.any { it.startWeek == 2 && it.endWeek == 4 })
        assertTrue(list.any { it.startWeek == 7 && it.endWeek == 17 })
    }

    @Test
    fun `无法解析的周次被跳过而不是回退为1到20周`() {
        val list = parse(cell(1, "神秘课程", "整周"))
        assertTrue(list.isEmpty())
    }

    @Test
    fun `单双周标记保留`() {
        val list = parse(
            cell(1, "单周课", "1-16周(单)"),
            cell(2, "双周课", "1-16周(双)"),
            cell(3, "单周课2", "1-16单周")
        )
        assertEquals(3, list.size)
        assertEquals(1, list.first { it.name == "单周课" }.type)
        assertEquals(2, list.first { it.name == "双周课" }.type)
        assertEquals(1, list.first { it.name == "单周课2" }.type)
    }

    @Test
    fun `逗号分隔的不连续周次`() {
        val list = parse(cell(4, "零散课", "1,3,5周"))
        assertEquals(3, list.size)
        assertEquals(listOf(1, 3, 5), list.map { it.startWeek }.sorted())
    }

    @Test
    fun `调课记录去标记且完全重复的记录被去掉`() {
        val list = parse(
            cell(1, "体育", "1-16周"),
            cell(1, "【调】体育", "1-16周")
        )
        assertEquals(1, list.size)
        assertEquals("体育", list[0].name)
    }

    @Test
    fun `调课后换时间的记录保留`() {
        val list = parse(
            cell(1, "商务交际与职业形象", "2-4周,7-17周"),
            cell(7, "【调】商务交际与职业形象", "第3周")
        )
        assertEquals(3, list.size)
        val sunday = list.first { it.day == 7 }
        assertEquals("商务交际与职业形象", sunday.name)
        assertEquals(3, sunday.startWeek)
        assertEquals(3, sunday.endWeek)
    }

    @Test
    fun `嵌套div只解析一次`() {
        val html = """<html><body><table id="table1">
            <tr><td class="festival">1</td>
              <td id="5"><div class="wrap"><div class="timetable_con">
                <p class="title">数据结构</p>
                <p title="教师">李四</p>
                <p title="上课地点">2-202</p>
                <p title="节/周">(3-4节)1-16周</p>
              </div></div></td>
            </tr>
        </table></body></html>"""
        val list = GinkgoParser(html).generateCourseList()
        assertEquals(1, list.size)
        assertEquals(3, list[0].startNode)
        assertEquals(4, list[0].endNode)
    }

    @Test
    fun `一个单元格内多个上课时间都被保留`() {
        val html = """<html><body><table id="table1">
            <tr><td class="festival">3</td>
              <td id="2"><div class="timetable_con">
                <p class="title">大学英语</p>
                <p title="教师">王五</p>
                <p title="上课地点">3-303</p>
                <p title="节/周">(1-2节)第3周</p>
                <p title="节/周">(5-6节)1-16周</p>
              </div></td>
            </tr>
        </table></body></html>"""
        val list = GinkgoParser(html).generateCourseList()
        assertEquals(2, list.size)
        val first = list.first { it.startNode == 1 }
        assertEquals(3, first.startWeek)
        assertEquals(3, first.endWeek)
        val second = list.first { it.startNode == 5 }
        assertEquals(1, second.startWeek)
        assertEquals(16, second.endWeek)
    }

    @Test
    fun `教师与地点在节次之后出现也能取到`() {
        val html = """<html><body><table id="table1">
            <tr><td class="festival">1</td>
              <td id="4"><div class="timetable_con">
                <p class="title">线性代数</p>
                <p title="节/周">(1-2节)1-16周</p>
                <p title="教师">赵六</p>
                <p title="上课地点">4-404</p>
              </div></td>
            </tr>
        </table></body></html>"""
        val list = GinkgoParser(html).generateCourseList()
        assertEquals(1, list.size)
        assertEquals("赵六", list[0].teacher)
        assertEquals("4-404", list[0].room)
    }

    @Test
    fun `全角字符也能解析`() {
        val list = parse(cell(1, "全角课", "第１－３周"))
        assertEquals(1, list.size)
        assertEquals(1, list[0].startWeek)
        assertEquals(3, list[0].endWeek)
    }

    @Test
    fun `没有任何一条课程落在1到20周默认值上`() {
        val list = parse(
            cell(1, "课程A", "第3周"),
            cell(2, "课程B", "整周"),
            cell(3, "课程C", "第5-6周")
        )
        assertFalse(list.any { it.startWeek == 1 && it.endWeek == 20 })
    }
}
