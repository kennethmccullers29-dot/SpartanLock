package com.ken1dot.spartanlock

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import android.graphics.Paint
import android.graphics.Typeface
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Observe the launcher's read-only settings bridge; older/missing launchers keep the classic layout. */
@Composable
internal fun rememberZuneTextEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(resolver) {
        val uri = Uri.parse("content://com.ken1dot.spartanlauncher.accent/lock-settings")
        fun refresh() {
            scope.launch {
                enabled = withContext(Dispatchers.IO) {
                    try {
                        resolver.query(uri, null, null, null, null)?.use {
                            it.moveToFirst() && it.getColumnIndex("zune_text_enabled").let { index ->
                                index >= 0 && it.getInt(index) != 0
                            }
                        } ?: false
                    } catch (_: RuntimeException) { false }
                }
            }
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { refresh() }
        }
        val registered = try {
            resolver.registerContentObserver(uri, false, observer)
            true
        } catch (_: RuntimeException) { false }
        refresh()
        onDispose { if (registered) resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

/** Overlapping diagonal passes with a quiet text ribbon between the foreground sweeps. */
@Composable
internal fun ZuneTypography(media: LockMedia, modifier: Modifier = Modifier) {
    val elapsed = remember(media.title, media.artist) { Animatable(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(media.title, media.artist, media.isPlaying, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!media.isPlaying) return@repeatOnLifecycle
            while (true) {
                val remainingMillis = ((20f - elapsed.value) * 1000).toInt().coerceAtLeast(1)
                elapsed.animateTo(20f, tween(remainingMillis, easing = LinearEasing))
                elapsed.snapTo(0f)
            }
        }
    }
    ZuneTypographyFrame(media, elapsed.value, modifier)
}

@Composable
private fun ZuneTypographyFrame(media: LockMedia, elapsed: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    } }
    val thin = remember(context) {
        ResourcesCompat.getFont(context, R.font.selawik_light)
            ?: Typeface.create("sans-serif-thin", Typeface.NORMAL)
    }
    val bold = remember(context) {
        ResourcesCompat.getFont(context, R.font.selawik_bold)
            ?: Typeface.create("sans-serif", Typeface.BOLD)
    }
    val title = remember(media.title) {
        media.title.trim().replace(Regex("\\s+"), " ").uppercase(Locale.ROOT)
    }
    val artist = remember(media.artist) {
        media.artist.trim().replace(Regex("\\s+"), " ").uppercase(Locale.ROOT)
    }
    Canvas(modifier.fillMaxSize().clipToBounds().clearAndSetSemantics { }) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val native = drawContext.canvas.nativeCanvas
        fun visibility(start: Float, end: Float): Float =
            minOf((elapsed - start) / 0.6f, (end - elapsed) / 0.8f, 1f).coerceIn(0f, 1f)

        // A subdued ribbon keeps short tracks from leaving the composition empty.
        paint.typeface = thin
        paint.textSize = with(density) { 26.sp.toPx() }
        paint.alpha = 65
        val ribbon = if (artist.isBlank()) "$title  •  " else "$artist  •  $title  •  "
        val ribbonWidth = paint.measureText(ribbon).coerceAtLeast(1f)
        native.save()
        try {
            native.translate(size.width * 0.5f, size.height * 0.5f)
            native.rotate(-45f)
            val extent = kotlin.math.hypot(size.width, size.height)
            val offset = (elapsed / 20f * ribbonWidth) % ribbonWidth
            var x = -extent - offset
            while (x < extent) {
                native.drawText(ribbon, x, -with(density) { 105.dp.toPx() }, paint)
                x += ribbonWidth
            }
        } finally { native.restore() }

        // Both passes keep their angle and font size: the movement comes from translation.
        val secondArtistPass = elapsed >= 10f
        val artistStart = if (secondArtistPass) 10f else 0.5f
        val artistEnd = if (secondArtistPass) 19.5f else 10f
        val artistAlpha = visibility(artistStart, artistEnd)
        if (artistAlpha > 0f && artist.isNotBlank()) {
            paint.typeface = thin
            paint.textSize = with(density) { 132.sp.toPx() }
            paint.alpha = (artistAlpha * 235).toInt()
            val progress = ((elapsed - artistStart) / (artistEnd - artistStart)).coerceIn(0f, 1f)
            val width = paint.measureText(artist)
            val margin = kotlin.math.hypot(size.width, size.height) * 0.55f
            native.save()
            try {
                native.translate(size.width * 0.5f, size.height * 0.5f)
                native.rotate(-45f)
                val x = margin - progress * (width + margin * 2f)
                native.drawText(artist, x, 0f, paint)
            } finally { native.restore() }
        }

        val firstTitlePass = elapsed in 2f..11f
        val secondTitlePass = elapsed in 11f..20f
        if (firstTitlePass || secondTitlePass) {
            val start = if (firstTitlePass) 2f else 11f
            val end = if (firstTitlePass) 11f else 20f
            val progress = ((elapsed - start) / (end - start)).coerceIn(0f, 1f)
            paint.typeface = bold
            paint.textSize = with(density) { (if (firstTitlePass) 40.sp else 62.sp).toPx() }
            paint.alpha = (visibility(start, end) * 255).toInt()
            // Long titles form the reference's tight block; short titles stay on one line.
            val lines = mutableListOf<String>()
            var line = ""
            for (word in title.split(" ")) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && paint.measureText(candidate) > size.width * 1.2f) {
                    lines.add(line)
                    line = word
                } else line = candidate
            }
            if (line.isNotEmpty()) lines.add(line)
            val width = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
            val lineHeight = paint.textSize * 0.94f
            val margin = kotlin.math.hypot(size.width, size.height) * 0.55f
            val x = if (firstTitlePass) {
                -width - margin + progress * (width + margin * 2f)
            } else {
                margin - progress * (width + margin * 2f)
            }
            native.save()
            try {
                native.translate(size.width * 0.5f, size.height * 0.5f)
                native.rotate(-45f)
                val baseline = with(density) { 95.dp.toPx() }
                lines.forEachIndexed { index, text ->
                    native.drawText(text, x, baseline + index * lineHeight, paint)
                }
            } finally { native.restore() }
        }
        paint.alpha = 255
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 412, heightDp = 360, backgroundColor = 0xFF001242, showBackground = true)
@Composable
private fun ZuneTypographyPreview() {
    ZuneTypographyFrame(
        LockMedia("So Heavy I Fell Through the Earth (Art Mix)", "Grimes", null, isPlaying = false, canSkipPrevious = true, canSkipNext = true),
        elapsed = 5f
    )
}
