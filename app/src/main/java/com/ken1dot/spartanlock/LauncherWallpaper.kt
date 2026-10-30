package com.ken1dot.spartanlock

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

private val wallpaperUri = Uri.parse("content://com.ken1dot.spartanlauncher.accent/wallpaper")

internal fun loadLauncherWallpaper(context: Context): ImageBitmap? = try {
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, wallpaperUri)) {
            decoder, info, _ ->
            val largestSide = maxOf(info.size.width, info.size.height)
            decoder.setTargetSampleSize(maxOf(1, (largestSide + 2047) / 2048))
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(wallpaperUri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val largestSide = maxOf(bounds.outWidth, bounds.outHeight)
        val sample = maxOf(1, (largestSide + 2047) / 2048)
        context.contentResolver.openInputStream(wallpaperUri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
    bitmap?.asImageBitmap()
} catch (_: Exception) {
    null
}
