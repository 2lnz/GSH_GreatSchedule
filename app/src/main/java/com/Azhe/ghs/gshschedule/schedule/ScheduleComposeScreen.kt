package com.Azhe.ghs.gshschedule.schedule

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import com.bumptech.glide.Glide
import com.Azhe.ghs.gshschedule.R
import com.Azhe.ghs.gshschedule.bean.CourseBean
import com.Azhe.ghs.gshschedule.bean.TableSelectBean
import com.Azhe.ghs.gshschedule.utils.CourseUtils
import com.Azhe.ghs.gshschedule.utils.ViewUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ── Data class for course card state ──

@androidx.compose.runtime.Stable
data class CourseCardState(
    val course: CourseBean,
    val timeText: String,
    val bgColor: Color,
    val isOtherWeek: Boolean,
    val isMultiCourse: Boolean,
    val isError: Boolean,
    val startNode: Int,
    val step: Int
)

// ── Navigation drawer item model ──

data class DrawerItem(val id: Int, val title: String, val icon: Int)

// ── Main screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onDrawerItemClick: (Int) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onAddCourseClick: () -> Unit,
    onModifyWeek: () -> Unit,
    onCreateSchedule: () -> Unit,
    onManageSchedule: () -> Unit,
    onTableSwitch: (Int) -> Unit,
    onTimeSettings: () -> Unit,
    onChangeBackground: () -> Unit,
    onCheckCourses: () -> Unit,
    onFAQ: () -> Unit,
    hasUnreadAnnouncement: Boolean = false,
    onAnnouncementClick: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetScaffoldState()
    // Track sheet expand intent ourselves so rapid double-taps don't race
    // against animation (currentValue lags behind user intent).
    var bottomSheetExpanded by remember { mutableStateOf(false) }

    // Debounce guard — ignore taps within 400ms to prevent concurrent
    // animations on the same SheetState from crashing the app.
    var lastToggleTime by remember { mutableStateOf(0L) }

    // Let LaunchedEffect drive the animation — it automatically cancels the
    // previous coroutine when bottomSheetExpanded toggles, preventing two
    // concurrent animateTo() calls on the same SheetState.
    // Skip the initial composition: the sheet starts hidden and calling
    // hide() before BottomSheetScaffold is fully mounted will crash.
    var animationReady by remember { mutableStateOf(false) }
    LaunchedEffect(bottomSheetExpanded) {
        if (!animationReady) {
            animationReady = true
            return@LaunchedEffect
        }
        try {
            if (bottomSheetExpanded) {
                sheetState.bottomSheetState.expand()
            } else {
                sheetState.bottomSheetState.hide()
            }
        } catch (_: Exception) {
            // Swallow all exceptions from animation (CancellationException
            // from coroutine cancellation, IllegalStateException from
            // Material3 state-machine conflicts, etc.). This is a leaf
            // coroutine so structured-concurrency cancellation is unaffected.
        }
    }

    // Intercept system back when bottom sheet is open — close the sheet
    // instead of letting the Activity consume the event and finish.
    BackHandler(enabled = bottomSheetExpanded) {
        bottomSheetExpanded = false
    }

    val drawerItems = listOf(
        DrawerItem(R.id.nav_course, "已添课程", R.drawable.wakeup),
        DrawerItem(R.id.nav_feedback, "吐个槽", R.drawable.feedback),
        DrawerItem(R.id.nav_setting, "设置", R.drawable.setting),
        DrawerItem(R.id.nav_about, "关于", R.drawable.about)
    )

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                items = drawerItems,
                onItemClick = { id ->
                    scope.launch { drawerState.close() }
                    onDrawerItemClick(id)
                },
                hasUnread = hasUnreadAnnouncement,
                onAnnouncementClick = {
                    scope.launch { drawerState.close() }
                    onAnnouncementClick()
                }
            )
        }
    ) {
        BottomSheetScaffold(
            scaffoldState = sheetState,
            sheetContent = {
                BottomSheetContent(
                    viewModel = viewModel,
                    onWeekSelected = { week ->
                        viewModel.selectedWeek = week
                    },
                    onModifyWeek = onModifyWeek,
                    onCreateSchedule = onCreateSchedule,
                    onManageSchedule = onManageSchedule,
                    onTableClick = { tableId ->
                        bottomSheetExpanded = false
                        onTableSwitch(tableId)
                    },
                    onTimeSettings = onTimeSettings,
                    onChangeBackground = onChangeBackground,
                    onCheckCourses = onCheckCourses,
                    onFAQ = onFAQ
                )
            },
            sheetPeekHeight = 0.dp,
            sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            sheetContainerColor = MaterialTheme.colorScheme.surface
        ) {
            ScheduleContent(
                viewModel = viewModel,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onShowMore = {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastToggleTime >= 400L) {
                        lastToggleTime = now
                        bottomSheetExpanded = !bottomSheetExpanded
                    }
                },
                onExportClick = onExportClick,
                onImportClick = onImportClick,
                onAddCourseClick = onAddCourseClick,
                onWeekDayClick = {
                    viewModel.selectedWeek = viewModel.currentWeek
                }
            )
        }
    }
}

// ── Drawer content ──

@Composable
private fun DrawerContent(
    items: List<DrawerItem>,
    onItemClick: (Int) -> Unit,
    hasUnread: Boolean = false,
    onAnnouncementClick: () -> Unit = {}
) {
    ModalDrawerSheet(
        modifier = Modifier.width(280.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.main_background_2020_1),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )
            Text(
                text = "GSH课程表",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── 通知铃铛入口 ──
        NavigationDrawerItem(
            label = { Text("公告通知") },
            selected = false,
            onClick = onAnnouncementClick,
            modifier = Modifier.padding(horizontal = 12.dp),
            icon = {
                // 铃铛图标 + 角标红点
                Box {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "公告通知",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    if (hasUnread) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .align(Alignment.TopEnd)
                                .background(Color.Red, shape = RoundedCornerShape(50))
                        )
                    }
                }
            },
            badge = if (hasUnread) {
                { Badge { Text("") } }
            } else null
        )

        items.forEach { item ->
            NavigationDrawerItem(
                label = { Text(item.title) },
                selected = false,
                onClick = { onItemClick(item.id) },
                modifier = Modifier.padding(horizontal = 12.dp),
                icon = {
                    Icon(
                        painter = painterResource(item.icon),
                        contentDescription = item.title,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
    }
}

// ── Main content ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScheduleContent(
    viewModel: ScheduleViewModel,
    onOpenDrawer: () -> Unit,
    onShowMore: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onAddCourseClick: () -> Unit,
    onWeekDayClick: () -> Unit
) {
    val density = LocalDensity.current

    // Read dataVersion to trigger recomposition when table data changes
    val dataVersion = viewModel.dataVersion

    // 课程数据只在这里观察一次，所有周页面共享。
    // 若放在各页面里观察，页面首次组合会先拿到空列表、收到数据后再重组一次，
    // 这两次组合恰好发生在左右滑动的动画过程中，是卡顿的主要来源之一。
    val courseData = List(7) { dayIndex ->
        viewModel.allCourseList[dayIndex].observeAsState(emptyList())
    }

    val isDark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val textColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color(viewModel.table.textColor)
    val maxWeek = viewModel.table.maxWeek

    val pagerState = rememberPagerState(
        initialPage = (viewModel.selectedWeek - 1).coerceIn(0, (maxWeek - 1).coerceAtLeast(0)),
        pageCount = { maxWeek }
    )

    // Sync pager with selectedWeek (instant — e.g. bottom sheet week pick)
    LaunchedEffect(viewModel.selectedWeek) {
        val targetPage = (viewModel.selectedWeek - 1).coerceIn(0, (maxWeek - 1).coerceAtLeast(0))
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    // Sync selectedWeek with pager — only after the swipe settles.
    // settledPage 在滚动完全停止后才变化，避免慢拖途中就写入 selectedWeek、
    // 触发整页重组和后台预计算，与滚动动画抢主线程造成掉帧。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collectLatest { page ->
                viewModel.selectedWeek = page + 1
            }
    }

    // When course data changes (import / add / edit / delete), invalidate the
    // entire card-state cache so stale entries are never served to ScheduleGrid.
    // NOTE: do NOT call precomputeCardStates here — it runs on Dispatchers.Default
    // and may read stale allCourseList before Room delivers fresh data to LiveData,
    // which would re-populate the cache with old data and defeat the invalidation.
    LaunchedEffect(dataVersion) {
        viewModel.invalidateCardStateCache()
    }

    // When the user settles on a different week (pager swipe), pre-compute
    // neighbour weeks on a background thread WITHOUT clearing the existing cache.
    LaunchedEffect(viewModel.selectedWeek) {
        viewModel.precomputeCardStates(viewModel.selectedWeek)
    }

    // Invalidate cache when table-level configuration changes (start date, showOtherWeek, etc.)
    LaunchedEffect(viewModel.table.id, viewModel.table.showTime, viewModel.table.maxWeek) {
        viewModel.invalidateCardStateCache()
    }

    // Compute week and day text (replaces the old view references)
    val weekText = remember(viewModel.selectedWeek) {
        "第${viewModel.selectedWeek}周"
    }
    val dayText = remember(viewModel.selectedWeek, viewModel.currentWeek) {
        if (viewModel.currentWeek > 0) {
            if (viewModel.selectedWeek == viewModel.currentWeek) {
                CourseUtils.getWeekday()
            } else {
                "非本周"
            }
        } else {
            "还没有开学哦"
        }
    }

    val backgroundUri = viewModel.table.background
    Box(modifier = Modifier.fillMaxSize()) {
        // 自定义背景图
        if (backgroundUri.isNotEmpty()) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    Glide.with(imageView.context)
                        .load(Uri.parse(backgroundUri))
                        .into(imageView)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (backgroundUri.isEmpty()) Modifier.background(MaterialTheme.colorScheme.surface)
                    else Modifier
                )
        ) {
        // ── Top section: date, week, actions ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = with(density) { ViewUtils.getStatusBarHeight(androidx.compose.ui.platform.LocalContext.current).toDp() })
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenDrawer, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "菜单",
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onAddCourseClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加课程",
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onImportClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.GetApp,
                        contentDescription = "导入",
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onExportClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "导出",
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onShowMore, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Date display
            Text(
                text = CourseUtils.getTodayDate(),
                color = textColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Week + weekday
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = weekText,
                    color = textColor,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dayText,
                    color = textColor,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onWeekDayClick() }
                )
            }
        }

        // ── Schedule pager ──
        key(dataVersion) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val week = page + 1
                ScheduleGrid(
                    viewModel = viewModel,
                    week = week,
                    courseData = courseData,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        } // Column (content)
    } // Box (background + content)
}

// ── Schedule grid ──

@Composable
fun ScheduleGrid(
    viewModel: ScheduleViewModel,
    week: Int,
    courseData: List<State<List<CourseBean>>>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val table = viewModel.table
    // 显式读取 timeList Compose 状态，确保修改后触发重组
    val timeList = viewModel.timeList

    // 文本样式与行高每页只算一次，所有卡片共用：
    // 避免每张卡片各自创建 TextMeasurer / TextStyle 并重复测量行高。
    val itemTextSize = table.itemTextSize
    val textStyle = remember(itemTextSize, density.fontScale) {
        TextStyle(
            fontSize = itemTextSize.sp,
            lineHeight = (itemTextSize + 1).sp,
            fontWeight = FontWeight.Bold
        )
    }
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    val lineHeightPx = remember(textStyle) {
        textMeasurer.measure(text = "国", style = textStyle, maxLines = 1).size.height.toFloat()
    }

    val col = 6 + (if (table.showSat) 1 else 0) + (if (table.showSun) 1 else 0)
    // 星期列卡片内容宽度：不在这里使用 BoxWithConstraints（SubcomposeLayout），
    // 否则整个网格会在测量阶段才组合，换来约 200ms 的布局卡顿。
    // 主页面内容区宽度即窗口宽度，由配置里的 screenWidthDp 推算即可。
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val cardContentWidthPx = remember(col, screenWidthDp, density) {
        val columnWidthPx = screenWidthDp * density.density / (col - 0.5f)
        (columnWidthPx - with(density) { 10.dp.toPx() }).toInt().coerceAtLeast(0)
    }
    val dayMap = remember(table.showSat, table.showSun, table.sundayFirst) {
        computeDayMap(table.showSat, table.showSun, table.sundayFirst)
    }
    // 实际显示顺序：Compose 的 Row 按子项组合顺序从左到右布局，
    // 「周日为每周第一天」时必须按 周日→周六 迭代，否则周日永远在最右列。
    // dayMap 的列号语义与此顺序一致（周日起始：周日=列1、周一=列2 …），
    // 因此每个星期标签的日期与课程仍按 colIdx 正确配对。
    val dayOrder = remember(table.sundayFirst) {
        if (table.sundayFirst) listOf(7, 1, 2, 3, 4, 5, 6) else listOf(1, 2, 3, 4, 5, 6, 7)
    }
    val itemHeightDp = with(density) { viewModel.itemHeight.toDp() }
    val marTopDp = with(density) { viewModel.marTop.toDp() }
    val isDark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val textColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color(table.textColor)
    val currentDay = CourseUtils.getWeekdayInt()

    // Date strings for this week
    val weekDate = remember(week, table.startDate, table.sundayFirst) {
        CourseUtils.getDateStringFromWeek(
            CourseUtils.countWeek(table.startDate, table.sundayFirst),
            week,
            table.sundayFirst
        )
    }

    Column(modifier = modifier) {
        // ── Day header row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            // Month column
            Text(
                text = weekDate[0] + "\n月",
                color = textColor,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(0.5f)
                    .padding(4.dp)
            )

            // Day columns
            for (day in dayOrder) {
                val colIdx = dayMap[day]
                if (colIdx == -1) continue

                val isToday = week == viewModel.currentWeek && day == currentDay
                val dateStr = weekDate[colIdx]

                val alphaColor = if (isDark) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                } else {
                    Color(
                        ColorUtils.setAlphaComponent(
                            table.textColor,
                            (0.32 * (table.textColor shr 24 and 0xff)).toInt()
                        )
                    )
                }

                Text(
                    text = "${viewModel.daysArray[day]}\n$dateStr",
                    color = if (isToday) textColor else alphaColor,
                    fontSize = 12.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                )
            }
        }

        // ── Grid content ──
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Time labels column — align with course card top
            Column(
                modifier = Modifier.weight(0.5f)
            ) {
                for (node in 1..table.nodes) {
                    val timeSize = when (col) {
                        7 -> 9.sp
                        6 -> 10.sp
                        else -> 8.sp
                    }
                    val start = if (timeList.isNotEmpty() && node - 1 < timeList.size) timeList[node - 1].startTime else ""
                    val end   = if (timeList.isNotEmpty() && node - 1 < timeList.size) timeList[node - 1].endTime   else ""

                    // 3 lines packed tight, placed at top of cell
                    androidx.compose.runtime.key(node) {
                        androidx.compose.ui.layout.Layout(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(itemHeightDp + marTopDp),
                            content = {
                                Text(text = node.toString(), color = textColor, fontSize = 12.sp,
                                    lineHeight = 12.sp, softWrap = false)
                                if (viewModel.showTimeDetail) {
                                    Text(text = start, color = textColor, fontSize = timeSize,
                                        lineHeight = timeSize, maxLines = 1, softWrap = false)
                                    Text(text = end, color = textColor, fontSize = timeSize,
                                        lineHeight = timeSize, maxLines = 1, softWrap = false)
                                }
                            }
                        ) { measurables, constraints ->
                            val w = constraints.maxWidth
                            val h = constraints.maxHeight
                            // measure each child with its own tight lineHeight
                            val p = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
                            layout(w, h) {
                                if (p.size == 1) {
                                    // 只显示节数时，居中摆放
                                    val node = p[0]
                                    node.placeRelative((w - node.width) / 2, (h - node.height) / 2)
                                } else {
                                    var y = 0
                                    for (i in p.indices) {
                                        if (y + p[i].height <= h) {
                                            p[i].placeRelative((w - p[i].width) / 2, y)
                                            y += p[i].height
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (table.nodes > 0) {
                    Spacer(modifier = Modifier.height((itemHeightDp + marTopDp) * 4))
                }
            }

            // Day columns
            for (day in dayOrder) {
                val colIdx = dayMap[day]
                if (colIdx == -1) continue

                val dayCourses = courseData[day - 1].value
                val alphaInt = viewModel.alphaInt
                val courses = dayCourses
                val cardStates = remember(dayCourses, week, table.id, table.showTime, table.showOtherWeekCourse, alphaInt, timeList) {
                    val cached = viewModel.getCachedCardStates(
                        week = week,
                        day = day,
                        courses = courses,
                        tableId = table.id,
                        showTime = table.showTime,
                        showOtherWeekCourse = table.showOtherWeekCourse,
                        alphaInt = alphaInt,
                        timeList = timeList
                    )
                    val states = cached ?: buildCourseCardStates(
                        courses = courses,
                        week = week,
                        day = day,
                        table = table,
                        alphaInt = alphaInt,
                        timeList = timeList,
                        context = context
                    ).also {
                        viewModel.cacheCardStates(
                            week = week,
                            day = day,
                            courses = courses,
                            tableId = table.id,
                            showTime = table.showTime,
                            showOtherWeekCourse = table.showOtherWeekCourse,
                            alphaInt = alphaInt,
                            timeList = timeList,
                            states = it
                        )
                    }
                    states
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                ) {
                    // 课程卡片按绝对位置叠放：同一时间段有多门课时不会互相挤动错位。
                    // 非本周课程先绘制、本周课程后绘制（覆盖在上方）。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((itemHeightDp + marTopDp) * table.nodes)
                    ) {
                        cardStates
                            .sortedBy { if (it.isOtherWeek) 0 else 1 }
                            .forEach { card ->
                                val offsetY = ((card.startNode - 1) * (viewModel.itemHeight + viewModel.marTop) + viewModel.marTop)
                                    .let { with(density) { it.toDp() } }
                                val cardHeight = (viewModel.itemHeight * card.step + viewModel.marTop * (card.step - 1))
                                    .let { with(density) { it.toDp() } }
                                CourseCard(
                                    cardState = card,
                                    textColor = Color(table.courseTextColor),
                                    textStyle = textStyle,
                                    strokeColor = Color(table.strokeColor),
                                    alphaInt = viewModel.alphaInt,
                                    itemHeight = cardHeight,
                                    contentWidthPx = cardContentWidthPx,
                                    lineHeightPx = lineHeightPx,
                                    textMeasurer = textMeasurer,
                                    modifier = Modifier.offset(y = offsetY),
                                    onCourseClick = {
                                        try {
                                            val activity = context as? androidx.fragment.app.FragmentActivity
                                            if (activity != null) {
                                                if (card.isMultiCourse) {
                                                    MultiCourseFragment.newInstance(week, day, card.startNode)
                                                        .show(activity.supportFragmentManager, "multiCourse")
                                                } else {
                                                    CourseDetailFragment.newInstance(card.course)
                                                        .show(activity.supportFragmentManager, "courseDetail")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            es.dmoral.toasty.Toasty.error(
                                                context.applicationContext, "哎呀>_<差点崩溃了"
                                            ).show()
                                        }
                                    }
                                )
                            }
                    }
                }
            }
        }
    }
}

// ── Course card ──

/**
 * 从教室文本中提取教室编号：包含数字的连续英文/数字片段。
 * 例如「成都校区A121教室」→「A121」、「1号教学楼203」→「203」。
 * 教室一行放不下时用它替代完整文本，保证编号始终可见。
 */
private fun extractRoomNumber(room: String): String? {
    return Regex("[A-Za-z0-9][A-Za-z0-9\\-–—_]*")
        .findAll(room)
        .map { it.value }
        .filter { token -> token.any { it.isDigit() } }
        .maxByOrNull { it.length }
}

@Composable
private fun CourseCard(
    cardState: CourseCardState,
    textColor: Color,
    textStyle: TextStyle,
    strokeColor: Color,
    alphaInt: Int,
    itemHeight: androidx.compose.ui.unit.Dp,
    contentWidthPx: Int,
    lineHeightPx: Float,
    textMeasurer: TextMeasurer,
    modifier: Modifier = Modifier,
    onCourseClick: () -> Unit
) {
    val bgColor = cardState.bgColor
    val alpha = alphaInt / 255f
    val bgAlpha = bgColor.copy(alpha = alpha)

    // 边框颜色自带透明度（默认 0x80ffffff），非本周课程再降到 30%，
    // 与旧版 TipTextView 的描边规则保持一致。
    val strokeAlphaFactor = if (cardState.isOtherWeek) 0.3f else 1f

    // 卡片上下各 4dp 内边距，内容区高度由卡片总高换算
    val contentHeightPx = with(LocalDensity.current) { (itemHeight - 8.dp).roundToPx() }

    val timeText = cardState.timeText
    val roomText = cardState.course.room?.trim().orEmpty()
    val teacherText = cardState.course.teacher?.trim().orEmpty()

    // 固定字号 + 固定行数排版：
    //   1. 课程名称（最多 4 行，超出省略）
    //   2. 教室（单独一行；放不下时优先保留教室编号，如 A121）
    //   3. 教师（单独一行）
    // 卡片高度放不下全部行时，优先保证教室、教师两行，剩余空间再分给课程名称。
    // 行高由页面级统一测量后传入，卡片内不再各自创建 TextMeasurer / SubcomposeLayout。
    val reservedLines = (if (roomText.isNotEmpty()) 1 else 0) +
            (if (teacherText.isNotEmpty()) 1 else 0)
    val totalLines = if (lineHeightPx > 0f) {
        (contentHeightPx / lineHeightPx).toInt()
    } else 0
    val baseLines = (totalLines - reservedLines).coerceAtLeast(0)
    val nameMaxLines = baseLines.coerceAtMost(4)
    val showTimeLine = timeText.isNotEmpty() && baseLines - nameMaxLines >= 1

    val finalTextColor = if (cardState.isOtherWeek) {
        textColor.copy(alpha = 0.3f)
    } else textColor

    // 教室一行放不下时，优先保留包含数字的教室编号（编号不一定在末尾）
    val roomDisplay = remember(roomText, contentWidthPx, textStyle) {
        if (roomText.isEmpty()) {
            ""
        } else {
            val layout = textMeasurer.measure(
                text = roomText,
                style = textStyle,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                maxLines = 1,
                constraints = Constraints(maxWidth = contentWidthPx)
            )
            if (!layout.hasVisualOverflow) roomText
            else extractRoomNumber(roomText) ?: roomText
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(bgAlpha)
            .border(
                width = 1.5.dp,
                color = strokeColor.copy(alpha = strokeColor.alpha * strokeAlphaFactor),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onCourseClick)
            .padding(4.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showTimeLine) {
                Text(text = timeText, color = finalTextColor, style = textStyle,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (nameMaxLines > 0) {
                Text(text = cardState.course.courseName, color = finalTextColor, style = textStyle,
                    maxLines = nameMaxLines, overflow = TextOverflow.Ellipsis)
            }
            if (roomDisplay.isNotEmpty()) {
                Text(text = roomDisplay, color = finalTextColor, style = textStyle,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (teacherText.isNotEmpty()) {
                Text(text = teacherText, color = finalTextColor, style = textStyle,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Corner indicator for multi-course or error
        if (cardState.isMultiCourse || cardState.isError) {
            val indicatorColor = if (cardState.isOtherWeek) textColor.copy(alpha = 0.3f) else textColor
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
            ) {
                if (cardState.isMultiCourse) {
                    // Triangle indicator
                    val path = Path().apply {
                        moveTo(size.width, size.height)
                        lineTo(size.width, size.height - 6.dp.toPx())
                        lineTo(size.width - 6.dp.toPx(), size.height)
                        close()
                    }
                    drawPath(path, color = indicatorColor)
                } else {
                    // X indicator
                    val x1 = size.width - 6.dp.toPx()
                    val y1 = size.height - 3.dp.toPx()
                    val x2 = size.width - 3.dp.toPx()
                    val y2 = size.height - 6.dp.toPx()
                    drawLine(
                        color = indicatorColor,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = indicatorColor,
                        start = Offset(x2, y1),
                        end = Offset(x1, y2),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }
    }
}

// ── Bottom sheet content ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottomSheetContent(
    viewModel: ScheduleViewModel,
    onWeekSelected: (Int) -> Unit,
    onModifyWeek: () -> Unit,
    onCreateSchedule: () -> Unit,
    onManageSchedule: () -> Unit,
    onTableClick: (Int) -> Unit,
    onTimeSettings: () -> Unit,
    onChangeBackground: () -> Unit,
    onCheckCourses: () -> Unit,
    onFAQ: () -> Unit
) {
    val tableSelectList by viewModel.initTableSelectList().observeAsState(emptyList())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 16.dp)
    ) {
        // ── Week selector ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("周数", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onModifyWeek) {
                Text("修改当前周", fontSize = 12.sp)
            }
        }

        // ── 周数滑动条 ──
        val maxWeek = viewModel.table.maxWeek.coerceAtLeast(1)
        if (maxWeek > 1) {
            WeekSlider(
                value = viewModel.selectedWeek.coerceIn(1..maxWeek),
                maxWeek = maxWeek,
                onValueChange = { week ->
                    viewModel.selectedWeek = week
                    onWeekSelected(week)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Multi-table section ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("多课表", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Row {
                TextButton(onClick = onCreateSchedule) {
                    Text("新建课表", fontSize = 12.sp)
                }
                TextButton(onClick = onManageSchedule) {
                    Text("管理", fontSize = 12.sp)
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            items(tableSelectList) { table ->
                AssistChip(
                    onClick = {
                        if (table.id != viewModel.table.id) {
                            onTableClick(table.id)
                        }
                    },
                    label = { Text(table.tableName, fontSize = 12.sp) },
                    colors = if (table.id == viewModel.table.id) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    } else {
                        AssistChipDefaults.assistChipColors()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Shortcuts ──
        Text("捷径", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onTimeSettings) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("上课时间", fontSize = 12.sp)
                }
            }
            TextButton(onClick = onChangeBackground) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("更换背景", fontSize = 12.sp)
                }
            }
            TextButton(onClick = onCheckCourses) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Checklist, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("已添课程", fontSize = 12.sp)
                }
            }
            TextButton(onClick = onFAQ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Help, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("常见问题", fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Helper functions ──

private fun computeDayMap(showSat: Boolean, showSun: Boolean, sundayFirst: Boolean): IntArray {
    val dayMap = IntArray(8) { -1 }
    if (!sundayFirst) {
        // Mon=1(col1), Tue=2(col2), ..., Fri=5(col5), Sat=6(col6), Sun=7(col7)
        for (i in 1..7) {
            dayMap[i] = if (i <= 5 || (i == 6 && showSat) || (i == 7 && showSun)) i else -1
        }
    } else {
        // Sun=7(col1), Mon=1(col2), ..., Sat=6(col7)
        dayMap[7] = if (showSun) 1 else -1
        for (i in 1..5) {
            dayMap[i] = i + 1
        }
        dayMap[6] = if (showSat) 7 else -1
    }
    return dayMap
}

internal fun buildCourseCardStates(
    courses: List<CourseBean>,
    week: Int,
    day: Int,
    table: com.Azhe.ghs.gshschedule.bean.TableBean,
    alphaInt: Int,
    timeList: List<com.Azhe.ghs.gshschedule.bean.TimeDetailBean>,
    context: android.content.Context
): List<CourseCardState> {
    if (courses.isEmpty()) return emptyList()

    val visibleCourses = courses.filter { it.endWeek >= week }
    val activeCourses = visibleCourses.filter { !isOtherWeekCourse(it, week) }
    // 同一时间段本周有课时，非本周课程会被完全遮住，不再绘制（避免它占用位置）
    val activeStartNodes = activeCourses.map { it.startNode }.toHashSet()
    val activeCountByNode = activeCourses.groupingBy { it.startNode }.eachCount()

    val result = mutableListOf<CourseCardState>()

    for (course in visibleCourses) {
        val isOtherWeek = isOtherWeekCourse(course, week)

        if (!table.showOtherWeekCourse && isOtherWeek) continue
        if (isOtherWeek && course.startNode in activeStartNodes) continue

        var isError = false
        var c = course.copy()

        if (c.step <= 0) { c = c.copy(step = 1); isError = true }
        if (c.startNode <= 0) { c = c.copy(startNode = 1); isError = true }
        if (c.startNode > table.nodes) { c = c.copy(startNode = table.nodes); isError = true }
        if (c.startNode + c.step - 1 > table.nodes) {
            c = c.copy(step = table.nodes - c.startNode + 1); isError = true
        }

        // 卡片排版：课程名称（最多四行，剩余高度不足时优先保留教室/教师行） / 教室（单独一行） / 教师（单独一行）
        // 单双周、非本周等信息不再显示文字，非本周课程仍通过降低不透明度区分。
        val timeText = if (table.showTime && timeList.isNotEmpty() && c.startNode - 1 < timeList.size) {
            timeList[c.startNode - 1].startTime
        } else ""

        // 同一时间段是否存在多门本周课程（与遍历顺序无关）
        val isMultiCourse = !isOtherWeek && (activeCountByNode[c.startNode] ?: 0) > 1

        // Assign color
        val bgColor = if (c.color.isEmpty()) {
            Color(ViewUtils.getCustomizedColor(context, c.id % 16))
        } else {
            try {
                Color(AndroidColor.parseColor(c.color))
            } catch (e: Exception) {
                Color(ViewUtils.getCustomizedColor(context, c.id % 16))
            }
        }

        result.add(
            CourseCardState(
                course = c,
                timeText = timeText,
                bgColor = bgColor,
                isOtherWeek = isOtherWeek,
                isMultiCourse = isMultiCourse,
                isError = isError,
                startNode = c.startNode,
                step = c.step
            )
        )
    }

    return result
}

/** 该课程在指定周是否不属于"本周"（单双周冲突或还没开课）。 */
private fun isOtherWeekCourse(course: CourseBean, week: Int): Boolean {
    return (week % 2 == 0 && course.type == 1) ||
            (week % 2 == 1 && course.type == 2) ||
            (course.startWeek > week)
}

// ── Custom Week Slider ────────────────────────────────────────────────

/**
 * 自定义周数滑动条。
 *
 * 视觉样式：
 *  - 轨道：圆角长条，已填充部分主题色，未填充 #E8ECF4
 *  - 滑块：垂直短线（| 形），深色 #3A4B7C，带阴影
 *
 * 交互：
 *  - 点击轨道某处直接跳转到对应周
 *  - 左右拖动连续滑动
 *  - 拖动时上方显示当前周数气泡
 */
@Composable
private fun WeekSlider(
    value: Int,
    maxWeek: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderPos by remember { mutableFloatStateOf(value.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    // 非拖动时同步外部 value
    if (!isDragging) {
        sliderPos = value.toFloat().coerceIn(1f..maxWeek.toFloat())
    }

    // 连续浮点分数（不取整），拖动丝滑；只在松手时 roundToInt 提交
    val fraction = if (maxWeek <= 1) 0f
        else ((sliderPos - 1f) / (maxWeek - 1f)).coerceIn(0f, 1f)

    val trackHeight = 14.dp
    val thumbW = 6.dp
    val thumbH = 24.dp
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (isDragging) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "第 ${sliderPos.roundToInt()} 周",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbH)
                .pointerInput(maxWeek) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
                        sliderPos = xToPos(down.position.x, size.width.toFloat(), maxWeek)

                        // 持续跟踪手指位置，只在真正移动时更新
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            change.consume()
                            sliderPos = xToPos(change.position.x, size.width.toFloat(), maxWeek)
                        }

                        isDragging = false
                        onValueChange(sliderPos.roundToInt().coerceIn(1..maxWeek))
                    }
                },
            // CenterStart: 子元素从左起算且垂直居中
            // Track 填满宽度所以无影响，Thumb 的 offset 从 x=0 起算，紧贴填充色条
            contentAlignment = Alignment.CenterStart
        ) {
            // ── 轨道 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(trackColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            // ── 滑块 Thumb — 左边缘紧贴填充色条右边缘 ──
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * fraction)
                    .width(thumbW)
                    .height(thumbH)
                    .shadow(2.dp, RoundedCornerShape(2.dp))
                    .background(thumbColor, RoundedCornerShape(2.dp))
            )
        }
    }
}

/** 触摸坐标 → 连续浮点周数（不取整，供拖动用） */
private fun xToPos(touchX: Float, containerW: Float, maxWeek: Int): Float {
    val f = (touchX / containerW).coerceIn(0f, 1f)
    return (1f + f * (maxWeek - 1)).coerceIn(1f..maxWeek.toFloat())
}
