package com.Azhe.ghs.gshschedule.utils

import android.content.Context
import androidx.core.content.edit
import com.Azhe.ghs.gshschedule.bean.AnnouncementBean
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 公告管理器 — 独立工具类，负责：
 *  1. 从远程 JSON（GitHub Pages）拉取公告列表
 *  2. 按时间范围过滤有效公告
 *  3. 通过 SharedPreferences 管理已读状态
 *  4. 提供「弹窗用未读列表」和「历史列表」
 *
 * 使用方式：
 *   val manager = AnnouncementManager
 *   val unread = manager.fetchUnreadAnnouncements(context)  // 弹窗用
 *   val all = manager.fetchAllAnnouncements(context)        // 历史页用
 *   manager.markAsRead(context, id)                         // 标记已读
 *   manager.hasUnread(context)                              // 红点状态
 */
object AnnouncementManager {

    /**
     * 公告 JSON 地址，按顺序依次尝试，前一个失败（超时/劫持/格式错误）才用下一个：
     *  1. 自定义域名 HTTPS — 直连，无 301 跳转，避免明文 HTTP 被劫持
     *  2. GitHub Pages — 会 301 到自定义域名的 HTTP 地址，仅作备用
     *  3. GitHub Raw — 国内常被屏蔽，最后兜底
     */
    private val JSON_URLS = listOf(
        "https://blog.2lnz.top/GSH_GreatSchedule/announcement/announcement.json",
        "https://2lnz.github.io/GSH_GreatSchedule/announcement/announcement.json",
        "https://raw.githubusercontent.com/2lnz/GSH_GreatSchedule/main/docs/announcement/announcement.json"
    )

    /** SharedPreferences 中存储已读 ID 集合的 key */
    private const val PREF_READ_IDS = "announcement_read_ids"

    /** SharedPreferences 文件名（独立文件，不污染主配置） */
    private const val PREF_NAME = "announcement_prefs"

    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 内存缓存 — 避免短时间内重复请求 */
    @Volatile
    private var cachedAnnouncements: List<AnnouncementBean>? = null

    @Volatile
    private var cacheTimestamp: Long = 0L

    /** 缓存有效期（毫秒），默认 5 分钟 */
    private val cacheTtlMs = 300_000L

    // ── 公开 API ────────────────────────────────────────────────

    /**
     * 预加载公告（从 SplashActivity 调用，提前触发网络请求）。
     * 不阻塞启动流程，失败静默忽略。
     */
    suspend fun preload(context: Context) {
        try {
            fetchValidAnnouncements(context)
        } catch (_: Exception) {}
    }

    /**
     * 拉取并过滤未读公告（供开屏弹窗使用）。
     * 优先返回缓存，缓存未命中时发起网络请求。
     * 网络失败时静默返回空列表，不影响 App 正常启动。
     */
    suspend fun fetchUnreadAnnouncements(context: Context): List<AnnouncementBean> {
        val all = fetchValidAnnouncements(context)
        val readIds = getReadIds(context)

        return all.filter { bean ->
            when (bean.type) {
                "every" -> true                       // 每次打开都弹
                else    -> bean.id !in readIds        // "once" 且未读过
            }
        }
    }

    /**
     * 获取所有有效期内公告（供历史列表页使用）。
     */
    suspend fun fetchAllAnnouncements(context: Context): List<AnnouncementBean> {
        return fetchValidAnnouncements(context)
    }

    /**
     * 标记某条公告为已读。
     */
    fun markAsRead(context: Context, id: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val readIds = getReadIds(context).toMutableSet()
        readIds.add(id)
        prefs.edit { putStringSet(PREF_READ_IDS, readIds.map { it.toString() }.toSet()) }
    }

    /**
     * 是否有未读公告（供侧边栏红点判断）。
     * 注意：此方法会触发网络请求，建议在协程中调用。
     */
    suspend fun hasUnread(context: Context): Boolean {
        return fetchUnreadAnnouncements(context).isNotEmpty()
    }

    // ── 内部实现 ────────────────────────────────────────────────

    /** 从网络拉取 JSON 并过滤有效期，优先使用缓存；地址按 JSON_URLS 顺序逐级回退 */
    private suspend fun fetchValidAnnouncements(context: Context): List<AnnouncementBean> {
        // 缓存命中直接返回
        val cached = cachedAnnouncements
        if (cached != null && System.currentTimeMillis() - cacheTimestamp < cacheTtlMs) {
            return cached
        }

        return withContext(Dispatchers.IO) {
            for (url in JSON_URLS) {
                try {
                    return@withContext fetchAndCache(url)
                } catch (e: Exception) {
                    // 该地址失败（超时、运营商封堵、返回劫持页面等）— 继续尝试下一个
                    e.printStackTrace()
                }
            }
            // 所有地址都失败 — 有旧缓存（过期但可用）则返回旧数据作为降级
            cachedAnnouncements ?: emptyList()
        }
    }

    /** 拉取 JSON、解析并按有效期过滤，成功后更新内存缓存 */
    private fun fetchAndCache(url: String): List<AnnouncementBean> {
        val json = fetchJson(url)
        // 用 getParameterized 显式构造泛型，不依赖匿名 TypeToken 子类的 Signature 属性
        // （R8 会丢失该属性，导致 release 包抛 IllegalStateException）
        val type = TypeToken.getParameterized(List::class.java, AnnouncementBean::class.java).type
        val list: List<AnnouncementBean> = gson.fromJson(json, type)
        val now = Date()
        val valid = list.filter { bean ->
            val start = tryParseDate(bean.startTime)
            val end   = tryParseDate(bean.endTime)
            (start == null || !now.before(start)) &&
            (end   == null || !now.after(end))
        }
        // 更新内存缓存
        cachedAnnouncements = valid
        cacheTimestamp = System.currentTimeMillis()
        return valid
    }

    /** OkHttp GET 请求，返回响应体字符串 */
    private fun fetchJson(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Cache-Control", "max-age=300")   // 5 分钟缓存
            .build()
        val response = client.newCall(request).execute()
        return response.body()?.string() ?: "[]"
    }

    /** 安全解析日期，格式错误返回 null */
    private fun tryParseDate(dateStr: String): Date? {
        if (dateStr.isBlank()) return null
        return try {
            dateFormat.parse(dateStr)
        } catch (_: Exception) {
            null
        }
    }

    /** 读取已读 ID 集合 */
    private fun getReadIds(context: Context): Set<Int> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val strSet = prefs.getStringSet(PREF_READ_IDS, emptySet()) ?: emptySet()
        return strSet.mapNotNull { it.toIntOrNull() }.toSet()
    }
}
