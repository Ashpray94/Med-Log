package com.suryaprakash.medlog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suryaprakash.medlog.data.Settings

/**
 * Design tokens. Calm, quiet surfaces; colour only where it means something.
 * Text contrast ≥ 7:1 for body text on cards and background.
 */
@Immutable
data class Palette(
    val paper: Color,       // screen background
    val card: Color,        // raised surfaces
    val ink: Color,         // primary text
    val inkSoft: Color,     // secondary text
    val line: Color,        // separators
    val fill: Color,        // quiet button fill
    val brand: Color,
    val onBrand: Color,
    val brandSoft: Color,
    val ok: Color,
    val okSoft: Color,
    val amber: Color,
    val amberSoft: Color,
    val red: Color,
    val redSoft: Color,
    val focus: Color,
    val figureBg: Color,
    // tile tints (icons on Home)
    val tintBlue: Color, val tintGreen: Color, val tintOrange: Color, val tintPurple: Color, val tintPink: Color, val tintTeal: Color,
)

val Warm = Palette(
    paper = Color(0xFFF4F2EE), card = Color(0xFFFFFFFF), ink = Color(0xFF1C1C1E), inkSoft = Color(0xFF55555B),
    line = Color(0xFFE6E3DE), fill = Color(0xFFEBE8E3), brand = Color(0xFF0B6E66), onBrand = Color.White, brandSoft = Color(0xFFE0F0EE),
    ok = Color(0xFF1E6B2A), okSoft = Color(0xFFE3F2E5), amber = Color(0xFF8A4B00), amberSoft = Color(0xFFFFF1D9),
    red = Color(0xFFC0271F), redSoft = Color(0xFFFCE9E7), focus = Color(0xFF2F5DA8), figureBg = Color(0xFFEDEAF6),
    tintBlue = Color(0xFF2F6FD6), tintGreen = Color(0xFF2E8B57), tintOrange = Color(0xFFE07A1F), tintPurple = Color(0xFF7C5CD6), tintPink = Color(0xFFD64F7A), tintTeal = Color(0xFF0B6E66),
)

val HighContrast = Warm.copy(
    paper = Color.White, card = Color.White, ink = Color.Black, inkSoft = Color(0xFF1A1A1A), line = Color.Black, fill = Color(0xFFE0E0E0),
    brand = Color(0xFF00332F), brandSoft = Color(0xFFD6ECE9), ok = Color(0xFF004D12), amber = Color(0xFF5C3100), red = Color(0xFF8C0000),
)

/**
 * Type and size scale. One scale for the whole app, so every screen has the same hierarchy:
 * largeTitle › title › headline › body › footnote. Big mode enlarges everything together.
 */
@Immutable
data class Scale(
    val huge: TextUnit,      // numbers, countdowns
    val title: TextUnit,     // screen title
    val headline: TextUnit,  // card and section titles
    val body: TextUnit,
    val button: TextUnit,
    val small: TextUnit,     // footnotes, labels under icons
    val target: Dp,          // minimum touch target
    val gap: Dp,             // space between blocks
    val margin: Dp,          // screen side margin
    val radius: Dp,
    val big: Boolean,
)

val Standard = Scale(huge = 44.sp, title = 32.sp, headline = 21.sp, body = 18.sp, button = 19.sp, small = 15.sp, target = 60.dp, gap = 16.dp, margin = 20.dp, radius = 20.dp, big = false)
val Big = Scale(huge = 54.sp, title = 38.sp, headline = 25.sp, body = 22.sp, button = 23.sp, small = 18.sp, target = 74.dp, gap = 18.dp, margin = 18.dp, radius = 22.dp, big = true)

/** The phone's own UI font: familiar, sharp at every size. */
val AppFont: FontFamily = FontFamily.Default
val Atkinson: FontFamily = AppFont

val LocalPalette = staticCompositionLocalOf { Warm }
val LocalScale = staticCompositionLocalOf { Standard }
val LocalSettings = staticCompositionLocalOf { Settings() }

@Composable
fun MedTheme(settings: Settings, content: @Composable () -> Unit) {
    val palette = if (settings.highContrast) HighContrast else Warm
    val scale = if (settings.bigMode) Big else Standard
    val weight = if (settings.boldText) FontWeight.Medium else FontWeight.Normal
    val base = TextStyle(fontFamily = AppFont, fontWeight = weight, color = palette.ink, fontSize = scale.body, lineHeight = scale.body * 1.4f)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = palette.brand, onPrimary = palette.onBrand, background = palette.paper, surface = palette.card,
            onBackground = palette.ink, onSurface = palette.ink, error = palette.red, outline = palette.line,
        ),
        typography = MaterialTheme.typography.copy(
            bodyLarge = base, bodyMedium = base, bodySmall = base.copy(fontSize = scale.small),
            titleLarge = base.copy(fontSize = scale.title, fontWeight = FontWeight.Bold, lineHeight = scale.title * 1.15f),
            labelLarge = base.copy(fontSize = scale.button, fontWeight = FontWeight.SemiBold),
        ),
    ) {
        CompositionLocalProvider(LocalPalette provides palette, LocalScale provides scale, LocalSettings provides settings) {
            androidx.compose.material3.ProvideTextStyle(base, content)
        }
    }
}
