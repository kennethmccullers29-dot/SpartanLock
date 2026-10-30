package com.ken1dot.spartanlock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import java.util.Calendar

internal class LockThemePreviewProvider : PreviewParameterProvider<LockTheme> {
    override val values = LockTheme.entries.asSequence()
}

private fun referenceTime() = Calendar.getInstance().apply {
    set(2014, Calendar.JULY, 25, 16, 58, 27)
}.time

/** A high-contrast fixture makes clipped wallpaper rotations visible in previews. */
@Composable
private fun previewWallpaper(): ImageBitmap = remember {
    Bitmap.createBitmap(480, 800, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, 800f,
                intArrayOf(0xFF1E65AC.toInt(), 0xFF123657.toInt(), 0xFF080E1E.toInt()),
                null, Shader.TileMode.CLAMP)
        }
        canvas.drawPaint(paint)
        paint.shader = null
        paint.color = 0xFFA5C9DE.toInt()
        canvas.drawOval(175f, 320f, 610f, 410f, paint)
        canvas.drawOval(-100f, 430f, 350f, 470f, paint)
        canvas.drawOval(290f, 230f, 530f, 380f, paint)
    }.asImageBitmap()
}

@Preview(widthDp = 360, heightDp = 600)
@Composable
internal fun LockClockReferencePreview(@PreviewParameter(LockThemePreviewProvider::class) theme: LockTheme) {
    val wallpaper = previewWallpaper()
    Box(Modifier.fillMaxSize()) {
        Image(wallpaper, contentDescription = null, modifier = Modifier.fillMaxSize())
        LiveLockClock(theme, referenceTime(), false, { 0f }, wallpaper, Modifier.fillMaxSize())
    }
}

@Preview(widthDp = 412, heightDp = 820)
@Composable
internal fun LockThemeGalleryPreview() {
    LockThemePicker(LockTheme.DIAGONAL, previewWallpaper(), referenceTime(), false, {}, {})
}
