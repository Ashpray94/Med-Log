package com.suryaprakash.medlog

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The colour rules, checked in the source so a slip fails the build:
 * green only ever means "done" (never an action button), and each mode's accent comes from the palette,
 * never typed in, so helping is blue everywhere and my health teal everywhere.
 */
class ColourRulesTest {
    private val src = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test fun sourceIsFound() = assertTrue("source not found", src.size > 50)

    @Test fun noGreenActionButtons() {
        val bad = src.filter { it.name != "Components.kt" }.flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if ("Tone.OK" in l) "${f.name}:${i + 1}" else null } }
        assertTrue("Green is for done, not for buttons: $bad", bad.isEmpty())
    }

    @Test fun accentsComeFromThePalette() {
        val typed = listOf("0xFF0A6B63", "0xFF0B6E66", "0xFF33589E", "0xFF1E9150", "0xFF0E857B", "0xFF7DD3C8")
        val bad = src.filter { it.name != "Theme.kt" }.flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (typed.any { it in l }) "${f.name}:${i + 1}" else null } }
        assertTrue("Mode colours typed in outside the theme: $bad", bad.isEmpty())
    }
}
