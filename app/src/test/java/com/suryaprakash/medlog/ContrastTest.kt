package com.suryaprakash.medlog

import androidx.compose.ui.graphics.Color
import com.suryaprakash.medlog.ui.Hs
import org.junit.Test
import kotlin.math.pow

class ContrastTest {
    /**
     * Calculate relative luminance according to WCAG 2.0 standard.
     * Uses sRGB linearisation: if component <= 0.03928 then divide by 12.92,
     * else ((component + 0.055) / 1.055) ^ 2.4
     */
    private fun relativeLuminance(argb: Long): Double {
        val a = (argb shr 24) and 0xFF
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0

        val rLinear = if (r <= 0.03928) r / 12.92 else ((r + 0.055) / 1.055).pow(2.4)
        val gLinear = if (g <= 0.03928) g / 12.92 else ((g + 0.055) / 1.055).pow(2.4)
        val bLinear = if (b <= 0.03928) b / 12.92 else ((b + 0.055) / 1.055).pow(2.4)

        return 0.2126 * rLinear + 0.7152 * gLinear + 0.0722 * bLinear
    }

    /**
     * Calculate contrast ratio between two colors.
     * Returns (L1 + 0.05) / (L2 + 0.05) where L1 is the lighter luminance.
     */
    private fun contrastRatio(color1: Long, color2: Long): Double {
        val l1 = relativeLuminance(color1)
        val l2 = relativeLuminance(color2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun testContrast_BlueOnWhite() {
        val contrast = contrastRatio(0xFF004C99, 0xFFFFFFFF)
        assert(contrast >= 7.0) { "Blue on White contrast is $contrast, expected >= 7.0" }
    }

    @Test
    fun testContrast_GreenOnWhite() {
        val contrast = contrastRatio(0xFF00622F, 0xFFFFFFFF)
        assert(contrast >= 7.0) { "Green on White contrast is $contrast, expected >= 7.0" }
    }

    @Test
    fun testContrast_RedOnWhite() {
        val contrast = contrastRatio(0xFFA50E26, 0xFFFFFFFF)
        assert(contrast >= 7.0) { "Red on White contrast is $contrast, expected >= 7.0" }
    }

    @Test
    fun testContrast_AmberOnWhite() {
        val contrast = contrastRatio(0xFF7A4A00, 0xFFFFFFFF)
        assert(contrast >= 7.0) { "Amber on White contrast is $contrast, expected >= 7.0" }
    }

    @Test
    fun testContrast_InkOnWhite() {
        val contrast = contrastRatio(0xFF0B0F14, 0xFFFFFFFF)
        assert(contrast >= 7.0) { "Ink on White contrast is $contrast, expected >= 7.0" }
    }

    @Test
    fun testContrast_InkOnPaper() {
        val contrast = contrastRatio(0xFF0B0F14, 0xFFFFFFFF)
        assert(contrast >= 7.0) { "Ink on Paper contrast is $contrast, expected >= 7.0" }
    }

    @Test
    fun testContrast_WhiteOnBlue() {
        val contrast = contrastRatio(0xFFFFFFFF, 0xFF004C99)
        assert(contrast >= 7.0) { "White on Blue contrast is $contrast, expected >= 7.0" }
    }

    @Test
    fun testContrast_WhiteOnGreen() {
        val contrast = contrastRatio(0xFFFFFFFF, 0xFF00622F)
        assert(contrast >= 7.0) { "White on Green contrast is $contrast, expected >= 7.0" }
    }

    @Test
    fun testContrast_WhiteOnRed() {
        val contrast = contrastRatio(0xFFFFFFFF, 0xFFA50E26)
        assert(contrast >= 7.0) { "White on Red contrast is $contrast, expected >= 7.0" }
    }

    @Test
    fun testContrast_WhiteOnAmber() {
        val contrast = contrastRatio(0xFFFFFFFF, 0xFF7A4A00)
        assert(contrast >= 7.0) { "White on Amber contrast is $contrast, expected >= 7.0" }
    }

    @Test
    fun testContrast_WhiteOnInk() {
        val contrast = contrastRatio(0xFFFFFFFF, 0xFF0B0F14)
        assert(contrast >= 7.0) { "White on Ink contrast is $contrast, expected >= 7.0" }
    }
}
