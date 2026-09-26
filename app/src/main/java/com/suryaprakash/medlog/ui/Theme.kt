package com.suryaprakash.medlog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * 60 / 30 / 10 (docs/DESIGN.md).
 * 60 — white and greys: a light grey page, white cards lifted by a soft shadow, near-black text.
 * 30 — the primary teal: the one main action on a page, the hero card, chosen things and the current tab.
 * 10 — accents: each feature's own icon colour (Medicines orange, Food green, Readings pink, Doctor blue,
 *      My health purple, History teal) and the status colours (red urgent/SOS, amber watch, green OK).
 */
val Warm = Palette(
    paper = Color(0xFFF2F2F0), card = Color(0xFFFFFFFF), ink = Color(0xFF141414), inkSoft = Color(0xFF45454A),
    line = Color(0xFFDDDDD8), fill = Color(0xFFE9E9E5), brand = Color(0xFF0A6B63), onBrand = Color.White, brandSoft = Color(0xFFDDEFEC),
    ok = Color(0xFF1B6B2E), okSoft = Color(0xFFE2F2E6), amber = Color(0xFF8A4B00), amberSoft = Color(0xFFFFF0D6),
    red = Color(0xFFC0271F), redSoft = Color(0xFFFCE8E5), focus = Color(0xFF2F5DA8), figureBg = Color(0xFFFFFFFF),
    tintBlue = Color(0xFF2266DD), tintGreen = Color(0xFF1E9150), tintOrange = Color(0xFFEA7310), tintPurple = Color(0xFF7447D6), tintPink = Color(0xFFD9406F), tintTeal = Color(0xFF0E857B),
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
    val question: TextUnit,  // the question on a question page: always the largest words
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

val Standard = Scale(huge = 44.sp, question = 30.sp, title = 28.sp, headline = 21.sp, body = 18.sp, button = 19.sp, small = 16.sp, target = 60.dp, gap = 16.dp, margin = 20.dp, radius = 20.dp, big = false)
val Big = Scale(huge = 54.sp, question = 36.sp, title = 33.sp, headline = 25.sp, body = 22.sp, button = 23.sp, small = 19.sp, target = 74.dp, gap = 18.dp, margin = 18.dp, radius = 22.dp, big = true)

/** The phone's own UI font: familiar, sharp at every size. */
val AppFont: FontFamily = FontFamily.Default
val Atkinson: FontFamily = AppFont

val LocalPalette = staticCompositionLocalOf { Warm }
val LocalScale = staticCompositionLocalOf { Standard }
val LocalSettings = staticCompositionLocalOf { Settings() }

@Composable
fun MedTheme(settings: Settings, content: @Composable () -> Unit) {
    val palette = if (settings.highContrast) HighContrast else Warm
    // Large words: the whole layout (words, spacing, buttons) grows together by about 1.2x, so it stays in proportion
    // and never gets crowded; the choice shows first, then after a short pause the change eases in over 0.6 s
    val scale = Standard
    val zoom by androidx.compose.animation.core.animateFloatAsState(if (settings.bigMode) 1.2f else 1f,
        androidx.compose.animation.core.tween(if (settings.lessMotion) 0 else 600, delayMillis = if (settings.lessMotion) 0 else 250), label = "zoom")
    val density = androidx.compose.ui.platform.LocalDensity.current
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
        CompositionLocalProvider(LocalPalette provides palette, LocalScale provides scale, LocalSettings provides settings,
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density * zoom, density.fontScale)) {
            androidx.compose.material3.ProvideTextStyle(base, content)
        }
    }
}
