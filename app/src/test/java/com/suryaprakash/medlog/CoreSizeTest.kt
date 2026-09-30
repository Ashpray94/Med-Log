package com.suryaprakash.medlog

import com.suryaprakash.medlog.clinical.Catalogue
import com.suryaprakash.medlog.clinical.Interview
import org.junit.Test
import java.io.File

class CoreSizeTest {
    private val cat = Catalogue.parse(File("src/main/assets/clinical/catalogue.json").readText())

    // Severity-related ask ids from Interview.kt
    private val SEVERITY_ASK_IDS = setOf("severity", "pain", "itch", "strength")

    // Problem ids that don't need severity (from Interview.kt)
    private val NO_SEVERITY = setOf("fainted", "fits", "fall", "near_fall", "choking", "sneeze", "burp", "hiccup", "black_stool", "blood_stool",
        "blood_urine", "high_bp", "low_bp", "low_sugar", "high_sugar", "low_oxygen", "self_harm", "confusion", "memory")

    @Test
    fun coreQuestionsAreAtMost5AndContainSeverity() {
        val failedProblems = mutableListOf<String>()

        for (p in cat.problems) {
            val coreAsks = Interview.core(cat, p, emptyMap())

            // Check size <= 5
            if (coreAsks.size > 5) {
                failedProblems.add("${p.id}: core size ${coreAsks.size} exceeds 5")
            }

            // Check no core ask has non-null gate
            for (ask in coreAsks) {
                if (ask.gate != null) {
                    failedProblems.add("${p.id}: core ask '${ask.id}' has a non-null gate")
                }
            }

            // Check severity is included (unless in NO_SEVERITY)
            if (p.id !in NO_SEVERITY) {
                val hasSeverity = coreAsks.any { it.id in SEVERITY_ASK_IDS || it.field == "severity" }
                if (!hasSeverity) {
                    failedProblems.add("${p.id}: missing severity ask in core")
                }
            }
        }

        if (failedProblems.isNotEmpty()) {
            throw AssertionError("Core size test failures:\n${failedProblems.joinToString("\n")}")
        }
    }
}
