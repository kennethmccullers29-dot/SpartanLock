package com.ken1dot.spartanlock

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Microsoft Selawik — metric-compatible open Segoe UI alternative (OFL). */
internal val SelawikFamily = FontFamily(
    Font(R.font.selawik_light, FontWeight.Thin),
    Font(R.font.selawik_light, FontWeight.ExtraLight),
    Font(R.font.selawik_light, FontWeight.Light),
    Font(R.font.selawik_semilight, FontWeight(350)),
    Font(R.font.selawik_regular, FontWeight.Normal),
    Font(R.font.selawik_semibold, FontWeight.Medium),
    Font(R.font.selawik_semibold, FontWeight.SemiBold),
    Font(R.font.selawik_bold, FontWeight.Bold),
)
