package com.suryaprakash.medlog

import com.suryaprakash.medlog.ui.screens.UpdateSheet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The update sheet shows once in the phone's lifetime; Settings is the same page in both modes. */
class UpdateSheetTest {
    @Test fun theSheetIsShownOnceEver() {
        assertFalse(UpdateSheet.seen(null))
        assertTrue(UpdateSheet.seen("1"))
    }

    @Test fun homePagesNoLongerCarryTheLongUpdateCard() {
        val dir = File("src/main/java/com/suryaprakash/medlog/ui/screens").takeIf { it.exists() } ?: File("app/src/main/java/com/suryaprakash/medlog/ui/screens")
        for (f in listOf("HomeScreen.kt", "HelpScreens.kt")) {
            val t = File(dir, f).readText()
            assertFalse("$f still shows UpdateCard()", Regex("""(?<![A-Za-z])UpdateCard\(\)""").containsMatchIn(t))
            assertTrue("$f shows the one-time sheet", t.contains("UpdateSheetOnce()"))
        }
    }

    @Test fun settingsIsTheSamePageInBothModes() {
        val main = (File("src/main/java/com/suryaprakash/medlog/MainActivity.kt").takeIf { it.exists() } ?: File("app/src/main/java/com/suryaprakash/medlog/MainActivity.kt")).readText()
        assertTrue(main.contains("Route.Settings -> SettingsScreen(nav)"))
        assertFalse(main.contains("HelperSettingsScreen"))
    }
}
