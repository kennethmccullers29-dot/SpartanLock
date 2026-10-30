package com.ken1dot.spartanlock

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AlbumArtGradient(artwork: ImageBitmap, opacity: Float) {
    val tint by produceState(Color(0xFF263040), artwork) {
        value = withContext(Dispatchers.Default) { albumTint(artwork) }
    }
    val animatedTint by animateColorAsState(tint, tween(900), label = "Album gradient color")
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.4f to animatedTint.copy(alpha = 0.06f),
                0.72f to animatedTint.copy(alpha = 0.65f),
                1f to animatedTint.copy(alpha = 0.98f),
                startY = 0f,
                endY = size.height
            ),
            alpha = opacity.coerceIn(0f, 1f)
        )
    }
}

/** Small color histogram favors recurring chromatic colors over black borders and highlights. */
private fun albumTint(artwork: ImageBitmap): Color {
    return try {
        val original = artwork.asAndroidBitmap()
        val sample = Bitmap.createScaledBitmap(original, 32, 32, true)
        try {
            val weights = FloatArray(18)
            val red = FloatArray(18)
            val green = FloatArray(18)
            val blue = FloatArray(18)
            val hsv = FloatArray(3)
            val pixels = IntArray(1024)
            sample.getPixels(pixels, 0, 32, 0, 0, 32, 32)
            var neutral = 0f
            var count = 0
            pixels.forEach { pixel ->
                if (android.graphics.Color.alpha(pixel) >= 128) {
                    android.graphics.Color.colorToHSV(pixel, hsv)
                    neutral += hsv[2]
                    count++
                    if (hsv[1] >= 0.18f && hsv[2] >= 0.15f) {
                        val bin = (hsv[0] / 20f).toInt().coerceIn(0, 17)
                        val weight = hsv[1] * hsv[2]
                        weights[bin] += weight
                        red[bin] += android.graphics.Color.red(pixel) * weight
                        green[bin] += android.graphics.Color.green(pixel) * weight
                        blue[bin] += android.graphics.Color.blue(pixel) * weight
                    }
                }
            }
            val bin = weights.indices.maxByOrNull { weights[it] } ?: 0
            if (weights[bin] > 0f) {
                android.graphics.Color.RGBToHSV(
                    (red[bin] / weights[bin]).toInt(),
                    (green[bin] / weights[bin]).toInt(),
                    (blue[bin] / weights[bin]).toInt(), hsv
                )
                hsv[1] = hsv[1].coerceIn(0.45f, 0.85f)
                hsv[2] = hsv[2].coerceIn(0.4f, 0.65f)
                Color(android.graphics.Color.HSVToColor(hsv))
            } else {
                val gray = if (count > 0) (neutral / count * 0.55f).coerceIn(0.12f, 0.4f) else 0.2f
                Color(gray, gray, gray)
            }
        } finally {
            if (sample !== original) sample.recycle()
        }
    } catch (_: RuntimeException) {
        Color(0xFF263040)
    }
}
