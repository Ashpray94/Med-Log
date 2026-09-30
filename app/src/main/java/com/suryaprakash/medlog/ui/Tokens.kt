package com.suryaprakash.medlog.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Hospital-sign design tokens. See docs/DESIGN.md, "Hospital-sign + Apple HIG rules". */
object Hs {
    // colour: every text/background pair here is at least 7:1
    val Ink = Color(0xFF0B0F14)
    val Paper = Color(0xFFFFFFFF)
    val Blue = Color(0xFF0057B8)
    val Green = Color(0xFF007A3D)
    val Red = Color(0xFFC8102E)
    val Amber = Color(0xFFB26A00)

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
