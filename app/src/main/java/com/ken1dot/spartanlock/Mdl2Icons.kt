package com.ken1dot.spartanlock

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Fluent UI MDL2 glyphs, filled variants (Iconify fluent-mdl2, 2048 grid, MIT). Mail = mail-solid, Message = message-fill; MDL2 has no solid Phone/Calendar/Camera, so those are the outline glyph's outer contour filled (holes cut with EvenOdd). */
internal object Mdl2 {
    private fun glyph(name: String, d: String, fillType: PathFillType = PathFillType.NonZero) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 2048f, viewportHeight = 2048f
    ).addPath(addPathNodes(d), fill = SolidColor(Color.White), pathFillType = fillType).build()

    val Phone = glyph("PhoneSolid", "M1607 1213q44 0 84 16t72 48l220 220q31 31 47 71t17 85q0 44-16 84t-48 72l-14 14q-54 54-99 96t-94 70t-109 44t-143 15q-125 0-257-39t-262-108t-256-164t-237-207t-206-238t-162-256T38 775T0 523q0-83 14-142t43-108t70-93t96-99l16-16q31-31 71-48t85-17q44 0 84 17t72 48l220 220q31 31 47 71t17 85q0 44-15 78t-37 63t-48 51t-49 45t-37 44t-15 49q0 38 27 65l551 551q27 27 65 27q26 0 48-15t45-37t45-48t51-49t62-37t79-15z")
    val Mail = glyph("MailSolid", "M2048 384v85L1024 981L0 469v-85zm-1024 811l1024-512v981H0V683z")
    val Calendar = glyph("CalendarSolid", "M2048 128v1792H0V128h384V0h128v128h1024V0h128v128zM0 512h2048v128H0zM768 768h128v128H768zm384 768h128v128h-128zm384-768h128v128h-128zm-384 0h128v128h-128zm-384 256h128v128H768zm-384 0h128v128H384zm1152 0h128v128h-128zm-384 0h128v128h-128zm-384 256h128v128H768zm-384 0h128v128H384zm1152 0h128v128h-128zm-384 0h128v128h-128zm-384 256h128v128H768zm-384 0h128v128H384z", PathFillType.EvenOdd)
    val Message = glyph("MessageFill", "M2048 128v1408H731l-475 475v-475H0V128z")
    val Camera = glyph("CameraSolid", "M2048 256v1536H0V256h549l128-128h694l128 128zM1024 512q106 0 199 40t163 109t110 163t40 200t-40 199t-109 163t-163 110t-200 40t-199-40t-163-109t-110-163t-40-200t40-199t109-163t163-110t200-40m0 896q79 0 149-30t122-83t82-122t31-149q0-79-30-149t-83-122t-122-82t-149-31q-79 0-149 30t-122 83t-82 122t-31 149q0 79 30 149t83 122t122 82t149 31M320 512q26 0 45 19t19 45t-19 45t-45 19t-45-19t-19-45t19-45t45-19", PathFillType.EvenOdd)
    val Search = glyph("Search", "M1344 0q97 0 187 25t168 71t142 110t111 143t71 168t25 187q0 97-25 187t-71 168t-110 142t-143 111t-168 71t-187 25q-125 0-239-42t-211-121l-785 784q-19 19-45 19t-45-19t-19-45t19-45l784-785q-79-96-121-210t-42-240q0-97 25-187t71-168t110-142T989 96t168-71t187-25m0 1280q119 0 224-45t183-124t123-183t46-224q0-119-45-224t-124-183t-183-123t-224-46q-119 0-224 45T937 297T814 480t-46 224q0 119 45 224t124 183t183 123t224 46")

    val Backspace = glyph("Back", "M2048 1088H250l787 787l-90 90L6 1024L947 83l90 90l-787 787h1798z")

    /** Maps a notifying app to its W10M lock-screen glyph, or null for app icon fallback. */
    fun forPackage(pkg: String): ImageVector? {
        val p = pkg.lowercase()
        return when {
            "dialer" in p || "telecom" in p || "incallui" in p || "spartanphone" in p || p.endsWith(".phone") -> Phone
            "messag" in p || "mms" in p || "sms" in p || "spartanmessag" in p -> Message
            "mail" in p || "gm" == p.substringAfterLast('.') || "outlook" in p -> Mail
            "calendar" in p -> Calendar
            else -> null
        }
    }
}