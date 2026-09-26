package com.suryaprakash.medlog.nlu

import com.suryaprakash.medlog.clinical.Catalogue
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The rule parser: the safety core of speech understanding (plan 7.2).
 *
 * Deterministic, auditable and tested. It finds problems (with negation), and the facts
 * a doctor needs: counts, colours, blood, sides, character, timing, triggers, readings.
 * It never invents a fact: anything it can't find stays "not said".
 */
class Parser(private val cat: Catalogue) {

    private data class Syn(val tokens: List<String>, val problemId: String)

    private val synonyms: List<Syn> = cat.problems
        .flatMap { p -> (p.synonyms + p.label.lowercase()).map { Syn(Normalize.text(it).split(" "), p.id) } }
        .distinctBy { it.tokens to it.problemId }
        .sortedByDescending { it.tokens.size }

    /** "<body part> ... pain" → problem, used when no exact phrase matched. */
    private val regionPain = listOf(
        listOf("head") to "headache",
        listOf("stomach", "tummy", "belly", "abdomen", "vayiru", "pet") to "stomach_pain",
        listOf("chest") to "chest_pain",
        listOf("back", "waist", "kamar") to "back_pain",
        listOf("neck") to "neck_pain",
        listOf("knee", "knees") to "knee_pain",
        listOf("hip", "hips") to "hip_pain",
        listOf("shoulder", "shoulders") to "shoulder_pain",
        listOf("leg", "legs", "calf", "thigh", "thighs") to "leg_pain",
        listOf("arm", "arms", "hand", "hands", "wrist", "elbow", "fingers") to "arm_pain",
        listOf("foot", "feet", "heel", "heels", "ankle", "ankles", "toe", "toes") to "foot_pain",
        listOf("ear", "ears") to "earache",
        listOf("tooth", "teeth", "gum", "gums") to "toothache",
        listOf("throat") to "sore_throat",
        listOf("eye", "eyes") to "eye_pain",
        listOf("joint", "joints") to "joint_swelling",
    )
    private val painWords = setOf("pain", "paining", "pains", "ache", "aching", "aches", "hurts", "hurting", "hurt", "sore", "dard", "vali")

    private val preNeg = setOf("no", "not", "never", "without", "didnt", "dont", "doesnt", "havent", "hasnt", "isnt", "wasnt",
        "werent", "arent", "nothing", "none", "neither", "nor", "denies", "nahi", "illa")
    private val postResolved = listOf("gone", "went away", "has stopped", "stopped", "is better now", "is over", "no more", "subsided", "settled")

    fun parse(raw: String, focus: String? = null, myMedicines: List<String> = emptyList(),
              now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Parsed {
        val text = Normalize.text(raw)
        val tokens = if (text.isEmpty()) emptyList() else text.split(" ").map { it.trim(',', '.') }
        val mentions = findProblems(tokens).toMutableList()

        // The tapped problem is the main one unless the person clearly said otherwise.
        if (focus != null && cat.problem(focus) != null) {
            val existing = mentions.firstOrNull { it.problemId == focus }
            if (existing == null) mentions.add(0, Mention(focus, matched = "(tapped)", position = -1))
            else if (mentions.first() !== existing) { mentions.remove(existing); mentions.add(0, existing) }
        }

        val clauses = Normalize.clauses(text)
        // negation / resolution per mention, within its own clause
        for (m in mentions) {
            if (m.position < 0) continue
            val clause = clauseAt(clauses, text, m.position) ?: continue
            val before = clause.second.substring(0, (charIndexOfToken(text, m.position) - clause.first).coerceIn(0, clause.second.length))
            val beforeTokens = scope(before)
            if (beforeTokens.any { it in preNeg } && !beforeTokens.contains("only")) {
                if (beforeTokens.joinToString(" ").contains("no more")) m.resolved = true
                m.negated = true
            }
            val after = clause.second.substring((charIndexOfToken(text, m.position) - clause.first).coerceIn(0, clause.second.length))
            if (postResolved.any { Regex("\\b$it\\b").containsMatchIn(after) }) { m.resolved = true; m.negated = true }
        }
        // A tapped problem that is "resolved" is still recorded (as better), never dropped.
        mentions.firstOrNull { it.problemId == focus && it.negated }?.let { if (it.resolved) { it.negated = false; it.facts["better"] = Fact(true, Source.SAID) } }

        val primary = mentions.firstOrNull { !it.negated }
        // facts, clause by clause, attached to the nearest problem in the clause (else the main one)
        for ((start, clause) in clauses) {
            val inClause = mentions.filter { !it.negated && it.position >= 0 && charIndexOfToken(text, it.position) in start..(start + clause.length) }
            val owner = inClause.firstOrNull() ?: primary ?: continue
            val scratch = Mention(owner.problemId, facts = owner.facts.toMutableMap())
            extractFacts(clause, scratch, primary, myMedicines)
            // Give each new fact to the problem it belongs to: the main problem first if the fact is one of its
            // fields, then a problem in this clause that has it, else the problem named in the clause.
            val candidates = listOfNotNull(primary) + inClause.filter { it !== primary }
            for ((k, f) in scratch.facts) {
                if (owner.facts[k] == f) continue
                val target = candidates.firstOrNull { c -> cat.problem(c.problemId)?.fields?.contains(k) == true && (c === primary || c in inClause) } ?: owner
                if (target.facts[k]?.source != Source.ASKED) target.facts[k] = f
            }
        }
        // counts/timing said before any problem word ("twice after lunch") belong to the main problem
        if (primary != null && clauses.isEmpty() && text.isNotBlank()) extractFacts(text, primary, primary, myMedicines)

        val readings = readings(text)
        readings.firstOrNull { it.type == "temp" }?.let { r -> primary?.facts?.putIfAbsent("temperature", Fact(r.v1, Source.SAID)) }
        primary?.let { p ->
            if (p.problemId in setOf("high_bp", "low_bp", "high_sugar", "low_sugar", "low_oxygen", "palpitations", "weight_loss")) {
                val want = mapOf("high_bp" to "bp", "low_bp" to "bp", "high_sugar" to "sugar", "low_sugar" to "sugar", "low_oxygen" to "spo2",
                    "palpitations" to "pulse", "weight_loss" to "weight")[p.problemId]
                readings.firstOrNull { it.type == want }?.let { r -> p.facts["reading"] = Fact(if (r.type == "bp") "${r.v1.toInt()}/${r.v2?.toInt()}" else fmt1(r.v1), Source.SAID) }
            }
        }

        return Parsed(
            transcript = raw.trim(),
            mentions = mentions,
            occurredAt = whenSaid(text, now, zone),
            readings = readings,
            medicinesTaken = medicinesTaken(text, myMedicines),
        )
    }

    // ───────────────────────── problems ─────────────────────────

    private fun findProblems(tokens: List<String>): List<Mention> {
        val used = BooleanArray(tokens.size)
        val found = ArrayList<Mention>()
        var i = 0
        while (i < tokens.size) {
            if (used[i]) { i++; continue }
            var matched: Pair<Syn, Boolean>? = null
            for (syn in synonyms) {
                val n = syn.tokens.size
                if (i + n > tokens.size) continue
                var fuzzy = false
                var ok = true
                for (k in 0 until n) {
                    val a = tokens[i + k]; val b = syn.tokens[k]
                    if (a == b || stem(a) == stem(b)) continue
                    if (closeEnough(a, b)) { fuzzy = true; continue }
                    ok = false; break
                }
                if (ok) { matched = syn to fuzzy; break }
            }
            if (matched != null) {
                val (syn, fuzzy) = matched
                val n = syn.tokens.size
                for (k in 0 until n) used[i + k] = true
                if (found.none { it.problemId == syn.problemId }) {
                    found += Mention(syn.problemId, matched = tokens.subList(i, i + n).joinToString(" "), fuzzy = fuzzy, position = i)
                }
                i += n
            } else i++
        }
        // "<body part> ... pain" within 3 words, when not already covered
        for (j in tokens.indices) {
            if (used[j] || tokens[j] !in painWords) continue
            for (d in -3..3) {
                val k = j + d
                if (k !in tokens.indices || k == j || used[k]) continue
                val pid = regionPain.firstOrNull { tokens[k] in it.first }?.second ?: continue
                if (found.none { it.problemId == pid }) {
                    found += Mention(pid, matched = tokens.subList(minOf(j, k), maxOf(j, k) + 1).joinToString(" "), position = minOf(j, k))
                    used[j] = true; used[k] = true
                }
                break
            }
        }
        return found.sortedBy { it.position }
    }

    private fun stem(w: String): String = when {
        w.length > 5 && w.endsWith("ing") -> w.dropLast(3)
        w.length > 4 && w.endsWith("ed") -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    /** Speech-recognition slips: "vomitting", "diarrhoea". Only for long words, never for short ones. */
    private fun closeEnough(a: String, b: String): Boolean {
        if (a.length < 6 || b.length < 6) return false
        if (a.first() != b.first()) return false
        val limit = if (b.length >= 9) 2 else 1
        return levenshtein(a, b, limit) <= limit
    }

    private fun levenshtein(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var best = cur[0]
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                best = minOf(best, cur[j])
            }
            if (best > limit) return limit + 1
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    private fun charIndexOfToken(text: String, token: Int): Int {
        if (token <= 0) return 0
        var count = 0
        for (i in text.indices) if (text[i] == ' ') { count++; if (count == token) return i + 1 }
        return text.length
    }

    private fun clauseAt(clauses: List<Pair<Int, String>>, text: String, token: Int): Pair<Int, String>? {
        val c = charIndexOfToken(text, token)
        return clauses.lastOrNull { it.first <= c } ?: clauses.firstOrNull()
    }

    // ───────────────────────── facts ─────────────────────────

    /** The few words before a phrase that a negation can reach: never across "and", "so", "because". */
    private fun scope(before: String): List<String> {
        val t = before.trim().split(" ").filter { it.isNotBlank() }
        val cut = t.indexOfLast { it in setOf("and", "so", "because", "cause", "which", "that", "who") }
        return t.drop(cut + 1).takeLast(3)
    }

    private fun negatedAt(clause: String, index: Int): Boolean {
        val before = scope(clause.substring(0, index))
        return before.any { it in preNeg } && !before.contains("only")
    }

    /** Finds [phrases] in the clause; returns true / false (negated) / null (not mentioned). */
    private fun yesNo(clause: String, phrases: List<String>, negPhrases: List<String> = emptyList()): Boolean? {
        for (p in negPhrases) if (Regex("\\b$p\\b").containsMatchIn(clause)) return false
        for (p in phrases) {
            val m = Regex("\\b$p\\b").find(clause) ?: continue
            return !negatedAt(clause, m.range.first)
        }
        return null
    }

    private fun put(m: Mention, field: String, value: Any?, quote: String? = null) {
        if (value == null) return
        if (m.facts[field]?.source == Source.ASKED) return
        m.facts[field] = Fact(value, Source.SAID, quote = quote)
    }

    private fun extractFacts(clause: String, owner: Mention, primary: Mention?, meds: List<String>) {
        val c = " $clause "
        val pid = owner.problemId
        val chesty = pid in setOf("chest_pain", "chest_tight", "acidity", "palpitations", "breathless")

        // counts: "2 times", "3 to 4 times", "5 motions"
        Regex("\\b(\\d{1,2})\\s*(?:to|or|-)\\s*(\\d{1,2})\\s*(times|vomits|motions|stools|episodes)").find(c)?.let {
            put(owner, "count", it.groupValues[2].toInt(), it.value.trim())
        } ?: Regex("\\b(\\d{1,2})\\s*(?:more\\s+)?(times|time|vomits|motions|loose motions|stools|episodes|attacks)\\b").find(c)?.let {
            put(owner, "count", it.groupValues[1].toInt(), it.value.trim())
        }

        // severity: "7 out of 10", "8/10", or words
        Regex("\\b(\\d{1,2})\\s*(?:out of|on|by|/)\\s*10\\b").find(c)?.let { m ->
            m.groupValues[1].toInt().takeIf { it in 0..10 }?.let { put(owner, "severity", it, m.value.trim()) }
        } ?: severityWord(c)?.let { (v, q) -> put(owner, "severity", v, q) }

        // side
        when {
            Regex("\\bboth (sides|side|legs|arms|eyes|ears|feet|hands|knees)\\b|\\bboth\\b").containsMatchIn(c) -> put(owner, "side", "both")
            Regex("\\bleft\\b").containsMatchIn(c) && Regex("\\bright\\b").containsMatchIn(c) && !Regex("\\bright now\\b").containsMatchIn(c) -> put(owner, "side", "both")
            Regex("\\bleft\\b").containsMatchIn(c) -> put(owner, "side", "left")
            Regex("\\bright\\b(?! now| away)").containsMatchIn(c) -> put(owner, "side", "right")
            Regex("\\b(middle|centre|center)\\b").containsMatchIn(c) -> put(owner, "side", "middle")
        }

        // character of pain
        val chars = CHARACTER.filter { (_, words) -> words.any { Regex("\\b$it\\b").containsMatchIn(c) } }.map { it.first }
        if (chars.isNotEmpty()) {
            val prev = (owner.facts["character"]?.value as? List<*>)?.map { it.toString() } ?: emptyList()
            put(owner, "character", (prev + chars).distinct())
        }

        // onset & pattern
        if (Regex("\\b(suddenly|sudden|all of a sudden|out of nowhere)\\b").containsMatchIn(c)) put(owner, "onset", "sudden")
        else if (Regex("\\b(slowly|gradually|little by little)\\b").containsMatchIn(c)) put(owner, "onset", "slow")
        if (Regex("\\b(comes and goes|on and off|off and on|sometimes|now and then|keeps coming)\\b").containsMatchIn(c)) put(owner, "pattern", "comes and goes")
        else if (Regex("\\b(all the time|constant|continuous|nonstop|non stop|whole day)\\b").containsMatchIn(c)) put(owner, "pattern", "constant")

        // colour (not "red" in "red eye"/"red and hot" handled by their own fields)
        COLOURS.firstOrNull { (_, words) -> words.any { w -> Regex("\\b$w\\b").containsMatchIn(c) } }?.let { (colour, _) ->
            if (!(colour == "red" && Regex("\\bred (eye|eyes|and hot|spots|patches|rash)\\b").containsMatchIn(c))) put(owner, "colour", colour)
        }
        // what came out
        when {
            Regex("\\b(bile|bitter water|yellow water)\\b").containsMatchIn(c) -> put(owner, "content", "bile")
            Regex("\\b(food|whatever i ate|what i ate|rice|meal)\\b").containsMatchIn(c) && pid == "vomiting" -> put(owner, "content", "food")
            Regex("\\b(only water|just water|liquid)\\b").containsMatchIn(c) -> put(owner, "content", "liquid")
        }
        // stool type (Bristol, in plain words)
        if (pid in setOf("loose_motions", "constipation")) when {
            Regex("\\b(watery|like water|water only|full water)\\b").containsMatchIn(c) -> put(owner, "stoolType", "watery")
            Regex("\\b(mushy|loose|soft and loose)\\b").containsMatchIn(c) -> put(owner, "stoolType", "mushy")
            Regex("\\b(hard|pellets|like stones|lumps)\\b").containsMatchIn(c) -> put(owner, "stoolType", "hard lumps")
        }
        if (pid in setOf("cough")) when {
            Regex("\\bdry\\b").containsMatchIn(c) -> put(owner, "dryWet", "dry")
            Regex("\\b(wet|phlegm|sputum|mucus|cough with)\\b").containsMatchIn(c) -> put(owner, "dryWet", "wet")
        }

        // yes / no facts
        for ((field, rule) in YESNO) {
            if (field == "armJaw" && !chesty) continue
            if (field == "dizzyBefore" && pid !in setOf("fall", "fainted", "near_fall", "palpitations")) continue
            if (field == "painful" && owner.facts.containsKey("severity")) continue
            if (field == "blood" && pid in setOf("bleeding", "nosebleed", "blood_stool", "blood_urine", "cough_blood", "vomit_blood", "bleeding_gums", "heavy_bleeding", "postmeno_bleeding", "cut")) continue
            yesNo(c, rule.first, rule.second)?.let { put(owner, field, it) }
        }
        if (Regex("\\b(blood thinner|blood thinners)\\b").containsMatchIn(c) || THINNERS.any { Regex("\\b$it\\b").containsMatchIn(c) }) {
            yesNo(c, listOf("blood thinners?") + THINNERS)?.let { put(owner, "bloodThinner", it) }
        }

        // timing / triggers, kept in the person's words
        contextPhrases(c).takeIf { it.isNotEmpty() }?.let { put(owner, "context", it.joinToString(", ")) }
        Regex("\\b(?:worse|increases|more|aggravated)\\s+(?:when|with|after|on|if|during|while|by)\\s+([a-z ]{2,30}?)(?=$|\\s(?:and|but|better)\\b|\\s*$)").find(c)?.let { put(owner, "worse", it.groupValues[1].trim()) }
        Regex("\\b(?:better|relieved|reduces|eases|less|goes away|settles)\\s+(?:when|with|after|on|if|by|while)\\s+([a-z ]{2,30}?)(?=$|\\s(?:and|but|worse)\\b|\\s*$)").find(c)?.let { put(owner, "better", it.groupValues[1].trim()) }
        Regex("\\b(?:spreading|spreads|spread|going|goes|radiating|radiates|moving|moves|travels|shooting)\\s+(?:to|down|into|towards|up to)\\s+(?:the |my )?([a-z ]{2,24}?)(?=$|\\s(?:and|but|also)\\b|\\s*$)").find(c)?.let {
            put(owner, "radiation", it.groupValues[1].trim())
            if (chesty && Regex("arm|jaw|back|shoulder|neck").containsMatchIn(it.groupValues[1])) put(owner, "armJaw", true)
        }
        Regex("\\b(?:for|since|from|past|last)\\s+(\\d{1,3}|a|an|one)\\s+(minute|hour|day|week|month)s?\\b").find(c)?.let { put(owner, "duration", it.value.trim()) }
        Regex("\\b(\\d{1,2})\\s+weeks?\\b").find(c)?.let { if (owner.problemId in setOf("cough", "hoarse", "mouth_ulcer", "wound_not_healing", "lump", "swallowing", "weight_loss", "hair_loss")) put(owner, "weeks", it.groupValues[1].toInt()) }
        Regex("\\b(\\d{1,2})\\s+months?\\b").find(c)?.let { if (owner.problemId in setOf("cough", "hoarse", "lump", "weight_loss", "swallowing")) put(owner, "weeks", it.groupValues[1].toInt() * 4) }
        Regex("\\b(\\d)\\s+pillows?\\b").find(c)?.let { put(owner, "pillows", it.groupValues[1].toInt()) }
        Regex("\\b(\\d{1,2})\\s+hours?\\b").find(c)?.let { if (pid in setOf("no_urine")) put(owner, "hours", it.groupValues[1].toInt()) }
        Regex("\\b(\\d{1,3})\\s+(minutes?|hours?)\\s+on the floor\\b").find(c)?.let {
            val n = it.groupValues[1].toInt(); put(owner, "timeOnFloor", if (it.groupValues[2].startsWith("hour")) n * 60 else n)
        }
        Regex("\\bslept\\s+(?:only\\s+|for\\s+|about\\s+)?(\\d{1,2})\\s+hours?\\b").find(c)?.let { put(owner, "sleepHours", it.groupValues[1].toInt()) }
        // what stopped: "couldn't eat", "can't sleep", "can't walk"
        val impact = IMPACT.filter { (_, rx) -> Regex(rx).containsMatchIn(c) }.map { it.first }
        if (impact.isNotEmpty()) put(owner, "impact", impact)
    }

    private fun severityWord(c: String): Pair<Int, String>? {
        for ((rx, v) in SEVERITY) Regex("\\b($rx)\\b").find(c)?.let { return v to it.value }
        return null
    }

    private fun contextPhrases(c: String): List<String> {
        val out = LinkedHashSet<String>()
        Regex("\\b(after|before|during|while|with)\\s+(breakfast|lunch|dinner|food|eating|meals?|tea|coffee|milk|walking|climbing stairs|climbing|bath|bathing|waking up|getting up|standing up|sleeping|lying down|exercise|the tablet|my tablet|tablets|medicine|the medicine|my medicines|taking [a-z]+)\\b").findAll(c).forEach { out += it.value.trim() }
        Regex("\\b(this|in the|every|early|yesterday|last) (morning|afternoon|evening|night)\\b|\\bat night\\b|\\bat midnight\\b|\\bon empty stomach\\b|\\bempty stomach\\b").findAll(c).forEach { out += it.value.trim() }
        return out.toList()
    }

    // ───────────────────────── readings ─────────────────────────

    fun readings(text: String): List<Reading> {
        val out = ArrayList<Reading>()
        Regex("\\b(\\d{2,3})\\s*(?:by|over|/|upon|slash)\\s*(\\d{2,3})\\b").findAll(text).forEach { m ->
            val s = m.groupValues[1].toDouble(); val d = m.groupValues[2].toDouble()
            if (s in 60.0..260.0 && d in 30.0..160.0 && s > d) out += Reading("bp", s, d, "mmHg")
        }
        Regex("\\b(?:sugar|glucose|rbs|fbs|ppbs|grbs|fasting|post lunch|random)\\D{0,18}?(\\d{2,3})\\b").find(text)?.let {
            val v = it.groupValues[1].toDouble(); if (v in 20.0..600.0) out += Reading("sugar", v, unit = "mg/dL")
        }
        Regex("\\b(?:oxygen|spo2|sp o2|saturation|o2|pulse ox)\\D{0,18}?(\\d{2,3})\\b").find(text)?.let {
            val v = it.groupValues[1].toDouble(); if (v in 50.0..100.0) out += Reading("spo2", v, unit = "%")
        }
        Regex("\\b(?:pulse|heart rate|heartbeat)\\D{0,14}?(\\d{2,3})\\b").find(text)?.let {
            val v = it.groupValues[1].toDouble(); if (v in 30.0..220.0) out += Reading("pulse", v, unit = "bpm")
        }
        Regex("\\b(\\d{2,3}(?:\\.\\d)?)\\s*(kg|kgs|kilo|kilos|kilograms?)\\b").find(text)?.let {
            val v = it.groupValues[1].toDouble(); if (v in 20.0..250.0) out += Reading("weight", v, unit = "kg")
        }
        temperature(text)?.let { out += Reading("temp", it, unit = "°F") }
        return out
    }

    /** Temperature in °F. Accepts Celsius (34–43) and converts. Rejects impossible values. */
    fun temperature(text: String): Double? {
        for (mm in Regex("\\b(\\d{2,3}(?:\\.\\d{1,2})?)\\s*(degrees?|deg|f|fahrenheit|c|celsius|centigrade)?\\b").findAll(text)) {
            val v = mm.groupValues[1].toDoubleOrNull() ?: continue
            val unit = mm.groupValues[2]
            val window = text.substring(maxOf(0, mm.range.first - 30), minOf(text.length, mm.range.last + 20))
            val near = Regex("fever|temperature|temp|thermometer|degree|hot").containsMatchIn(window)
            if (unit.isEmpty() && !near) continue
            if (Regex("^\\d{2,3}\\s*(by|over|/|upon)\\s*\\d").containsMatchIn(text.substring(mm.range.first))) continue
            val saysF = unit.startsWith("f")
            if (!saysF && v in 34.0..43.0) return Math.round((v * 9 / 5 + 32) * 10) / 10.0
            if (!unit.startsWith("c") && v in 93.0..110.0) return v
        }
        return null
    }

    // ───────────────────────── when ─────────────────────────

    fun whenSaid(text: String, now: LocalDateTime, zone: ZoneId): Long? {
        fun at(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
        Regex("\\b(\\d{1,3}|a|an|half an)\\s+(minute|minutes|min|mins|hour|hours|day|days)\\s+(ago|back)\\b").find(text)?.let {
            val n = when (it.groupValues[1]) { "a", "an" -> 1L; "half an" -> 0L; else -> it.groupValues[1].toLong() }
            return at(when {
                it.groupValues[1] == "half an" -> now.minusMinutes(30)
                it.groupValues[2].startsWith("min") -> now.minusMinutes(n)
                it.groupValues[2].startsWith("hour") -> now.minusHours(n)
                else -> now.minusDays(n)
            })
        }
        Regex("\\bat (\\d{1,2})(?:[.: ](\\d{2}))?\\s*(am|pm|a m|p m|oclock|o clock)?\\b").find(text)?.let {
            var h = it.groupValues[1].toInt(); val mi = it.groupValues[2].toIntOrNull() ?: 0
            val ap = it.groupValues[3].replace(" ", "")
            if (h in 1..12 && ap == "pm" && h != 12) h += 12
            if (ap == "am" && h == 12) h = 0
            if (ap.isEmpty() && ap != "oclock" && h in 1..6) h += 12      // "at 3" in conversation usually means afternoon
            if (h in 0..23 && mi in 0..59) {
                var t = now.withHour(h).withMinute(mi).withSecond(0).withNano(0)
                if (Regex("\\byesterday\\b").containsMatchIn(text)) t = t.minusDays(1)
                else if (t.isAfter(now)) t = t.minusDays(1)
                return at(t)
            }
        }
        val today = now.toLocalDate()
        return when {
            Regex("\\bjust now\\b|\\bright now\\b").containsMatchIn(text) -> at(now)
            Regex("\\blast night\\b|\\byesterday night\\b").containsMatchIn(text) -> at(today.minusDays(1).atTime(22, 0))
            Regex("\\byesterday (morning)\\b").containsMatchIn(text) -> at(today.minusDays(1).atTime(8, 0))
            Regex("\\byesterday (afternoon)\\b").containsMatchIn(text) -> at(today.minusDays(1).atTime(14, 0))
            Regex("\\byesterday (evening)\\b").containsMatchIn(text) -> at(today.minusDays(1).atTime(18, 0))
            Regex("\\byesterday\\b").containsMatchIn(text) -> at(today.minusDays(1).atTime(12, 0))
            Regex("\\b(this|in the|early) morning\\b").containsMatchIn(text) && now.hour >= 12 -> at(today.atTime(8, 0))
            Regex("\\bthis afternoon\\b").containsMatchIn(text) && now.hour >= 17 -> at(today.atTime(14, 0))
            Regex("\\bafter (breakfast)\\b").containsMatchIn(text) && now.hour >= 11 -> at(today.atTime(9, 0))
            Regex("\\bafter (lunch)\\b").containsMatchIn(text) && now.hour >= 15 -> at(today.atTime(14, 0))
            else -> null
        }
    }

    // ───────────────────────── medicines ─────────────────────────

    fun medicinesTaken(text: String, mine: List<String>): List<String> {
        val m = Regex("\\b(?:took|taken|had|take|taking|swallowed|used)\\s+(?:a |an |one |my |the |\\d+ |some )?(?:tablet of |tablets of |dose of |spoon of |sachet of |capsule of )?([a-z0-9 -]{2,30})").findAll(text)
        val out = LinkedHashSet<String>()
        for (hit in m) {
            val phrase = hit.groupValues[1]
            mine.firstOrNull { name -> val n = Normalize.text(name).split(" ").first(); n.length >= 3 && (phrase.startsWith(n) || phrase.contains(" $n")) }?.let { out += it }
            COMMON_MEDS.firstOrNull { phrase.startsWith(it) || phrase.contains(" $it") }?.let { out += it.replaceFirstChar { c -> c.uppercase() } }
        }
        return out.toList()
    }

    companion object {
        val SEVERITY = listOf(
            "unbearable|worst ever|worst|excruciating|terrible|killing me|cant bear|cannot bear" to 9,
            "very bad|very severe|severe|very much|too much|a lot|very painful|really bad" to 8,
            "bad|quite bad|strong" to 6,
            "moderate|medium|okay bad|so so" to 5,
            "mild|a little|little|slight|slightly|small|not much|bit" to 2,
        )
        val CHARACTER = listOf(
            "sharp" to listOf("sharp"),
            "dull" to listOf("dull"),
            "burning" to listOf("burning", "burns", "like fire", "jalan"),
            "throbbing" to listOf("throbbing", "pounding", "pulsating", "beating", "banging"),
            "cramping" to listOf("cramping", "cramps", "cramp", "twisting", "colicky"),
            "pressing" to listOf("pressing", "pressure", "squeezing", "crushing", "elephant"),
            "tight" to listOf("tight", "tightness", "band"),
            "stabbing" to listOf("stabbing", "pricking", "piercing", "needle", "needles", "knife"),
            "aching" to listOf("aching", "ache"),
            "shooting" to listOf("shooting", "electric", "current"),
            "tingling" to listOf("tingling", "pins and needles"),
            "heavy" to listOf("heavy", "heaviness"),
        )
        val COLOURS = listOf(
            "yellow" to listOf("yellow", "yellowish"),
            "green" to listOf("green", "greenish"),
            "red" to listOf("red", "reddish", "bright red"),
            "brown" to listOf("brown", "brownish"),
            "black" to listOf("black", "blackish"),
            "white" to listOf("white", "whitish", "milky"),
            "clear" to listOf("clear", "transparent", "colourless", "colorless"),
            "pink" to listOf("pink", "pinkish"),
            "grey" to listOf("grey", "gray"),
        )
        val THINNERS = listOf("ecosprin", "aspirin", "warfarin", "acitrom", "acenocoumarol", "clopidogrel", "clopitab", "clopilet",
            "plavix", "apixaban", "eliquis", "rivaroxaban", "xarelto", "dabigatran", "pradaxa", "ticagrelor", "brilinta", "heparin", "enoxaparin", "prasugrel")
        val COMMON_MEDS = listOf("paracetamol", "dolo", "crocin", "calpol", "combiflam", "ibuprofen", "brufen", "pan d", "pan 40", "pantop",
            "pantoprazole", "rantac", "ranitidine", "omeprazole", "omez", "digene", "gelusil", "eno", "ondem", "ondansetron", "emeset", "domperidone",
            "domstal", "loperamide", "imodium", "ors", "electral", "cetirizine", "avil", "allegra", "sorbitrate", "isosorbide", "glucose",
            "sugar", "insulin", "inhaler", "asthalin", "nebuliser", "nebulizer", "saridon", "disprin", "vicks", "cough syrup", "benadryl")
        val IMPACT = listOf(
            "eating" to "\\b(cant|cannot|couldnt|not able to|unable to|didnt) eat\\b|\\bno food\\b",
            "sleeping" to "\\b(cant|cannot|couldnt|not able to|unable to|didnt) sleep\\b|\\bno sleep\\b",
            "walking" to "\\b(cant|cannot|couldnt|not able to|unable to) walk\\b",
            "work" to "\\b(cant|cannot|couldnt|not able to|unable to) (work|do anything|do my work|cook)\\b",
            "bathing" to "\\b(cant|cannot|couldnt|not able to|unable to) (bathe|take bath|have bath)\\b",
        )
        /** field → (phrases meaning yes, phrases meaning no). Negation before a yes phrase makes it no. */
        val YESNO: Map<String, Pair<List<String>, List<String>>> = linkedMapOf(
            "blood" to (listOf("blood(?!\\s*(thinner|thinners|pressure|sugar|test|tests|report|group|bank))", "bloody", "bleeding") to emptyList()),
            "coffeeGround" to (listOf("coffee grounds?", "like coffee", "coffee powder", "coffee colou?r") to emptyList()),
            "keepWater" to (listOf("keeping water down", "can keep water", "keep water down", "drinking fine", "able to drink", "water stays", "drinking water fine")
                to listOf("cant keep water", "cannot keep water", "cant keep anything", "cannot keep anything", "even water comes out", "even water is coming out",
                "vomit(ing|ed)? even water", "not able to drink", "unable to drink", "cant drink", "cannot drink", "water also comes out", "not able to keep water")),
            "urineToday" to (listOf("passed urine", "passing urine", "peed", "urine is coming", "urine came", "went for urine")
                to listOf("no urine", "not passed urine", "havent passed urine", "didnt pass urine", "not peed", "havent peed", "urine not coming", "no pee")),
            "projectile" to (listOf("projectile", "forcefully", "forceful", "shooting out", "with force") to emptyList()),
            "chills" to (listOf("shiver", "shivering", "shivers", "chills", "rigors", "shaking with cold", "kulir") to emptyList()),
            "sweating" to (listOf("sweat", "sweating", "sweaty", "perspiring", "perspiration", "cold sweat") to emptyList()),
            "confusion" to (listOf("confused", "confusion", "not making sense", "disoriented", "talking nonsense", "doesnt recognise", "doesnt recognize") to emptyList()),
            "breathless" to (listOf("breathless", "short of breath", "shortness of breath", "hard to breathe", "cant breathe", "cannot breathe", "difficulty (in )?breathing",
                "breathing difficulty", "out of breath", "gasping", "breathing problem", "trouble breathing", "breathing is hard", "breathlessness") to emptyList()),
            "atRest" to (listOf("even at rest", "even resting", "while resting", "at rest", "even sitting", "sitting also", "even when sitting", "lying down also", "without doing anything") to emptyList()),
            "lyingFlat" to (listOf("lying flat", "lie flat", "lying down", "when i lie down", "when i lie", "at night lying") to emptyList()),
            "armJaw" to (listOf("left arm", "arm", "jaw", "left hand", "shoulder", "neck") to emptyList()),
            "exertion" to (listOf("walking", "climbing", "stairs", "steps", "exercise", "when i walk", "on walking", "exertion", "working hard") to emptyList()),
            "hitHead" to (listOf("hit my head", "hit head", "head hit", "banged my head", "bumped my head", "head injury", "fell on my head", "head struck", "hurt my head", "head got hit")
                to listOf("didnt hit my head", "did not hit my head", "head is fine", "head was fine", "head not hit")),
            "couldGetUp" to (listOf("got up", "got up by myself", "able to get up", "could get up", "managed to get up", "got back up")
                to listOf("couldnt get up", "could not get up", "cant get up", "cannot get up", "unable to get up", "not able to get up", "still on the floor", "lying on the floor", "still lying")),
            "dizzyBefore" to (listOf("dizzy", "giddy", "light headed", "lightheaded", "head was spinning", "felt faint") to emptyList()),
            "worstEver" to (listOf("worst ever", "worst headache", "worst pain", "thunderclap", "never had like this", "never had this bad", "like never before") to emptyList()),
            "visionChange" to (listOf("blurred", "blurry", "cant see", "cannot see", "vision", "seeing double", "double vision", "eyesight", "things look hazy") to emptyList()),
            "stiffNeck" to (listOf("stiff neck", "neck stiff", "neck is stiff", "cant bend my neck", "cannot bend neck") to emptyList()),
            "faceDroop" to (listOf("face drooping", "face droop", "face is drooping", "mouth drooping", "face crooked", "face fell", "face is crooked", "face deviated") to emptyList()),
            "armWeak" to (listOf("arm weak", "leg weak", "arm is weak", "leg is weak", "cant lift my arm", "cannot lift my arm", "cant lift my hand", "weak on 1 side", "1 side weak", "hand weak", "no strength in (my )?(arm|hand|leg)") to emptyList()),
            "speech" to (listOf("slurred", "slurring", "speech", "cant speak", "cannot speak", "words not coming", "cant talk", "cannot talk", "talking funny") to emptyList()),
            "spinning" to (listOf("spinning", "room spinning", "going round", "rotating", "vertigo", "whirling", "moving around me") to emptyList()),
            "onStanding" to (listOf("stand up", "standing up", "when i stand", "on standing", "get up from (the )?bed", "getting up", "when i get up") to emptyList()),
            "itchy" to (listOf("itch", "itchy", "itching", "khujli") to emptyList()),
            "spreading" to (listOf("spreading", "spreads", "spread", "getting bigger", "increasing") to emptyList()),
            "blisters" to (listOf("blisters?", "water bubbles", "bubbles on skin") to emptyList()),
            "newMedicine" to (listOf("new medicine", "new tablet", "new tablets", "started a new", "new pill", "started taking", "changed my medicine", "new drug", "new medicines", "dose was increased", "dose increased") to emptyList()),
            "lipSwelling" to (listOf("lips? swelling", "swollen lips?", "lips? (is |are )?swollen", "face swelling", "face (is )?swollen", "tongue swelling", "tongue (is )?swollen",
                "swelling (of|in) (the )?(lips?|face|tongue|throat)", "throat (is )?swelling", "throat closing") to emptyList()),
            "oneSide" to (listOf("1 leg", "1 side", "only left", "only right", "only 1", "only on 1") to listOf("both legs", "both sides", "both feet")),
            "redHot" to (listOf("red and hot", "hot and red", "warm to touch", "red hot", "hot to touch") to emptyList()),
            "painful" to (listOf("painful", "paining", "hurts", "tender") to emptyList()),
            "burning" to (listOf("burning", "burns") to emptyList()),
            "frequent" to (listOf("often", "again and again", "frequently", "many times", "every hour", "every few minutes") to emptyList()),
            "cannotPass" to (listOf("cant pass urine", "cannot pass urine", "urine not coming", "unable to pass urine", "not able to pass urine") to emptyList()),
            "bleedingStops" to (listOf("stopped bleeding", "bleeding stopped", "stops with pressure", "bleeding has stopped", "bleeding stops") to
                listOf("not stopping", "wont stop", "doesnt stop", "keeps bleeding", "still bleeding", "not stopped", "wont stop bleeding", "cant stop")),
            "choking" to (listOf("choking", "choked", "cant swallow and cant breathe") to emptyList()),
            "fits" to (listOf("fits?", "seizure", "convulsions?", "jerking", "shaking fit") to emptyList()),
            "lostConsciousness" to (listOf("passed out", "blacked out", "fainted", "lost consciousness", "unconscious", "became unconscious", "blacked") to emptyList()),
            "swallowWater" to (listOf("can swallow water", "water goes down", "drinking water is fine") to
                listOf("cant swallow water", "cannot swallow water", "even water doesnt go", "water not going down", "cant even swallow water", "cannot even drink water")),
            "mucus" to (listOf("mucus", "slimy", "jelly", "sticky white") to emptyList()),
            "blackStool" to (listOf("black stool", "black motion", "black motions", "tarry", "black and sticky", "dark stool", "black colou?r stool", "stool is black", "motion is black") to emptyList()),
            "selfHarm" to (listOf("want to die", "kill myself", "end my life", "hurt myself", "suicide", "no reason to live", "better off dead") to emptyList()),
        )
    }
}
