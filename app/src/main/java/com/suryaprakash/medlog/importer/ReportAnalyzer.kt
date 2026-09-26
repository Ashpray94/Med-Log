package com.suryaprakash.medlog.importer

import java.time.LocalDate

/**
 * Reads the text of an old hospital report or prescription and pulls out dated findings,
 * medicine lines and the patient name. Ported from MedLog v1 (src/utils/reportAnalyzer.ts), whose rules
 * were tuned on real Indian lab, radiology and discharge layouts. Retrieval only: it copies lines that are
 * in the report and never writes new text.
 */
object ReportAnalyzer {
    data class Finding(val content: String, val date: LocalDate?)
    data class Result(val patientName: String?, val medicines: List<String>, val findings: List<Finding>, val lines: List<String>)

    private val MONTHS = mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12)

    private val OBSERVATION_LABELS = setOf(
        "diagnosis", "impression", "finding", "findings", "observation", "observations", "assessment", "history", "chief complaint",
        "complaint", "complaints", "symptom", "symptoms", "clinical summary", "summary", "conclusion", "plan", "final impression",
        "radiology", "advice", "discharge summary", "course in hospital", "provisional diagnosis", "clinical notes",
    )
    private val MED_SECTIONS = setOf("medication", "medications", "prescription", "prescriptions", "rx", "medicines", "treatment", "discharge medications", "advice on discharge")
    private val LAB_SECTIONS = setOf("laboratory report", "complete blood count (cbc)", "liver function tests (lft)", "prothrombin time", "hemoglobin",
        "creatinine", "uric acid", "blood urea", "thyroid stimulating hormone (tsh)", "lipid profile", "renal function test", "kidney function test", "hba1c")

    private val MED_FORM = Regex("\\b(tab|tablet|cap|capsule|syp|syrup|inj|injection|drop|drops|cream|ointment|inhaler)\\b", RegexOption.IGNORE_CASE)
    private val DOSE = Regex("\\b\\d+(?:\\.\\d+)?\\s?(mg|mcg|g|ml|iu)\\b(?!\\s*/\\s*(dl|l))", RegexOption.IGNORE_CASE)
    private val INSTRUCTION = Regex("\\b\\d-\\d-\\d\\b|\\bafter food\\b|\\bbefore food\\b|\\btill review\\b|\\bp/o\\b|\\bi\\.v\\b|\\bod\\b|\\bbd\\b|\\btds\\b|\\bhs\\b|\\bsos\\b", RegexOption.IGNORE_CASE)
    private val LAB_UNITS = Regex("mg/dl|u/l|ng/dl|g/dl|mmol/l|sec|fl|pg|cells/cu\\.?mm|iu/ml|unit\\(s\\)", RegexOption.IGNORE_CASE)

    private val FINDING_WORDS = listOf("noted", "narrowing", "stricture", "growth", "mass", "lesion", "suggestive", "appears", "appearance",
        "infiltration", "metastasis", "metastases", "lymphnode", "lymphnodes", "thyroiditis", "consolidation", "collection", "perforation",
        "osteopenia", "degenerative", "dysphagia", "obstruction", "biopsy", "ulceroproliferative", "circumferential", "contained",
        "hypermetabolic", "irregular", "diffuse", "enlarged", "effusion", "fracture", "calculus", "stone", "cyst", "nodule", "thickening",
        "infarct", "ischemic", "hypertrophy", "stenosis", "regurgitation", "fatty", "cardiomegaly", "diagnosed", "known case", "k/c/o", "c/o")

    private val BOILERPLATE = listOf(
        "^page\\s+\\d+", "^patient\\s+name\\s*:", "^name\\s*:", "^patient\\s*:", "^pt\\.\\s*name", "^client\\s+name", "^patient\\s+id", "^visit\\s+id",
        "^sample\\s+id", "^age\\s*:", "^age/gender", "^sex/age", "^sex\\s*:", "^gender\\s*:", "^dob\\s*:", "^date\\s+of\\s+birth\\s*:", "^phone\\s*:",
        "^email\\s*:", "^address\\s*:", "^reference\\s*(no|number)?\\s*:", "^report\\s*(id|no|number)?\\s*:", "^lab\\s*name\\s*:", "^hospital\\s*:",
        "^referred\\s+by", "^consulted\\s+by", "^methodology", "^disclaimer", "^for appointments", "^for emergency", "^printed on", "^reported on",
        "^received on", "^\\*+end of report\\*+$", "^tests marked with nabl", "^observed value", "^biological reference interval", "^test description",
        "^laboratory report", "^department of laboratory services", "^radiologists$", "^consultant\\b", "^regno", "^uhid",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    fun analyze(raw: List<String>, importedOn: LocalDate = LocalDate.now()): Result {
        val lines = raw.map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }.distinct()
        val meds = LinkedHashSet<String>()
        val found = ArrayList<Triple<String, LocalDate?, Int>>()
        val seen = HashSet<String>()
        var name: String? = null
        var section: String? = null
        var lastDate: LocalDate? = null

        lines.forEachIndexed { index, line ->
            if (hardBreak(line)) { section = null; return@forEachIndexed }
            parseDate(line)?.let { lastDate = it }
            if (name == null) name = patientName(line)
            detectSection(line)?.let { sec ->
                section = sec
                if (!line.contains(':') && !line.contains('-')) return@forEachIndexed
            }
            if (isMedicineLine(line) || (section == "med" && MED_FORM.containsMatchIn(line))) { meds += line; return@forEachIndexed }
            val prev = found.lastOrNull()
            if (prev != null && section != "med" && mergeWithPrevious(prev.first, line, prev.third, index)) {
                found[found.lastIndex] = Triple("${prev.first} ${line.removePrefix("•").removePrefix("-").trim()}".replace(Regex("\\s+"), " "), prev.second, index)
                return@forEachIndexed
            }
            if (!capture(line, section, parseDate(line) != null)) return@forEachIndexed
            if (!seen.add(line)) return@forEachIndexed
            found += Triple(line, lastDate, index)
        }
        return Result(name, meds.toList(), found.map { Finding(it.first, it.second) }, lines)
    }

    private fun hardBreak(l: String) = Regex("^--\\s*\\d+\\s+of\\s+\\d+\\s*--$", RegexOption.IGNORE_CASE).matches(l) ||
        Regex("department of laboratory services|^laboratory report$|^test description$|^methodology$", RegexOption.IGNORE_CASE).containsMatchIn(l)

    private fun detectSection(l: String): String? {
        val n = l.trim().lowercase().replace(Regex("[:\\-]\\s*$"), "")
        return when { n in OBSERVATION_LABELS -> "obs"; n in MED_SECTIONS -> "med"; n in LAB_SECTIONS -> "lab"; else -> null }
    }

    fun patientName(l: String): String? {
        val m = Regex("^(patient\\s+name|name|patient)\\s*[:\\-]\\s*(.+)$", RegexOption.IGNORE_CASE).find(l) ?: return null
        val v = m.groupValues[2]
            .replace(Regex("sample\\s+collected.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("reported\\s+on.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("age/gender.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}.*$"), "")
            .replace(Regex("\\s{2,}.*"), "").trim()
        return v.takeIf { it.isNotEmpty() && !it.any(Char::isDigit) }
    }

    fun isMedicineLine(l: String): Boolean {
        if (LAB_UNITS.containsMatchIn(l) && !MED_FORM.containsMatchIn(l)) return false
        return MED_FORM.containsMatchIn(l) && (DOSE.containsMatchIn(l) || INSTRUCTION.containsMatchIn(l)) && Regex("[a-z]{3,}", RegexOption.IGNORE_CASE).containsMatchIn(l)
    }

    private fun labRow(l: String): Boolean {
        val lower = l.lowercase()
        val digits = l.count(Char::isDigit)
        val words = l.split(" ").count { it.isNotBlank() }
        if (Regex("observed value|biological reference interval|sample type|laboratory report|methodology|received on|reported on").containsMatchIn(lower)) return true
        if (digits >= 6 && LAB_UNITS.containsMatchIn(lower)) return true
        return words <= 6 && digits >= 3
    }

    private fun narrative(l: String): Boolean {
        val c = l.removePrefix("•").removePrefix("-").trim()
        val lower = c.lowercase()
        val words = c.split(" ").count { it.isNotBlank() }
        val digits = c.count(Char::isDigit)
        val letters = c.count(Char::isLetter)
        if (words < 4 || labRow(c)) return false
        if (digits > 0 && letters / maxOf(digits, 1) < 3) return false
        if (Regex("^(page|visit id|sample id|patient id|uhid|date|methodology|referred by|consulted by|sex/age|age/gender|disclaimer|for appointments|for emergency|radiologists|consultant)\\b").containsMatchIn(lower)) return false
        if (Regex("discla|guidance|physician'?s advice|clinical correlation|variation in context|clinical status").containsMatchIn(lower)) return false
        if (Regex("appears? normal|no significant abnormality|physiological fdg distribution").containsMatchIn(lower)) return false
        if (FINDING_WORDS.any { lower.contains(it) }) return true
        return Regex("^[A-Za-z][A-Za-z\\s'()/-]{2,30}\\s*:\\s*[A-Za-z].+$").matches(c)
    }

    private fun labObservation(l: String) = l.any(Char::isLetter) && Regex("\\b\\d+(?:\\.\\d+)?\\b").containsMatchIn(l) && LAB_UNITS.containsMatchIn(l) &&
        !Regex("sample type|reference interval|methodology|reported on|received on|printed on", RegexOption.IGNORE_CASE).containsMatchIn(l)

    private fun capture(l: String, section: String?, hasDate: Boolean): Boolean {
        val lower = l.lowercase()
        if (l.length < 8 || l.length > 220) return false
        if (BOILERPLATE.any { it.containsMatchIn(l) }) return false
        if (lower == "normal" || labRow(l) && section != "lab") return false
        if (section == "lab") return labObservation(l)
        if (section == "obs") return narrative(l)
        if (OBSERVATION_LABELS.any { lower.startsWith("$it:") || lower.startsWith("$it -") }) return narrative(l)
        if (l.startsWith("•") || l.startsWith("-")) return narrative(l)
        if (hasDate && narrative(l)) return true
        return Regex("^[A-Za-z][A-Za-z\\s'()/-]{2,30}\\s*:\\s*.+$").matches(l) && narrative(l)
    }

    private fun mergeWithPrevious(prev: String, cur: String, prevIndex: Int, index: Int): Boolean {
        if (index != prevIndex + 1 || labRow(cur)) return false
        if (Regex("^[A-Z][A-Za-z\\s'()/-]{2,30}\\s*:").containsMatchIn(cur) || cur.startsWith("•") || cur.startsWith("-")) return false
        return Regex("^[a-z(]").containsMatchIn(cur.trim()) || !Regex("[.!?]$").containsMatchIn(prev.trim())
    }

    fun parseDate(input: String): LocalDate? {
        val t = input.trim()
        fun build(y: Int, m: Int, d: Int) = runCatching { LocalDate.of(y, m, d) }.getOrNull()
        fun year(y: Int) = if (y < 100) (if (y >= 70) 1900 + y else 2000 + y) else y
        Regex("\\b(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})\\b").find(t)?.let { return build(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }
        Regex("\\b(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})\\b").find(t)?.let { return build(year(it.groupValues[3].toInt()), it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
        Regex("\\b(\\d{1,2})\\s+([A-Za-z]{3,9})\\.?,?\\s+(\\d{2,4})\\b").find(t)?.let { m ->
            MONTHS[m.groupValues[2].take(3).lowercase()]?.let { return build(year(m.groupValues[3].toInt()), it, m.groupValues[1].toInt()) }
        }
        Regex("\\b([A-Za-z]{3,9})\\.?\\s+(\\d{1,2}),?\\s+(\\d{2,4})\\b").find(t)?.let { m ->
            MONTHS[m.groupValues[1].take(3).lowercase()]?.let { return build(year(m.groupValues[3].toInt()), it, m.groupValues[2].toInt()) }
        }
        return null
    }
}
