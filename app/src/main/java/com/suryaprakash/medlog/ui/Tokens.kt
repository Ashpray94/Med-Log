package com.suryaprakash.medlog.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Hospital-sign design tokens. See docs/DESIGN.md, "Hospital-sign + Apple HIG rules". */
object Hs {
    // colour: every text/background pair here is at least 7:1
    val Ink = Color(0xFF0B0F14)
    val Paper = Color(0xFFFFFFFF)
    val Blue = Color(0xFF004C99)
    val Green = Color(0xFF00622F)
    val Red = Color(0xFFA50E26)
    val Amber = Color(0xFF7A4A00)

    // size
    val TargetMin = 48.dp
    val TargetPrimary = 56.dp
    val Radius = 12.dp
    val Gutter = 16.dp

    // type
    val Body = 18.sp
    val Label = 16.sp
    val Title = 24.sp
    val Headline = 28.sp
}

/** Senior-friendly sizing and typography for patient screens. */
object Senior {
    val Body = 20.sp
    val Label = 16.sp
    val Main = 88.dp
    val Target = 56.dp
    val Gap = 12.dp
    val HeroIcon = 64.dp
}
