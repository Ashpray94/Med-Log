package com.suryaprakash.medlog.clinical

import java.util.Locale
import kotlin.math.roundToInt

private val ONE_NUMBER_LABEL = mapOf("temp" to "The doctor's fever limit for this person (°F or °C)")

/** The four lines of a [Band]. */
enum class Line { AMBER_LOW, RED_LOW, AMBER_HIGH, RED_HIGH;
    fun of(b: Band?): Double? = when (this) { AMBER_LOW -> b?.amberLow; RED_LOW -> b?.redLow; AMBER_HIGH -> b?.amberHigh; RED_HIGH -> b?.redHigh }
}

/**
 * One measure on the "Personal limits" page: which lines make sense, what words to use, and the sensible range.
 * [readingType] is the reading type its "Suggest from readings" button uses (null = no button); [second] means
 * the blood pressure bottom number (v2 of a "bp" reading).
 */
data class MeasureSpec(
    val key: String, val title: String, val unit: String, val lines: List<Line>,
    val range: ClosedFloatingPointRange<Double>, val readingType: String? = null, val second: Boolean = false,
    /** the words that match what DangerRules really does: "below" / "at or below", "at or above" / "more than" */
    val lowWord: String = "below", val highWord: String = "at or above",
    val decimals: Boolean = false,
    /** one number for the whole measure, saved as both the amber and the red high line (fever, B71) */
    val oneNumber: Boolean = false,
) {
    /** The words above the box: a single question for a one-number measure, else the line's name and unit. */
    fun fieldLabel(l: Line): String = if (oneNumber) ONE_NUMBER_LABEL.getValue(key) else "${label(l)} ($unit)"

    fun label(l: Line): String = when (l) {
        Line.AMBER_LOW -> "Amber $lowWord"; Line.RED_LOW -> "Red $lowWord"
        Line.AMBER_HIGH -> "Amber $highWord"; Line.RED_HIGH -> "Red $highWord"
    }
}

/**
 * The pure parts of the limits page: which fields, checking what was typed, the "suggest from readings" maths,
 * the general numbers shown as hints, and the words for the summary. No Android in here, so it is unit-tested.
 */
object LimitsForm {
    val SPECS: List<MeasureSpec> = listOf(
        MeasureSpec("bpSys", "Blood pressure, top number", "mmHg", Line.entries, 60.0..260.0, "bp", lowWord = "at or below"),
        MeasureSpec("bpDia", "Blood pressure, bottom number", "mmHg", Line.entries, 30.0..160.0, "bp", second = true, lowWord = "at or below"),
        MeasureSpec("spo2", "Oxygen (SpO₂)", "%", listOf(Line.AMBER_LOW, Line.RED_LOW), 70.0..100.0, "spo2", lowWord = "at or below"),
        MeasureSpec("sugar", "Sugar", "mg/dL", Line.entries, 30.0..600.0, "sugar", lowWord = "at or below"),
        MeasureSpec("temp", "Temperature", "°F", listOf(Line.AMBER_HIGH, Line.RED_HIGH), 93.0..110.0, "temp", decimals = true, oneNumber = true),
        MeasureSpec("pulse", "Pulse", "per minute", Line.entries, 20.0..220.0, "pulse", lowWord = "at or below"),
        MeasureSpec("vomit", "Vomiting a day", "times", listOf(Line.AMBER_HIGH, Line.RED_HIGH), 1.0..30.0, highWord = "more than"),
        MeasureSpec("loose", "Loose stools a day", "times", listOf(Line.AMBER_HIGH, Line.RED_HIGH), 1.0..30.0, highWord = "more than"),
        MeasureSpec("constipationDays", "Days without a motion", "days", listOf(Line.AMBER_HIGH, Line.RED_HIGH), 1.0..30.0, highWord = "more than"),
    )

    fun spec(key: String) = SPECS.first { it.key == key }

    /** The texts for the boxes when the page opens. A one-number measure shows one number (the amber line, else the red one) on both lines. */
    fun initialTexts(spec: MeasureSpec, band: Band?): Map<Line, String> {
        if (!spec.oneNumber) return spec.lines.associateWith { text(band, it) }
        val one = (band?.amberHigh ?: band?.redHigh)?.let { fmt(it) }.orEmpty()
        return spec.lines.associateWith { one }
    }

    /** [texts] after typing [value] in the box of [line]; a one-number measure keeps both lines equal. */
    fun typed(spec: MeasureSpec, texts: Map<Line, String>, line: Line, value: String): Map<Line, String> =
        if (spec.oneNumber) spec.lines.associateWith { value } else texts + (line to value)

    /** The general fever rule the doctor's number replaces, in words. */
    fun tempRule(ageYears: Int?, cancerCare: Boolean): String {
        val amber = if ((ageYears ?: 0) >= 65) "100.4" else "102"
        val red = if (cancerCare) "100" else "104"
        return "The general rule this replaces: amber at $amber °F, red at $red °F. Your number becomes both the amber and the red line. 104 °F and above is always red."
    }

    /** A number for the box: no ".0". */
    fun fmt(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else String.format(Locale.US, "%.1f", d)

    /** What the person typed. null = empty or not a number. Temperature accepts °C (34–43), converted to °F. */
    fun parse(key: String, text: String): Double? {
        val v = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        return if (key == "temp") DangerRules.toFahrenheit(v) else v
    }

    /** The text for a line (empty when not set). */
    fun text(band: Band?, line: Line): String = line.of(band)?.let { fmt(it) } ?: ""

    /** Typed texts (per line) → a band. */
    fun band(key: String, texts: Map<Line, String>): Band = Band(
        amberLow = parse(key, texts[Line.AMBER_LOW].orEmpty()), redLow = parse(key, texts[Line.RED_LOW].orEmpty()),
        amberHigh = parse(key, texts[Line.AMBER_HIGH].orEmpty()), redHigh = parse(key, texts[Line.RED_HIGH].orEmpty()),
    )

    /**
     * Plain-language problem with what was typed for one measure, or null when it is fine.
     * Checks: numbers only, inside a sensible range, every low line under every high line, and red beyond amber.
     */
    fun validate(spec: MeasureSpec, texts: Map<Line, String>): String? {
        val name = spec.title
        for (l in spec.lines) {
            val t = texts[l].orEmpty()
            if (t.isBlank()) continue
            val v = parse(spec.key, t) ?: return "$name: \"$t\" is not a number."
            if (v !in spec.range) return "$name: ${spec.label(l).lowercase()} should be between ${fmt(spec.range.start)} and ${fmt(spec.range.endInclusive)} ${spec.unit}."
        }
        val b = band(spec.key, texts)
        val lows = listOfNotNull(b.amberLow, b.redLow); val highs = listOfNotNull(b.amberHigh, b.redHigh)
        if (lows.isNotEmpty() && highs.isNotEmpty() && lows.max() >= highs.min()) return "$name: the low numbers must be lower than the high numbers."
        if (b.amberLow != null && b.redLow != null && b.redLow > b.amberLow) return "$name: the red low number must be the same as or lower than the amber one."
        if (b.amberHigh != null && b.redHigh != null && b.redHigh < b.amberHigh) return "$name: the red high number must be the same as or higher than the amber one."
        if (spec.key == "temp" && (b.redHigh ?: 0.0) > 104.0) return "$name: 104 °F and above is always red, so use 104 or lower."
        return null
    }

    /** The first problem across all measures, or null. */
    fun validateAll(all: Map<String, Map<Line, String>>): String? =
        SPECS.firstNotNullOfOrNull { s -> validate(s, all[s.key].orEmpty()) }

    /** The limits to save: only measures with something typed. */
    fun toLimits(all: Map<String, Map<Line, String>>, doctorConfirmed: Boolean, setBy: String, setAt: Long): Limits =
        Limits(SPECS.associate { it.key to band(it.key, all[it.key].orEmpty()) }.filterValues { !it.isEmpty }, doctorConfirmed, setBy, setAt)

    /** At least this many readings are needed for a suggestion. */
    const val MIN_READINGS = 3

    /**
     * "Suggest from readings": margins around the median of the person's last readings ([values], up to 14).
     *   high lines = median + 10 % (amber) / + 20 % (red)
     *   low lines  = median - 5 %  (amber) / - 10 % (red)         (oxygen: capped at 100)
     * Two exceptions, because plain percentages would be silly: sugar swings a lot, so its high lines are
     * + 50 % / + 100 % and its low lines - 25 % / - 50 %; temperature uses + 2 °F / + 3.5 °F (red never above 104).
     * Only the lines that measure has are filled. The helper still has to read the numbers and tap Save.
     */
    fun suggest(key: String, values: List<Double>): Band? {
        if (values.size < MIN_READINGS) return null
        val sorted = values.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        val spec = spec(key)
        fun r(x: Double) = if (spec.decimals) (x * 10).roundToInt() / 10.0 else x.roundToInt().toDouble()
        fun cap(x: Double) = if (key == "spo2") minOf(x, 100.0) else x
        val hA = if (key == "sugar") 1.5 else 1.10
        val hR = if (key == "sugar") 2.0 else 1.20
        val lA = if (key == "sugar") 0.75 else 0.95
        val lR = if (key == "sugar") 0.5 else 0.90
        if (key == "temp") return Band(amberHigh = r(median + 2.0), redHigh = r(minOf(median + 3.5, 104.0)))
        return Band(
            amberLow = if (Line.AMBER_LOW in spec.lines) cap(r(median * lA)) else null,
            redLow = if (Line.RED_LOW in spec.lines) cap(r(median * lR)) else null,
            amberHigh = if (Line.AMBER_HIGH in spec.lines) cap(r(median * hA)) else null,
            redHigh = if (Line.RED_HIGH in spec.lines) cap(r(median * hR)) else null,
        )
    }

    /** The general number for a line, as words for the hint ("general: 180"), following DangerRules. */
    fun general(key: String, line: Line, ageYears: Int?, cancerCare: Boolean): String {
        val over55 = (ageYears ?: 0) > 55
        val elderly = (ageYears ?: 0) >= 65
        val n: String? = when (key) {
            "bpSys" -> when (line) { Line.AMBER_HIGH -> if (over55) null else "180"; Line.AMBER_LOW -> if (over55) null else "89"; else -> null }
            "bpDia" -> if (line == Line.AMBER_HIGH && !over55) "110" else null
            // low lines are "at or below": the general "below 94" is written as 93
            "spo2" -> when (line) { Line.AMBER_LOW -> if (over55) "89" else "93"; Line.RED_LOW -> if (over55) null else "89"; else -> null }
            "sugar" -> when (line) { Line.AMBER_LOW -> "69"; Line.RED_LOW -> "53"; Line.AMBER_HIGH -> "300"; Line.RED_HIGH -> "400" }
            "temp" -> when (line) { Line.AMBER_HIGH -> if (elderly) "100.4" else "102"; Line.RED_HIGH -> if (cancerCare) "100" else "104"; else -> null }
            "pulse" -> when (line) { Line.AMBER_LOW -> "39"; Line.AMBER_HIGH -> "130"; else -> null }
            "vomit", "loose" -> if (line == Line.AMBER_HIGH) (if (cancerCare) "4" else "5") else null
            "constipationDays" -> if (line == Line.AMBER_HIGH && cancerCare) "2" else null
            else -> null
        }
        return if (n == null) "general: none" else "general: $n"
    }

    /** Settings row summary. */
    fun summary(limits: Limits): String = when {
        !isSet(limits) -> "Not set"
        limits.doctorConfirmed -> "Set · doctor agreed"
        else -> "Set"
    }

    fun isSet(limits: Limits) = limits.bands.values.any { !it.isEmpty }

    /** The grey line under a reading: null when nothing is missing. [needsLimit] is Triage.needsLimit. */
    fun needsLimitLine(needsLimit: String?): String? = when (needsLimit) {
        "bp" -> "Your helper hasn't set blood pressure limits yet."
        "spo2" -> "Your helper hasn't set oxygen limits yet."
        else -> null
    }
}
