package com.ken1dot.spartanlock

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun splitForegroundAlpha(color: Int): Int {
    val red = color ushr 16 and 255
    val green = color ushr 8 and 255
    val blue = color and 255
    val light = ((minOf(red, green, blue) - 140f) / 95f).coerceIn(0f, 1f)
    return (light * light * (3f - 2f * light) * (color ushr 24)).toInt()
}

private fun splitClockMask(source: Bitmap): Bitmap {
    val width = minOf(source.width, 512)
    val height = (source.height.toLong() * width / source.width).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(source, width, height, true)
    val readable = if (Build.VERSION.SDK_INT >= 26 && scaled.config == Bitmap.Config.HARDWARE)
        scaled.copy(Bitmap.Config.ARGB_8888, false) else scaled
    val pixels = IntArray(width * height)
    readable.getPixels(pixels, 0, width, 0, 0, width, height)
    for (index in pixels.indices) {
        pixels[index] = (splitForegroundAlpha(pixels[index]) shl 24) or 0x00FFFFFF
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

@Composable
internal fun rememberSplitClockMask(source: Bitmap?, enabled: Boolean): Bitmap? {
    // shortcut: bright detail supplies the foreground; use a supplied depth mask for dark subjects.
    fun build(bitmap: Bitmap): Bitmap? = try {
        splitClockMask(bitmap)
    } catch (_: RuntimeException) {
        null
    }
    if (LocalInspectionMode.current) {
        return remember(source, enabled) { if (enabled && source != null) build(source) else null }
    }
    val result = produceState<Pair<Bitmap, Bitmap?>?>(null, source, enabled) {
        value = null
        if (enabled && source != null) {
            value = withContext(Dispatchers.Default) { source to build(source) }
        }
    }.value
    return result?.takeIf { enabled && it.first === source }?.second
}
