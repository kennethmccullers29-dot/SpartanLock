package com.ken1dot.spartanlock

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

internal fun orbitAngle(timeMillis: Double, revolutionMillis: Double): Float =
    (((timeMillis % revolutionMillis + revolutionMillis) % revolutionMillis) /
        revolutionMillis * 360.0).toFloat()

internal fun orbitWeekday(firstDay: Int, index: Int): Int =
    (firstDay - Calendar.SUNDAY + index) % 7 + Calendar.SUNDAY

@Composable
internal fun OrbitClock(
    now: Date,
    wallpaper: ImageBitmap?,
    progress: () -> Float,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val inspection = LocalInspectionMode.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val frameTime = remember { mutableDoubleStateOf(now.time.toDouble()) }
    val bitmap = remember(wallpaper) { wallpaper?.asAndroidBitmap() }
    val font = remember(context) { ResourcesCompat.getFont(context, R.font.selawik_regular)!! }
    val calendar = remember(now, locale) { Calendar.getInstance(locale).apply { time = now } }
    val firstDay = calendar.firstDayOfWeek
    val today = calendar.get(Calendar.DAY_OF_WEEK)
    val utcOffset = TimeZone.getDefault().getOffset(now.time)
    val weekdays = remember(locale, firstDay) {
        val names = DateFormatSymbols(locale).weekdays
        Array(7) { names[orbitWeekday(firstDay, it)].take(1).uppercase(locale) }
    }
    val description = remember(now, locale) {
        DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT, locale).format(now)
    }
    SideEffect {
        if (!animate || inspection) frameTime.doubleValue = now.time.toDouble()
    }
    LaunchedEffect(animate, lifecycle) {
        if (!animate || inspection) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                withFrameNanos { frameNanos ->
                    // Choreographer and nanoTime share a clock; retain sub-millisecond frame timing.
                    val sampleNanos = System.nanoTime()
                    frameTime.doubleValue = System.currentTimeMillis().toDouble() +
                        (frameNanos - sampleNanos) / 1_000_000.0
                }
            }
        }
    }

    Spacer(
        modifier.clipToBounds()
            .preferredFrameRate(if (animate && !inspection) 120f else 0f)
            .semantics { contentDescription = description }
            .drawWithCache {
                val scale = size.width / 480f
                val height = if (scale > 0f) size.height / scale else 0f
                val cx = 240f
                val cy = maxOf(240f, height * 0.30f)
                val radii = floatArrayOf(122f, 161f, 200f)
                val paths = Array(3) { index ->
                    Path().apply {
                        fillType = Path.FillType.EVEN_ODD
                        addCircle(cx, cy, radii[index] + 18f, Path.Direction.CW)
                        addCircle(cx, cy, radii[index] - 18f, Path.Direction.CW)
                    }
                }
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    typeface = font
                    textAlign = Paint.Align.CENTER
                    textSize = 23f
                }
                val weekdayBaselineOffset = -(paint.ascent() + paint.descent()) / 2f
                val destination = RectF()
                if (bitmap != null) {
                    val zoom = maxOf(480f / bitmap.width, height / bitmap.height)
                    val w = bitmap.width * zoom
                    val h = bitmap.height * zoom
                    destination.set((480f - w) / 2f, (height - h) / 2f,
                        (480f + w) / 2f, (height + h) / 2f)
                }
                val angles = FloatArray(3)
                onDrawBehind {
                    if (scale <= 0f || height <= 0f) return@onDrawBehind
                    // State reads belong here: a frame invalidates drawing, not composition or layout.
                    val localTime = frameTime.doubleValue + utcOffset
                    angles[0] = orbitAngle(localTime, 43_200_000.0)
                    angles[1] = orbitAngle(localTime, 60_000.0)
                    angles[2] = orbitAngle(localTime, 3_600_000.0)
                    val p = progress().coerceIn(0f, 1f)
                    val canvas = drawContext.canvas.nativeCanvas
                    canvas.save()
                    canvas.scale(scale, scale)
                    for (index in 0..2) {
                        val angle = angles[index] +
                            themePieceProgress(p, index * 0.025f) * (180f + index * 90f)
                        canvas.save()
                        canvas.clipPath(paths[index])
                        canvas.rotate(angle, cx, cy)
                        if (bitmap != null) {
                            paint.color = android.graphics.Color.WHITE
                            canvas.drawBitmap(bitmap, null, destination, paint)
                        }
                        canvas.restore()
                        paint.color = 0x26324F8A
                        canvas.drawPath(paths[index], paint)
                        val radians = Math.toRadians((angle - 90f).toDouble())
                        paint.color = android.graphics.Color.WHITE
                        canvas.drawCircle(cx + cos(radians).toFloat() * radii[index],
                            cy + sin(radians).toFloat() * radii[index], 20f, paint)
                    }
                    val weekY = cy + 280f
                    for (index in 0..6) {
                        val x = 40f + index * (400f / 6f)
                        val active = orbitWeekday(firstDay, index) == today
                        paint.color = android.graphics.Color.WHITE
                        if (active) canvas.drawCircle(x, weekY, 21f, paint)
                        paint.color = if (active) 0xFF234C76.toInt() else android.graphics.Color.WHITE
                        val baseline = weekY + weekdayBaselineOffset
                        canvas.drawText(weekdays[index], x, baseline, paint)
                    }
                    canvas.restore()
                }
            }
    )
}
