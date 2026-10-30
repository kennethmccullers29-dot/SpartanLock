package com.ken1dot.spartanlock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ken1dot.spartanlock.SelawikFamily

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = SelawikFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)