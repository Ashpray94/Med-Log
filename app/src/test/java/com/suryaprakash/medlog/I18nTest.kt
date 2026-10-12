package com.suryaprakash.medlog

import com.suryaprakash.medlog.speech.I18n
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class I18nTest {
    @After fun reset() = I18n.useMap("en", emptyMap())

    @Test fun exactAndPatterns() {
        I18n.useMap("ta", mapOf(
            "Call {0}" to "{0}-ஐ அழையுங்கள்",
            "{1} in {0}" to "{0} இல் {1}",
            "Fever" to "காய்ச்சல்",
            "Is your {0} better now?" to "உங்கள் {0} இப்போது சரியாகிவிட்டதா?",
        ))
        assertEquals("காய்ச்சல்", I18n.tr("Fever"))
        assertEquals("  காய்ச்சல்", I18n.tr("  Fever"))
        assertEquals("Ravi-ஐ அழையுங்கள்", I18n.tr("Call Ravi"))
        // slots can change order between languages
        assertEquals("5 இல் Meera", I18n.tr("Meera in 5"))
        // a problem name inside a pattern is translated too
        assertEquals("உங்கள் காய்ச்சல் இப்போது சரியாகிவிட்டதா?", I18n.tr("Is your fever better now?"))
        // nothing known: English stays
        assertEquals("Something new", I18n.tr("Something new"))
    }

    @Test fun englishIsUntouched() {
        I18n.useMap("en", emptyMap())
        assertEquals("Call Ravi", I18n.tr("Call Ravi"))
    }

    /** Every shipped language keeps every {0} slot of the English line. */
    @Test fun shippedFilesKeepSlots() {
        val dir = listOf(File("src/main/assets/i18n"), File("app/src/main/assets/i18n")).firstOrNull { it.isDirectory } ?: return
        val slot = Regex("\\{\\d\\}")
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val o = JSONObject(f.readText())
            for (k in o.keys()) {
                // a plural ending ("item{1}") may be left out: Hindi and Tamil don't add an "s"
                val plural = Regex("[A-Za-z](\\{\\d\\})").findAll(k).map { it.groupValues[1] }.toSet()
                val want = slot.findAll(k).map { it.value }.toSet()
                val got = slot.findAll(o.getString(k)).map { it.value }.toSet()
                assertTrue("${f.name}: $k has a slot the English doesn't", want.containsAll(got))
                assertTrue("${f.name}: $k drops a slot that isn't a plural ending", plural.containsAll(want - got))
            }
        }
    }

    /** "1 item" and "3 items" both match "{0} item{1}", whose translation needs no plural ending. */
    @Test fun pluralEndings() {
        I18n.useMap("hi", mapOf("{0} item{1}" to "{0} चीज़ें"))
        assertEquals("1 चीज़ें", I18n.tr("1 item"))
        assertEquals("3 चीज़ें", I18n.tr("3 items"))
    }
}
