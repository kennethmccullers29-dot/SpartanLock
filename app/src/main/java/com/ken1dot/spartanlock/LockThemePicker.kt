package com.ken1dot.spartanlock

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.absoluteValue

private val MetroAccent = Color(0xFF0078D7)
private val MetroSecondary = Color(0xFFAAAAAA)

@Composable
internal fun LockThemePicker(
    selected: LockTheme,
    wallpaper: ImageBitmap?,
    now: Date,
    use24Hour: Boolean,
    onApply: (LockTheme) -> Unit,
    onCancel: () -> Unit
) {
    val themes = LockTheme.entries
    val pager = rememberPagerState(initialPage = themes.indexOf(selected)) { themes.size }
    val pivots = rememberLazyListState(initialFirstVisibleItemIndex = themes.indexOf(selected))
    val scope = rememberCoroutineScope()
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler(onBack = onCancel)
    LaunchedEffect(pager.currentPage) { pivots.animateScrollToItem(pager.currentPage) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val compact = maxHeight < 680.dp
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = if (compact) 16.dp else 30.dp)) {
                Text("PERSONALIZATION", color = Color.White, fontFamily = SelawikFamily,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp)
                Text("lock screen", color = Color.White, fontFamily = SelawikFamily,
                    fontWeight = FontWeight.Light, fontSize = if (compact) 38.sp else 46.sp,
                    letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 5.dp))
                Text("more options in Spartan Launcher: Settings > Personalization > Lock screen", color = Color(0xFF999999),
                    fontFamily = SelawikFamily, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }

            // Metro pivots are both the page title and direct navigation.
            LazyRow(
                state = pivots,
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp)
            ) {
                items(themes.size, key = { themes[it].id }) { index ->
                    val active = pager.currentPage == index
                    Column(
                        Modifier.heightIn(min = 48.dp)
                            .semantics { this.selected = active }
                            .clickable(role = Role.Tab, onClickLabel = "Preview ${themes[index].title}") {
                                scope.launch { pager.animateScrollToPage(index) }
                            },
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(themes[index].title.lowercase(), fontFamily = SelawikFamily,
                            fontWeight = FontWeight.Light, fontSize = 27.sp,
                            color = if (active) Color.White else Color(0xFF777777))
                        Box(Modifier.padding(top = 5.dp).width(28.dp).height(2.dp)
                            .background(if (active) MetroAccent else Color.Transparent))
                    }
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pageWidth = minOf((maxWidth - 82.dp).coerceAtLeast(1.dp),
                (maxHeight - 25.dp).coerceAtLeast(1.dp) * (480f / 800f))
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed(pageWidth),
                contentPadding = PaddingValues(start = 24.dp, end = 58.dp),
                pageSpacing = 18.dp,
                beyondViewportPageCount = 1,
                key = { themes[it].id },
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val theme = themes[page]
                var drag by remember { mutableFloatStateOf(0f) }
                var dragging by remember { mutableStateOf(false) }
                val progress by animateFloatAsState(
                    targetValue = drag,
                    animationSpec = if (dragging) snap() else spring(dampingRatio = 0.85f),
                    label = "Unlock preview"
                )
                BoxWithConstraints(
                    Modifier.fillMaxSize().graphicsLayer {
                        val distance = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
                            .absoluteValue.coerceIn(0f, 1f)
                        alpha = 1f - distance * 0.45f
                        scaleX = 1f - distance * 0.035f
                        scaleY = 1f - distance * 0.035f
                    },
                    contentAlignment = Alignment.CenterStart
                ) {
                    val cardHeight = minOf((maxHeight - 25.dp).coerceAtLeast(1.dp), maxWidth * (800f / 480f))
                    val cardWidth = cardHeight * (480f / 800f)
                    Column(Modifier.width(cardWidth)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${(page + 1).toString().padStart(2, '0')} / 07",
                                color = MetroSecondary, fontFamily = SelawikFamily, fontSize = 11.sp,
                                letterSpacing = 1.sp)
                            Text(if (theme == selected) "IN USE" else "PREVIEW",
                                color = if (theme == selected) MetroAccent else MetroSecondary,
                                fontFamily = SelawikFamily, fontSize = 11.sp, letterSpacing = 1.sp)
                        }
                        Box(
                            Modifier.fillMaxWidth().height(cardHeight).clipToBounds()
                                .background(Brush.verticalGradient(
                                    listOf(Color(0xFF174575), Color(0xFF050C15)))))
                        {
                            Box(Modifier.fillMaxSize().graphicsLayer {
                                translationY = -progress.coerceIn(0f, 1f) * size.height
                            }) {
                                if (wallpaper != null) Image(wallpaper, contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                                    Color.Black.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.28f)))))
                                LiveLockClock(
                                    theme, now, use24Hour,
                                    { if (theme.hasSpecialUnlock) progress else 0f }, wallpaper,
                                    Modifier.fillMaxSize().graphicsLayer {
                                        alpha = if (theme.hasSpecialUnlock) 1f
                                            else (1f - progress * 800f / 180f).coerceIn(0f, 1f)
                                    },
                                    animate = pager.currentPage == page
                                )
                            }
                            // Border and gesture target sit above the clock without intercepting pager swipes.
                            Box(Modifier.fillMaxSize()
                                .border(if (theme == selected) 2.dp else 1.dp,
                                    if (theme == selected) MetroAccent else Color(0xFF454545))
                                .pointerInput(theme) {
                                    detectVerticalDragGestures(
                                        onDragStart = { dragging = true },
                                        onVerticalDrag = { change, amount ->
                                            change.consume()
                                            drag = (drag - amount / size.height).coerceIn(0f, 1f)
                                        },
                                        onDragEnd = { dragging = false; drag = 0f },
                                        onDragCancel = { dragging = false; drag = 0f }
                                    )
                                })
                        }
                    }
                }
            }

            }

            val current = themes[pager.currentPage]
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = if (compact) 10.dp else 18.dp)) {
                Text(current.detail, color = Color.White, fontFamily = SelawikFamily,
                    fontSize = 15.sp, lineHeight = 20.sp, minLines = 2, maxLines = 2)
                Text("Drag up on the preview to try it.", color = MetroSecondary,
                    fontFamily = SelawikFamily, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            }

            Row(Modifier.fillMaxWidth().background(Color(0xFF1F1F1F))) {
                MetroPickerCommand("cancel", false, true, onCancel, Modifier.weight(1f))
                MetroPickerCommand(
                    if (current == selected) "done" else "apply",
                    true, !pager.isScrollInProgress,
                    { onApply(themes[pager.settledPage]) }, Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetroPickerCommand(
    label: String,
    confirm: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.height(76.dp).background(if (confirm) MetroAccent else Color.Transparent)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(Modifier.size(25.dp)) {
            val stroke = 1.6.dp.toPx()
            if (confirm) {
                drawLine(Color.White, Offset(size.width * 0.16f, size.height * 0.51f),
                    Offset(size.width * 0.40f, size.height * 0.75f), stroke)
                drawLine(Color.White, Offset(size.width * 0.40f, size.height * 0.75f),
                    Offset(size.width * 0.86f, size.height * 0.22f), stroke)
            } else {
                drawLine(Color.White, Offset(size.width * 0.85f, size.height * 0.5f),
                    Offset(size.width * 0.15f, size.height * 0.5f), stroke)
                drawLine(Color.White, Offset(size.width * 0.15f, size.height * 0.5f),
                    Offset(size.width * 0.48f, size.height * 0.17f), stroke)
                drawLine(Color.White, Offset(size.width * 0.15f, size.height * 0.5f),
                    Offset(size.width * 0.48f, size.height * 0.83f), stroke)
            }
        }
        Text(label, color = Color.White, fontFamily = SelawikFamily, fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp))
    }
}
