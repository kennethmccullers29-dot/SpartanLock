package com.ken1dot.spartanlock

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun LockMediaArtwork(media: LockMedia?, fallback: ImageBitmap?) {
    val image = media?.artwork ?: fallback ?: return
    val transition = rememberInfiniteTransition(label = "Artist artwork drift")
    val horizontal by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000), RepeatMode.Reverse),
        label = "Horizontal artwork drift"
    )
    val vertical by transition.animateFloat(
        initialValue = 1f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(tween(19_000), RepeatMode.Reverse),
        label = "Vertical artwork drift"
    )
    // Alternate album art with the artist picture (when the session provides one) via a slow crossfade.
    val artist = media?.artistArtwork
    val showArtist = androidx.compose.runtime.remember(media?.title) { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(artist, media?.isPlaying) {
        showArtist.value = false
        if (artist == null || media?.isPlaying != true) return@LaunchedEffect
        while (true) { kotlinx.coroutines.delay(12_000); showArtist.value = !showArtist.value }
    }
    val artistAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (showArtist.value && artist != null) 1f else 0f, tween(1_400), label = "Artist crossfade")
    Box(Modifier.fillMaxSize().clipToBounds()) {
        listOfNotNull(image to (1f - artistAlpha), artist?.let { it to artistAlpha }).forEach { (bmp, a) ->
        if (a > 0.001f) Image(
            bitmap = bmp,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = a
                if (media?.artwork != null) {
                    scaleX = 1.16f
                    scaleY = 1.16f
                    if (media.isPlaying) {
                        translationX = horizontal * 20.dp.toPx()
                        translationY = vertical * 14.dp.toPx()
                    }
                }
            }
        )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LockMediaControls(media: LockMedia) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = media.title,
            color = Color.White,
            fontFamily = SelawikFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().basicMarquee(),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
        if (media.artist.isNotBlank()) {
            Text(
                text = media.artist,
                color = Color.White.copy(alpha = 0.84f),
                fontFamily = SelawikFamily,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().basicMarquee(),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaButton("Previous track", MediaGlyph.Previous, media.canSkipPrevious) {
                LockMediaStore.previous()
            }
            MediaButton(
                if (media.isPlaying) "Pause" else "Play",
                if (media.isPlaying) MediaGlyph.Pause else MediaGlyph.Play,
                true
            ) {
                LockMediaStore.togglePlayback()
            }
            MediaButton("Next track", MediaGlyph.Next, media.canSkipNext) {
                LockMediaStore.next()
            }
        }
    }
}

private enum class MediaGlyph { Previous, Play, Pause, Next }

@Composable
private fun MediaButton(
    label: String,
    glyph: MediaGlyph,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(40.dp).clickable(
            enabled = enabled,
            onClickLabel = label,
            onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(22.dp).graphicsLayer { alpha = if (enabled) 1f else 0.4f }) {
            when (glyph) {
                MediaGlyph.Play -> triangle(0.2f, 0.15f, 0.8f, 0.5f, 0.2f, 0.85f)
                MediaGlyph.Pause -> {
                    drawRect(Color.White, Offset(size.width * 0.2f, size.height * 0.17f),
                        Size(size.width * 0.2f, size.height * 0.66f))
                    drawRect(Color.White, Offset(size.width * 0.6f, size.height * 0.17f),
                        Size(size.width * 0.2f, size.height * 0.66f))
                }
                MediaGlyph.Previous -> {
                    drawRect(Color.White, Offset(size.width * 0.14f, size.height * 0.16f),
                        Size(size.width * 0.11f, size.height * 0.68f))
                    triangle(0.79f, 0.16f, 0.27f, 0.5f, 0.79f, 0.84f)
                }
                MediaGlyph.Next -> {
                    triangle(0.21f, 0.16f, 0.73f, 0.5f, 0.21f, 0.84f)
                    drawRect(Color.White, Offset(size.width * 0.75f, size.height * 0.16f),
                        Size(size.width * 0.11f, size.height * 0.68f))
                }
            }
        }
    }
}

private fun DrawScope.triangle(
    x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float
) {
    val path = Path().apply {
        moveTo(size.width * x1, size.height * y1)
        lineTo(size.width * x2, size.height * y2)
        lineTo(size.width * x3, size.height * y3)
        close()
    }
    drawPath(path, Color.White)
}
