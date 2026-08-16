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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ── Data class for course card state ──

@androidx.compose.runtime.Stable
data class CourseCardState(
    val course: CourseBean,
    val displayText: String,
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

@OptIn(ExperimentalFoundationApi::class, kotlinx.coroutines.FlowPreview::class)
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

    // Sync selectedWeek with pager — debounced: only update AFTER scroll settles,
    // avoiding mid-animation state writes that trigger recomposition and cause jank.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .debounce(300L)
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val table = viewModel.table
    // 显式读取 timeList Compose 状态，确保修改后触发重组
    val timeList = viewModel.timeList

    val col = 6 + (if (table.showSat) 1 else 0) + (if (table.showSun) 1 else 0)
    val dayMap = remember(table.showSat, table.showSun, table.sundayFirst) {
        computeDayMap(table.showSat, table.showSun, table.sundayFirst)
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

    // Observe course data
    val courseData = List(7) { dayIndex ->
        viewModel.allCourseList[dayIndex].observeAsState(emptyList())
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
            for (day in 1..7) {
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
            for (day in 1..7) {
                val colIdx = dayMap[day]
                if (colIdx == -1) continue

                val dayCourses = courseData[day - 1].value
                val alphaFloat = viewModel.alphaInt / 255f
                val cardStates = remember(dayCourses, week, table.id, table.showTime, alphaFloat, timeList) {
                    buildCourseCardStates(
                        courses = dayCourses ?: emptyList(),
                        week = week,
                        day = day,
                        table = table,
                        alphaInt = viewModel.alphaInt,
                        timeList = timeList,
                        context = context
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                ) {
                    // Course cards positioned in their time slots
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Single spacer to establish total column height
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((itemHeightDp + marTopDp) * table.nodes)
                        )
                        // Course cards overlaid — use relative offsets to avoid cumulative shift
                        Column {
                            var runningBottom = 0.dp
                            cardStates.forEach { card ->
                                val absoluteTop = ((card.startNode - 1) * (viewModel.itemHeight + viewModel.marTop) + viewModel.marTop)
                                    .let { with(density) { it.toDp() } }
                                val cardHeight = (viewModel.itemHeight * card.step + viewModel.marTop * (card.step - 1))
                                    .let { with(density) { it.toDp() } }
                                val relativeSpacer = (absoluteTop - runningBottom).coerceAtLeast(0.dp)

                                if (relativeSpacer > 0.dp) {
                                    Spacer(modifier = Modifier.height(relativeSpacer))
                                }
                                CourseCard(
                                    cardState = card,
                                    textColor = Color(table.courseTextColor),
                                    textSize = table.itemTextSize,
                                    strokeColor = Color(table.strokeColor),
                                    alphaInt = viewModel.alphaInt,
                                    itemHeight = cardHeight,
                                    onCourseClick = {
                                        try {
                                            val detailFragment = CourseDetailFragment.newInstance(card.course)
                                            (context as? androidx.fragment.app.FragmentActivity)
                                                ?.supportFragmentManager
                                                ?.let { detailFragment.show(it, "courseDetail") }
                                        } catch (e: Exception) {
                                            es.dmoral.toasty.Toasty.error(
                                                context.applicationContext, "哎呀>_<差点崩溃了"
                                            ).show()
                                        }
                                    }
                                )
                                runningBottom = absoluteTop + cardHeight
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Course card ──

@Composable
private fun CourseCard(
    cardState: CourseCardState,
    textColor: Color,
    textSize: Int,
    strokeColor: Color,
    alphaInt: Int,
    itemHeight: androidx.compose.ui.unit.Dp,
    onCourseClick: () -> Unit
) {
    val bgColor = cardState.bgColor
    val alpha = alphaInt / 255f
    val bgAlpha = bgColor.copy(alpha = alpha)

    val displayTextAlpha = if (cardState.isOtherWeek) alpha * 0.3f else alpha

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(bgAlpha)
            .clickable(onClick = onCourseClick)
            .padding(4.dp)
    ) {
        // Text content
        Text(
            text = cardState.displayText,
            color = if (cardState.isOtherWeek) textColor.copy(alpha = 0.3f) else textColor,
            fontSize = textSize.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            lineHeight = (textSize + 2).sp
        )

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

    val result = mutableListOf<CourseCardState>()
    var pre = courses.firstOrNull() ?: return emptyList()

    for (course in courses) {
        if (course.endWeek < week) continue

        val isOtherWeek = (week % 2 == 0 && course.type == 1) ||
                (week % 2 == 1 && course.type == 2) ||
                (course.startWeek > week)

        if (!table.showOtherWeekCourse && isOtherWeek) continue

        var isError = false
        var c = course.copy()

        if (c.step <= 0) { c = c.copy(step = 1); isError = true }
        if (c.startNode <= 0) { c = c.copy(startNode = 1); isError = true }
        if (c.startNode > table.nodes) { c = c.copy(startNode = table.nodes); isError = true }
        if (c.startNode + c.step - 1 > table.nodes) {
            c = c.copy(step = table.nodes - c.startNode + 1); isError = true
        }

        // Check for overlap
        val isCovered = if (result.isNotEmpty()) pre.startNode == c.startNode else false

        // Build display text
        val strBuilder = StringBuilder()
        strBuilder.append(c.courseName)
        if (c.room != "") strBuilder.append("\n@${c.room}")
        if (!c.teacher.isNullOrBlank()) strBuilder.append("\n${c.teacher}")
        if (isOtherWeek) {
            when (c.type) {
                1 -> strBuilder.append("\n单周")
                2 -> strBuilder.append("\n双周")
            }
            strBuilder.append("[非本周]")
        } else {
            when (c.type) {
                1 -> strBuilder.append("\n单周")
                2 -> strBuilder.append("\n双周")
            }
        }

        if (table.showTime && timeList.isNotEmpty() && c.startNode - 1 < timeList.size) {
            strBuilder.insert(0, timeList[c.startNode - 1].startTime + "\n")
        }

        // Check if multi-course
        val isMultiCourse = result.any { it.startNode == c.startNode && !it.isOtherWeek } && !isOtherWeek

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

        // Skip covered non-current-week courses
        if (isCovered && isOtherWeek) {
            pre = c
            continue
        }

        result.add(
            CourseCardState(
                course = c,
                displayText = strBuilder.toString(),
                bgColor = bgColor,
                isOtherWeek = isOtherWeek,
                isMultiCourse = isMultiCourse,
                isError = isError,
                startNode = c.startNode,
                step = c.step
            )
        )

        pre = c
    }

    return result
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
