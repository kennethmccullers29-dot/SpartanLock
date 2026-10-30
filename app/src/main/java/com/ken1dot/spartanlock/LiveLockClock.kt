package com.ken1dot.spartanlock

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.res.ResourcesCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

internal enum class LockTheme(val id: String, val title: String, val detail: String) {
    CLASSIC("classic", "Classic", "The original Spartan lock screen"),
    DIAGONAL("diagonal", "Diagonal", "Time and date slide away in opposite directions"),
    BOLD("bold", "Bold", "Bright yellow time with a bold day and date"),
    SPLIT("split", "Split", "A clock tucked into the detail of your wallpaper"),
    ORBIT("orbit", "Orbit", "Three rotating wallpaper rings tell the time"),
    WORDS("words", "Words", "Time and date written out, line by line"),
    MINIMAL("minimal", "Minimal", "Fine typography with a compact day and date");

    val hasSpecialUnlock: Boolean get() = this == DIAGONAL || this == ORBIT

    companion object {
        fun fromStored(id: String?, legacyDiagonal: Boolean = false): LockTheme =
            if (id == null && legacyDiagonal) DIAGONAL
            else entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

internal fun liveLockSwipeProgress(offset: Float, height: Float): Float =
    if (height > 0f) (-offset / height).coerceIn(0f, 1f) else 0f

internal fun numberWords(number: Int): String {
    require(number in 0..59)
    val small = listOf("zero", "one", "two", "three", "four", "five", "six", "seven",
        "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
        "sixteen", "seventeen", "eighteen", "nineteen")
    if (number < 20) return small[number]
    val tens = listOf("", "", "twenty", "thirty", "forty", "fifty")[number / 10]
    return tens + if (number % 10 == 0) "" else "-" + small[number % 10]
}

internal fun ordinalWords(day: Int): String {
    require(day in 1..31)
    val small = listOf("", "first", "second", "third", "fourth", "fifth", "sixth",
        "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth", "thirteenth",
        "fourteenth", "fifteenth", "sixteenth", "seventeenth", "eighteenth", "nineteenth")
    if (day < 20) return small[day]
    if (day == 20) return "twentieth"
    if (day == 30) return "thirtieth"
    return numberWords(day / 10 * 10) + "-" + small[day % 10]
}

/** Local progress makes each piece leave at a different point in the same swipe. */
internal fun themePieceProgress(progress: Float, delay: Float): Float =
    ((progress - delay) / (0.55f - delay)).coerceIn(0f, 1f)

/**
 * Reference geometry uses the Windows Phone 480-wide viewport. The same renderer
 * draws the full lock screen and the gallery, so previews cannot drift from it.
 */
@Composable
internal fun LiveLockClock(
    theme: LockTheme,
    now: Date,
    use24Hour: Boolean,
    progress: () -> Float,
    wallpaper: ImageBitmap?,
    modifier: Modifier = Modifier,
    bottomClearance: Float = 0f,
    animate: Boolean = true
) {
    if (theme == LockTheme.ORBIT) {
        OrbitClock(now, wallpaper, progress, animate, modifier)
        return
    }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val light = remember(context) { ResourcesCompat.getFont(context, R.font.selawik_light)!! }
    val regular = remember(context) { ResourcesCompat.getFont(context, R.font.selawik_regular)!! }
    val bold = remember(context) { ResourcesCompat.getFont(context, R.font.selawik_bold)!! }
    val serif = remember { Typeface.create("serif", Typeface.BOLD) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val time = remember(now, use24Hour, locale) {
        SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm", locale).format(now)
    }
    val day = remember(now, locale) { SimpleDateFormat("EEEE", locale).format(now) }
    val date = remember(now, locale) { SimpleDateFormat("MMMM d", locale).format(now) }
    val shortDate = remember(now, locale) {
        SimpleDateFormat("EEE M/d", locale).format(now).uppercase(locale)
    }
    val calendar = remember(now) { Calendar.getInstance().apply { this.time = now } }
    val wordLines = remember(now, use24Hour, locale) {
        val hour = if (use24Hour) calendar.get(Calendar.HOUR_OF_DAY)
            else calendar.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        val english = locale.language == "en"
        fun number(value: Int) = if (english) numberWords(value) else String.format(locale, "%d", value)
        val ordinal = if (english) ordinalWords(calendar.get(Calendar.DAY_OF_MONTH))
            else number(calendar.get(Calendar.DAY_OF_MONTH))
        listOf(
            number(hour),
            if (calendar.get(Calendar.MINUTE) == 0 && locale.language == "en") "o'clock"
                else number(calendar.get(Calendar.MINUTE)),
            day + if (locale.language == "en") " the" else "",
            ordinal + if (locale.language == "en") " of" else "",
            SimpleDateFormat("MMMM", locale).format(now)
        )
    }
    val bitmap = remember(wallpaper) { wallpaper?.asAndroidBitmap() }
    val foreground = rememberSplitClockMask(bitmap, theme == LockTheme.SPLIT)
    val maskPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
    }

    Canvas(modifier.clipToBounds().semantics { contentDescription = "$time, $day, $date" }) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val canvas = drawContext.canvas.nativeCanvas
        val scale = size.width / 480f
        val height = size.height / scale
        val p = progress().coerceIn(0f, 1f)
        val first = themePieceProgress(p, 0f)
        val second = themePieceProgress(p, 0.04f)
        canvas.save()
        canvas.scale(scale, scale)
        fun text(
            value: String, x: Float, y: Float, fontSize: Float, font: Typeface = light,
            color: Int = android.graphics.Color.WHITE, align: Paint.Align = Paint.Align.LEFT,
            alpha: Float = 1f
        ) {
            paint.typeface = font
            // Keep unusually long localized dates within the viewport.
            paint.textSize = fontSize
            val width = paint.measureText(value)
            if (width > 448f) paint.textSize *= 448f / width
            paint.color = color
            paint.alpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
            paint.textAlign = align
            canvas.drawText(value, x, y, paint)
            paint.alpha = 255
        }
        when (theme) {
            LockTheme.DIAGONAL -> {
                canvas.save()
                canvas.translate(-560f * first, 60f * first)
                canvas.rotate(-50f, 240f, height * 0.40f)
                text(time, 240f, height * 0.40f + 36f, 132f, bold, align = Paint.Align.CENTER)
                canvas.restore()
                canvas.save()
                canvas.translate(560f * second, 30f * second)
                canvas.rotate(24f, 260f, height * 0.57f)
                text(day, 260f, height * 0.57f, 29f, bold, align = Paint.Align.CENTER)
                text(date, 260f, height * 0.57f + 33f, 29f, bold, align = Paint.Align.CENTER)
                canvas.restore()
            }
            LockTheme.BOLD -> {
                val yellow = 0xFFE4F332.toInt()
                text(time, 22f, height * 0.67f, 132f, bold, yellow)
                text(day, 24f, height * 0.75f, 39f, serif, yellow)
                text(date, 24f, height * 0.75f + 44f, 39f, serif, yellow)
            }
            LockTheme.SPLIT -> {
                val pieces = time.split(':')
                val minuteWidth = run {
                    paint.typeface = light
                    paint.textSize = 142f
                    paint.measureText(pieces[1])
                }
                val minutesX = 462f - minuteWidth
                val baseline = height * 0.54f
                val layer = if (foreground != null)
                    canvas.saveLayer(0f, baseline - 160f, 480f, baseline + 8f, null) else -1
                text(pieces[0] + ":", minutesX - 18f,
                    height * 0.54f, 142f, align = Paint.Align.RIGHT)
                text(pieces[1], 462f, height * 0.54f, 142f,
                    align = Paint.Align.RIGHT)
                if (foreground != null && bitmap != null) {
                    val zoom = maxOf(480f / bitmap.width, height / bitmap.height)
                    val width = bitmap.width * zoom
                    val imageHeight = bitmap.height * zoom
                    canvas.drawBitmap(foreground, null,
                        RectF((480f - width) / 2f, (height - imageHeight) / 2f,
                            (480f + width) / 2f, (height + imageHeight) / 2f), maskPaint)
                }
                if (layer >= 0) canvas.restoreToCount(layer)
                text(day, 458f, height * 0.54f + 48f, 34f, regular,
                    align = Paint.Align.RIGHT)
                text(date, 458f, height * 0.54f + 85f, 34f, regular,
                    align = Paint.Align.RIGHT)
            }
            LockTheme.ORBIT -> Unit
            LockTheme.WORDS -> {
                wordLines.forEachIndexed { index, line ->
                    text(line, 20f,
                        height - 298f - bottomClearance + index * 39f, 38f, bold)
                }
            }
            LockTheme.MINIMAL -> {
                text(time, 20f, height - 170f - bottomClearance, 148f)
                text(shortDate, 24f, height - 111f - bottomClearance, 42f, regular)
            }
            LockTheme.CLASSIC -> {
                text(time, 30f, height - 240f, 98f)
                text(SimpleDateFormat("EEEE d MMMM", locale).format(now),
                    30f, height - 192f, 34f)
            }
        }
        canvas.restore()
    }
}
