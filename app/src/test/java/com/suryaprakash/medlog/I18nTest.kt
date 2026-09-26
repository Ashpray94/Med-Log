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
            for (k in o.keys()) assertEquals("${f.name}: $k", slot.findAll(k).map { it.value }.sorted().toList(), slot.findAll(o.getString(k)).map { it.value }.sorted().toList())
        }
    }
}
