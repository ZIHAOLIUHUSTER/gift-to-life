package com.gift.tolife.feature.memory

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.gift.tolife.core.ai.DeckItem
import com.gift.tolife.core.ai.DeckItemKind
import com.gift.tolife.core.ui.component.AppEmptyState
import com.gift.tolife.core.ui.component.PageHeader
import com.gift.tolife.core.ui.theme.LifeMotion
import com.gift.tolife.core.ui.theme.LifeType
import com.gift.tolife.core.ui.theme.ThemeExtended
import kotlinx.coroutines.flow.drop

/**
 * 总结牌桌：左右滑动翻卡，点一下看背面。
 *
 * 2026-09-26 改版（方案 C）：**定高牌框 + 真堆叠层次**。
 * 之前是"高度随内容"，短卡会变成飘在大片空白里的一条横条、邻卡只露 8dp 还贴着屏幕边被切。
 * 现在所有卡片同一尺寸（宽 = 屏宽 − 40dp，高 = min(可用高 × 0.68, 520dp)），
 * 邻卡露出 14dp、缩放 0.94、透明度 0.7；内容三段固定（标签 / 内容 / 底部信息），
 * 超长整行裁剪并提示"还有约 N 行 · 点开读全文"。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SummaryDeckScreen(
    onBack: () -> Unit,
    viewModel: SummaryDeckViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRegenerate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var readingItem by remember { mutableStateOf<DeckItem?>(null) }
    val pagerState = rememberPagerState(pageCount = { state.items.size })
    val context = LocalContext.current
    val gestureHints = remember(context) { DeckGestureHints(context) }
    var hintState by remember(gestureHints) { mutableStateOf(gestureHints.read()) }

    BackHandler(enabled = readingItem != null) { readingItem = null }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect {
            if (state.items.size > 1 && !hintState.swiped) {
                gestureHints.recordSwipe()
                hintState = hintState.copy(swiped = true)
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PageHeader(
                title = state.title.ifBlank { "回顾" },
                eyebrow = state.subtitle.takeIf { it.isNotBlank() },
                onBack = { if (readingItem != null) readingItem = null else onBack() },
                actions = {
                    if (state.regenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("重新写一次") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = { menuOpen = false; confirmRegenerate = true },
                            )
                            DropdownMenuItem(
                                text = { Text("删掉这条", color = ThemeExtended.current.danger) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                !state.ready -> Box(Modifier.align(Alignment.Center).padding(24.dp)) {
                    AppEmptyState(title = "这条总结已经不在了", description = "它可能被删除了")
                }
                readingItem != null -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = CARD_MARGIN, vertical = 18.dp),
                ) {
                    FullReading(item = readingItem!!, onCollapse = { readingItem = null })
                }
                else -> DeckTable(
                    items = state.items,
                    pagerState = pagerState,
                    onReadFull = { readingItem = it },
                    hintState = hintState,
                    onFlipped = {
                        if (!hintState.flipped) {
                            gestureHints.recordFlip()
                            hintState = hintState.copy(flipped = true)
                        }
                    },
                )
            }
        }
    }

    if (confirmRegenerate) {
        AlertDialog(
            onDismissRequest = { confirmRegenerate = false },
            title = { Text("重新写一次？") },
            text = { Text("会用这段时间的记录重新生成，覆盖现在这一份，并消耗一次 API 调用。") },
            confirmButton = {
                TextButton(onClick = { confirmRegenerate = false; viewModel.regenerate() }) { Text("重新写") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRegenerate = false }) { Text("取消") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删掉这条总结？") },
            text = { Text("会移进回收站，原始记录不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDone = onBack)
                }) { Text("删除", color = ThemeExtended.current.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

// ---------------------------------------------------------------- 牌桌

/** 卡片左右留白：牌宽 = 屏宽 − 2 × CARD_MARGIN。 */
private val CARD_MARGIN = 22.dp

/** 邻卡露出的宽度（缩放之后净露出这么多）。 */
private val CARD_PEEK = 14.dp

/** 非当前卡片的缩放与透明度。 */
private const val BACK_SCALE = 0.94f
private const val BACK_ALPHA = 0.7f

/** 定高上限：太高在平板上会变成一条竖着的长条。 */
private val CARD_MAX_HEIGHT = 520.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckTable(
    items: List<DeckItem>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onReadFull: (DeckItem) -> Unit,
    hintState: DeckGestureHintState,
    onFlipped: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardWidth = maxWidth - CARD_MARGIN * 2
        val cardHeight = minOf(maxHeight * 0.68f, CARD_MAX_HEIGHT)
        // 缩放会把邻卡往里收，所以几何上的露出要补偿回来，保证"看上去露出 CARD_PEEK"
        val geometryPeek = CARD_PEEK + cardWidth * ((1f - BACK_SCALE) / 2f)
        val pageWidth = cardWidth + (CARD_MARGIN - geometryPeek)

        val current = items.getOrNull(pagerState.currentPage)
        val glow = MaterialTheme.colorScheme.primary.copy(
            alpha = if (ThemeExtended.current.dark) 0.10f else 0.045f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(glow, Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.42f),
                            radius = size.maxDimension * 0.62f,
                        )
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CompositionLocalProvider(
                LocalDeckCardWidth provides cardWidth,
                LocalDeckCardHeight provides cardHeight,
            ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight),
                pageSize = PageSize.Fixed(pageWidth),
                contentPadding = PaddingValues(horizontal = CARD_MARGIN),
                pageSpacing = 0.dp,
            ) { page ->
                val item = items.getOrNull(page) ?: return@HorizontalPager
                val offset = run {
                    val raw = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    if (raw > 1f) 1f else if (raw < -1f) -1f else raw
                }
                val distance = kotlin.math.abs(offset)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .zIndex(1f - distance)
                        .graphicsLayer {
                            val scale = 1f + (BACK_SCALE - 1f) * distance
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f + (BACK_ALPHA - 1f) * distance
                            transformOrigin = TransformOrigin.Center
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    DeckCard(
                        item = item,
                        width = cardWidth,
                        height = cardHeight,
                        raised = distance < 0.01f,
                        onReadFull = { onReadFull(item) },
                        onFlipped = onFlipped,
                    )
                }
            }
            }

            Spacer(Modifier.height(16.dp))
            PageDots(count = items.size, current = pagerState.currentPage)
            Spacer(Modifier.height(10.dp))
            // 只保留首次的一行教学；学会后留住占位，不让牌框上下跳动。
            val hintHeight = with(LocalDensity.current) {
                MaterialTheme.typography.labelSmall.lineHeight.toDp()
            }.coerceAtLeast(18.dp)
            Box(
                Modifier.fillMaxWidth().height(hintHeight).padding(horizontal = CARD_MARGIN),
                contentAlignment = Alignment.Center,
            ) {
                hintState.hint(canSwipe = items.size > 1, canFlip = current?.canFlip == true)?.let { hint ->
                    Text(
                        hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { index ->
            val on = index == current
            val width by animateDpAsState(
                targetValue = if (on) 16.dp else 5.dp,
                animationSpec = tween(LifeMotion.FAST),
                label = "pageDash",
            )
            Box(
                Modifier.width(16.dp).height(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(width)
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                        ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 一张卡

/** 正文分档：短句大一点、长文小一点，避免"短卡一片小字、长卡挤成砖头"。 */
internal const val BODY_TIER_SHORT = 20
internal const val BODY_TIER_MEDIUM = 60

internal enum class BodyTier { LARGE, MEDIUM, SMALL }

internal fun bodyTierOf(charCount: Int): BodyTier = when {
    charCount <= BODY_TIER_SHORT -> BodyTier.LARGE
    charCount <= BODY_TIER_MEDIUM -> BodyTier.MEDIUM
    else -> BodyTier.SMALL
}

private fun BodyTier.style(base: TextStyle): TextStyle = when (this) {
    BodyTier.LARGE -> base.copy(fontSize = 22.sp, lineHeight = 32.sp)
    BodyTier.MEDIUM -> base.copy(fontSize = 17.sp, lineHeight = 27.sp)
    BodyTier.SMALL -> base.copy(fontSize = 15.sp, lineHeight = 24.sp)
}

@Composable
private fun DeckCard(
    item: DeckItem,
    width: Dp,
    height: Dp,
    raised: Boolean,
    onReadFull: () -> Unit,
    onFlipped: () -> Unit,
) {
    var flipped by remember(item) { mutableStateOf(false) }
    val density = LocalDensity.current
    val target = if (flipped && item.canFlip) 180f else 0f
    val rotation by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(LifeMotion.SLOW + 200, easing = FastOutSlowInEasing),
        label = "cardFlip",
    )
    val showBack = rotation > 90f

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .then(
                when {
                    item.canFlip -> Modifier.clickable {
                        flipped = !flipped
                        onFlipped()
                    }
                    // 信件不翻面：点卡片直接进整屏
                    item.kind == DeckItemKind.LETTER -> Modifier.clickable { onReadFull() }
                    else -> Modifier
                }
            )
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 16 * density.density
            },
    ) {
        // 外壳已经定尺；只挂载可见的一面，避免透明面的按钮/滚动层吞掉触摸。
        if (showBack) {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                CardBack(item)
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                CardFront(item, raised = raised, onReadFull = onReadFull)
            }
        }
    }
}

@Composable
private fun CardFrame(
    accent: Color,
    raised: Boolean,
    footer: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .shadow(
                elevation = if (raised) 6.dp else 0.dp,
                shape = RoundedCornerShape(18.dp),
                clip = false,
            ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶部一痕：只画 28dp 的短痕，不做整条色带
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .padding(start = 22.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent.copy(alpha = 0.9f)),
                )
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 20.dp)) {
                // 内容区（吃掉中间全部高度，底部信息条自然贴底）
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.CenterStart,
                    content = content,
                )
                Spacer(Modifier.height(14.dp))
                footer()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardFront(item: DeckItem, raised: Boolean, onReadFull: () -> Unit) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val contentWidth = (LocalDeckCardWidth.current - 44.dp)

    val style = bodyTierOf(item.text.length).style(MaterialTheme.typography.bodyMedium)
    val cardHeight = LocalDeckCardHeight.current
    val maxLines = remember(cardHeight, style, item.title, density.fontScale) {
        maxLinesFor(cardHeight, style, hasTitle = item.title != null, fontScale = density.fontScale)
    }
    val overflowLines = remember(item.text, style, contentWidth, maxLines, density) {
        val w = with(density) { contentWidth.roundToPx() }
        val lines = measurer.measure(
            text = item.text,
            style = style,
            constraints = Constraints(maxWidth = w),
        ).lineCount
        (lines - maxLines).coerceAtLeast(0)
    }

    CardFrame(accent = MaterialTheme.colorScheme.primary, raised = raised, footer = {
        CardFooter(
            left = when {
                overflowLines > 0 -> "还有约 $overflowLines 行"
                item.time != null -> item.time
                item.canFlip -> "本期统计"
                else -> ""
            },
            right = "读全文",
            onClick = onReadFull,
        )
    }) {
        Column(Modifier.fillMaxWidth()) {
            CardLabel(item.label)
            Spacer(Modifier.height(14.dp))
            if (item.title != null) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(
                text = item.text,
                style = style,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = maxLines,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (item.badges.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item.badges.forEach { badge -> DataPill(badge) }
                }
            }
        }
    }
}

@Composable
private fun CardBack(item: DeckItem) {
    val style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 25.sp)

    CardFrame(accent = MaterialTheme.colorScheme.primary, raised = false, footer = {
        CardFooter(
            left = item.backFoot.orEmpty(),
            right = if (item.backMore > 0) "还有 ${item.backMore} 条" else "",
        )
    }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            CardLabel(item.backTitle.orEmpty())
            Spacer(Modifier.height(14.dp))
            item.backText?.let { text ->
                Text(
                    text = text,
                    style = style,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item.backItems.forEach { back ->
                Row(
                    Modifier
                        .padding(bottom = 12.dp)
                        .height(IntrinsicSize.Min),
                ) {
                    Box(
                        Modifier
                            .padding(end = 10.dp)
                            .width(2.dp)
                            .heightIn(min = 20.dp)
                            .fillMaxHeight()
                            .background(
                                if (back.highlighted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                    )
                    Column {
                        Text(
                            back.time + if (back.highlighted) " · 就是这句" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            back.text,
                            style = style,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** 整屏阅读：信件或任何长卡点开后的样子。 */
@Composable
private fun FullReading(item: DeckItem, onCollapse: () -> Unit) {
    CardFrame(
        accent = MaterialTheme.colorScheme.primary,
        raised = true,
        footer = { CardFooter(left = item.time.orEmpty(), right = "收起全文", onClick = onCollapse) },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            CardLabel(item.label)
            Spacer(Modifier.height(16.dp))
            if (item.title != null) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------- 基础件

/**
 * 内容区能放几行。
 *
 * 卡框上下：3dp 标签条 + 20dp×2 内边距 + 14dp 间隔 + 48dp 页脚；
 * 内容区里还要给标签（约 16dp）和标题（约 34dp）让位。
 */
internal fun maxLinesFor(cardHeight: Dp, style: TextStyle, hasTitle: Boolean, fontScale: Float = 1f): Int {
    val contentHeight = cardHeight - 3.dp - 40.dp - 14.dp - 48.dp
    val chrome = 16.dp * fontScale + 14.dp + if (hasTitle) 24.dp * fontScale + 10.dp else 0.dp
    val usable = (contentHeight - chrome).coerceAtLeast(1.dp)
    val lineHeight = (style.lineHeight.value.takeIf { it > 0f } ?: style.fontSize.value * 1.4f) * fontScale
    return (usable.value / lineHeight).toInt().coerceAtLeast(1)
}

/** 当前牌框尺寸：卡片内容需要知道可用宽高来算行数。 */
internal val LocalDeckCardWidth = compositionLocalOf { 320.dp }
internal val LocalDeckCardHeight = compositionLocalOf { 480.dp }

@Composable
private fun DataPill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CardLabel(text: String) {
    Text(
        text = text,
        style = LifeType.eyebrow,
        color = ThemeExtended.current.accentText,
    )
}

@Composable
private fun CardFooter(
    left: String,
    right: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            left,
            modifier = Modifier.weight(1f),
            style = LifeType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (right.isNotBlank()) {
            if (onClick != null) {
                TextButton(
                    onClick = onClick,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                ) { Text(right, style = MaterialTheme.typography.labelMedium) }
            } else {
                Text(
                    right,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


