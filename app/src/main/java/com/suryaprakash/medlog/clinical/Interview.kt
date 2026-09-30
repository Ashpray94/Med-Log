package com.suryaprakash.medlog.clinical

import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Normalize

/**
 * The conversation: one short question at a time (plan 6.3, reworked for older people).
 *
 * Core questions first (when, danger signs, where, how bad): 4–6 at most. Then the person is asked
 * whether they want to tell more; only if they say yes do the extended questions follow.
 * Every answer can be spoken (in English or an Indian language, see [Lang]) or tapped.
 */
object Interview {

    enum class Kind { YESNO, CHOICE, MULTI, NUMBER, SCALE, BODY, TEMP, FREE }

    data class Choice(val value: String, val label: String, val words: List<String> = emptyList(), val sprite: String? = null)

    data class Ask(
        val id: String,
        val field: String,
        val text: String,
        val kind: Kind,
        val choices: List<Choice> = emptyList(),
        val core: Boolean = true,
        val danger: Boolean = false,
        /** Two short lines: what the question means, then why it is asked (see [Help]). Shown under the question. */
        val help: String = Help.of(id, field),
        /** The question is only asked when this holds (for example, colour of phlegm only if phlegm came up). */
        val gate: Gate? = null,
    ) {
        val what: String get() = Help.what(help)
        val why: String get() = Help.why(help)
    }

    /** "Ask only if [field] was answered with one of [any]" ([negate]: only if it was not; an unanswered field then counts as "not"). */
    data class Gate(val field: String, val any: Set<Any?>, val negate: Boolean = false) {
        fun open(facts: Map<String, Fact>): Boolean {
            val v = facts[field]?.value ?: return negate
            val hit = v in any
            return if (negate) !hit else hit
        }
    }

    /** False when an earlier answer makes [a] pointless. */
    fun applies(a: Ask, facts: Map<String, Fact>) = a.gate?.open(facts) ?: true

    // ── fixed questions ──

    val WHEN = Ask("when", "started", "When did it start?", Kind.CHOICE, listOf(
        Choice("just now", "Just now", Lang.words("now")),
        Choice("today", "Earlier today", Lang.words("today")),
        Choice("yesterday", "Yesterday", Lang.words("yesterday")),
        Choice("a few days", "A few days ago", Lang.words("days")),
        Choice("a week or more", "A week or more", Lang.words("week")),
    ))

    val COUNT = Ask("count", "count", "How many times today?", Kind.NUMBER, listOf(
        Choice("1", "Once"), Choice("2", "2 times"), Choice("3", "3 times"), Choice("4", "4 times"), Choice("6", "5 or more"),
    ))

    val WHERE = Ask("where", "site", "Show me where it is.", Kind.BODY)

    val DEPTH = Ask("depth", "depth", "How deep does it feel?", Kind.CHOICE, listOf(
        Choice("on the skin", "On the skin", listOf("skin", "surface", "outside", "upar", "mele", "tolu", "charma"), "depth_skin"),
        Choice("just under the skin", "Just under the skin", listOf("under the skin", "below the skin", "just under", "andar thoda"), "depth_under"),
        Choice("in the muscle", "In the muscle", listOf("muscle", "muscles", "flesh", "mansapeshi", "sathai", "nas"), "depth_muscle"),
        Choice("deep inside", "Deep inside", listOf("deep", "inside", "inner", "andar", "ullae", "ulle", "lopala", "organ"), "depth_deep"),
        Choice("in the bone or joint", "In the bone or joint", listOf("bone", "joint", "haddi", "jod", "elumbu", "moottu", "emuka", "keelu"), "depth_bone"),
    ))

    val SEVERITY = Ask("severity", "severity", "How bad is it?", Kind.SCALE, listOf(
        Choice("2", "A little", listOf("little", "a little", "slight", "mild", "thoda", "halka", "konjam", "koncham", "swalpa", "kurachu"), "face_1"),
        Choice("4", "Some", listOf("some", "medium", "moderate", "okay", "theek", "paravaillai", "madhyam"), "face_2"),
        Choice("6", "Bad", listOf("bad", "quite bad", "zyada", "jada", "adhigam", "ekkuva", "jaasti"), "face_3"),
        Choice("8", "Very bad", listOf("very bad", "severe", "a lot", "bahut", "bohot", "romba", "chala", "tumba", "valare"), "face_4"),
        Choice("10", "Worst ever", listOf("worst", "unbearable", "can't bear", "sahan nahi", "thaanga mudiyala", "bharisalagadu"), "face_5"),
    ))

    /** Pain scale (Wong-Baker FACES, 0–10). The same scale, reworded, measures itch, burning and tingling. */
    private val PAIN_CHOICES = listOf(
        Choice("0", "No hurt", listOf("no pain", "none", "zero", "nothing", "no hurt", "dard nahi", "vali illa"), "face_0"),
        Choice("2", "Hurts a little", listOf("little", "a little", "slight", "mild", "thoda", "halka", "konjam", "koncham", "swalpa"), "face_1"),
        Choice("4", "Hurts a bit more", listOf("bit more", "some", "medium", "moderate", "kuch", "oralavu"), "face_2"),
        Choice("6", "Hurts even more", listOf("even more", "quite bad", "bad", "zyada", "jada", "adhigam", "ekkuva"), "face_3"),
        Choice("8", "Hurts a lot", listOf("a lot", "very bad", "severe", "bahut", "bohot", "romba", "chala", "tumba"), "face_4"),
        Choice("10", "Worst hurt", listOf("worst", "unbearable", "cant bear", "sahan nahi", "thaanga mudiyala"), "face_5"),
    )
    val PAIN = Ask("pain", "severity", "How much does it hurt? Say a number from 0 to 10.", Kind.SCALE, PAIN_CHOICES)
    val ITCH = Ask("itch", "severity", "How itchy is it? From 0 to 10.", Kind.SCALE, PAIN_CHOICES.mapIndexed { i, c -> c.copy(label = listOf("Not itchy", "A little itchy", "Quite itchy", "Very itchy", "Very, very itchy", "Worst itch")[i]) })
    val STRENGTH = Ask("strength", "severity", "How strong is it? From 0 to 10.", Kind.SCALE, PAIN_CHOICES.mapIndexed { i, c -> c.copy(label = listOf("None", "A little", "Some", "Strong", "Very strong", "Worst ever")[i]) })

    val BURN_LOOK = Ask("burnLook", "burnDepth", "What does the burn look like?", Kind.CHOICE, listOf(
        Choice("red, no blisters", "Red, no blisters", listOf("red", "just red", "no blisters", "laal"), "burn_1"),
        Choice("blisters", "Blisters", listOf("blister", "blisters", "bubbles", "chhale", "koppulam", "boppalu"), "burn_2"),
        Choice("white, brown or black", "White, brown or black", listOf("white", "brown", "black", "charred", "safed", "kaala", "karuppu", "vellai", "no feeling"), "burn_3"),
    ))
    val BURN_SIZE = Ask("burnSize", "burnSize", "How big is it, compared with the palm of your hand?", Kind.CHOICE, listOf(
        Choice("smaller than a coin", "Smaller than a coin", listOf("small", "coin", "tiny", "chhota", "chinna"), "size_1"),
        Choice("about a palm", "About one palm", listOf("palm", "hand", "one palm", "hatheli", "ullangai"), "size_2"),
        Choice("bigger than a palm", "Bigger than a palm", listOf("bigger", "large", "big", "more than", "bada", "periya", "pedda"), "size_3"),
    ))

    val CHARACTER = Ask("character", "character", "What does it feel like?", Kind.MULTI, listOf(
        Choice("sharp", "Sharp", listOf("sharp", "tez"), "feel_sharp"),
        Choice("dull", "Dull ache", listOf("dull", "ache", "aching", "heavy", "bhaari", "dard"), "feel_dull"),
        Choice("burning", "Burning", listOf("burning", "burn", "jalan", "erichal", "erichchal", "manta", "urige"), "feel_burning"),
        Choice("throbbing", "Throbbing", listOf("throbbing", "pounding", "beating", "dhak dhak", "thudippu", "pulsating"), "feel_throbbing"),
        Choice("cramping", "Cramping", listOf("cramp", "cramping", "twisting", "ainthan", "marodh", "pidippu"), "feel_cramping"),
        Choice("pressing", "Pressing", listOf("pressing", "pressure", "squeezing", "tight", "dabav", "azhuththam"), "feel_pressing"),
        Choice("stabbing", "Stabbing", listOf("stabbing", "pricking", "needle", "chubhan", "kuthal", "kuththal", "podichinattu"), "feel_stabbing"),
        Choice("tingling", "Tingling", listOf("tingling", "pins and needles", "numb", "jhanjhanahat", "kooch", "marathu"), "feel_tingling"),
    ))

    val MORE = Ask("more", "more", "Can you tell me a little more? It helps your doctor.", Kind.YESNO)

    val PATTERN = Ask("pattern", "pattern", "Is it there all the time, or does it come and go?", Kind.CHOICE, listOf(
        Choice("constant", "All the time", listOf("all the time", "always", "constant", "continuous", "hamesha", "lagatar", "eppavum", "epudu")),
        Choice("comes and goes", "Comes and goes", listOf("comes and goes", "sometimes", "on and off", "kabhi kabhi", "appappo", "appudappudu")),
    ), core = false)

    val COURSE = Ask("course", "course", "Is it getting better, worse, or the same?", Kind.CHOICE, listOf(
        Choice("better", "Better", listOf("better", "improving", "theek", "kuraivu")),
        Choice("same", "Same", listOf("same", "no change", "waisa hi", "appadiye")),
        Choice("worse", "Worse", listOf("worse", "getting worse", "zyada", "adhigam")),
    ), core = false)

    val IMPACT = Ask("impact", "impact", "Is it stopping you from doing things?", Kind.CHOICE, listOf(
        Choice("no", "No", listOf("no", "nahi", "illai")),
        Choice("some", "A little", listOf("a little", "some", "thoda", "konjam")),
        Choice("a lot", "A lot", listOf("a lot", "very much", "bahut", "romba")),
    ), core = false)

    val WORSE = Ask("worse", "worse", "What makes it worse?", Kind.MULTI, listOf(
        Choice("moving", "Moving", listOf("moving", "move", "hilna", "asaivu")), Choice("walking", "Walking", listOf("walking", "walk", "chalna", "nadakka")),
        Choice("eating", "Eating", listOf("eating", "food", "khana", "saapadu", "annam")), Choice("lying down", "Lying down", listOf("lying", "lie down", "letna", "padukka")),
        Choice("breathing in", "Breathing in", listOf("breathing", "breath", "saans", "moochu")), Choice("nothing", "Nothing", listOf("nothing", "kuch nahi", "onnum illa", "emi ledu")),
    ), core = false)

    val BETTER = Ask("better", "better", "What makes it better?", Kind.MULTI, listOf(
        Choice("rest", "Rest", listOf("rest", "aaram", "oyvu")), Choice("medicine", "Medicine", listOf("medicine", "tablet", "dawai", "dava", "marundhu", "mandu")),
        Choice("lying down", "Lying down", listOf("lying", "lie down", "letna", "padukka")), Choice("warmth", "Warmth", listOf("warm", "heat", "hot", "garam", "soodu")),
        Choice("food", "Eating", listOf("eating", "food", "khana", "saapadu")), Choice("nothing", "Nothing", listOf("nothing", "kuch nahi", "onnum illa", "emi ledu")),
    ), core = false)

    val TOOK_MED = Ask("tookMed", "tookMedicine", "Did you take any medicine for it?", Kind.YESNO, core = false)
    val WHICH_MED = Ask("whichMed", "medicineTaken", "Which medicine? Just say its name.", Kind.FREE, core = false)
    val ANYTHING = Ask("anything", "note", "Anything else you want your doctor to know?", Kind.FREE, core = false)

    private val NOT_LOCATABLE = setOf("fever", "chills", "tired", "weakness", "unwell", "no_appetite", "weight_loss", "night_sweats", "thirsty", "dehydrated",
        "dizzy", "fainted", "fits", "confusion", "memory", "tremor", "balance", "hearing_loss", "ringing_ears", "runny_nose", "blocked_nose", "sneeze",
        "no_smell", "blurred_vision", "vision_loss", "double_vision", "floaters", "cough", "breathless", "wheeze", "palpitations", "low_oxygen", "snoring",
        "high_bp", "low_bp", "nausea", "vomiting", "loose_motions", "constipation", "burp", "hiccup", "low_mood", "anxious", "cant_sleep", "sleep_too_much",
        "lonely", "hallucination", "self_harm", "low_sugar", "high_sugar", "pale", "side_effect", "frequent_urine", "no_urine", "leaking_urine",
        "blood_urine", "blood_stool", "black_stool", "burning_urine", "trouble_walking", "trouble_eating", "toilet_accident", "hot_flush", "jaundice",
        "vomit_blood", "cough_blood", "choking", "dry_mouth", "hoarse", "near_fall", "heavy_bleeding", "postmeno_bleeding", "discharge", "face_droop", "speech_trouble")
    private val SHALLOW_REGIONS = setOf("head", "temple", "face", "eye", "ear", "nose", "mouth", "tooth", "throat", "mind")
    private val COUNTABLE = setOf("vomiting", "loose_motions", "fainted", "fall", "near_fall", "nosebleed", "fits", "palpitations", "cough_blood",
        "vomit_blood", "blood_stool", "black_stool", "burp", "sneeze", "toilet_accident", "leaking_urine")
    private val NO_SEVERITY = setOf("fainted", "fits", "fall", "near_fall", "choking", "sneeze", "burp", "hiccup", "black_stool", "blood_stool",
        "blood_urine", "high_bp", "low_bp", "low_sugar", "high_sugar", "low_oxygen", "self_harm", "confusion", "memory")

    private val BURNS = setOf("burn", "sunburn")
    private val ITCHY = setOf("itching", "itchy_eyes", "hives", "fungal", "rash")
    private val SENSATIONS = setOf("tingling", "numbness", "foot_numb", "nausea", "dizzy", "palpitations", "breathless", "anxious", "tired", "weakness", "chills", "hot_flush")

    /** Pain and burning get the 0–10 pain scale; itch and other sensations get the same scale in their own words. */
    fun scaleFor(p: Problem): Ask = when {
        p.id in ITCHY -> ITCH
        p.id in SENSATIONS -> STRENGTH
        painful(p) -> PAIN
        else -> SEVERITY
    }

    fun painful(p: Problem) = p.id in BURNS || "character" in p.fields || p.glyph in setOf("pain", "throb", "press", "flame", "bandage", "bite", "sting", "cast", "swell", "bruise") ||
        p.label.contains("pain", true) || p.label.contains("ache", true)

    fun locatable(p: Problem) = p.id !in NOT_LOCATABLE && p.region !in setOf("whole", "mind")
    fun deepable(p: Problem) = locatable(p) && p.region !in SHALLOW_REGIONS

    /** Core questions for [p], skipping anything already known. */
    fun core(cat: Catalogue, p: Problem, facts: Map<String, Fact>, age: Int? = null): List<Ask> {
        val out = ArrayList<Ask>()
        fun add(a: Ask) { if (!facts.containsKey(a.field)) out += a }
        add(WHEN)
        coreDanger(cat, p).forEach(::add)
        if (locatable(p)) add(WHERE)
        if (p.id in BURNS) { add(BURN_LOOK); add(BURN_SIZE) }
        // Core is at most 5 and always keeps severity (last). Burn size moves to "Tell more" if room is short.
        val sev = scaleFor(p).takeIf { p.id !in NO_SEVERITY && !facts.containsKey(it.field) }
        return out.take(if (sev != null) 4 else 5) + listOfNotNull(sev)
    }

    /** Extended questions (only after the person agrees to tell more). */
    fun extended(cat: Catalogue, p: Problem, facts: Map<String, Fact>, age: Int? = null): List<Ask> {
        val out = ArrayList<Ask>()
        fun add(a: Ask) { if (!facts.containsKey(a.field) && out.none { it.field == a.field }) out += a.copy(core = false) }
        fun isAgeExcluded(minAge: Int?, maxAge: Int?): Boolean {
            if (age == null) return false
            if (minAge != null && age < minAge) return true
            if (maxAge != null && age > maxAge) return true
            return false
        }
        if (p.id in COUNTABLE) add(COUNT)
        if (deepable(p)) add(DEPTH)
        if ("character" in p.fields) add(CHARACTER)
        dangerQuestions(cat, p).filterNot { a -> coreDanger(cat, p).any { it.field == a.field } }.forEach(::add)
        p.followUps.mapNotNull { cat.questions[it] }.filter { it.priority < 85 }.forEach { q ->
            if (!isAgeExcluded(q.minAge, q.maxAge)) fromQuestion(cat, q)?.let(::add)
        }
        // other catalogue fields that have simple answers
        for (f in p.fields) {
            val field = cat.field(f) ?: continue
            if (f in setOf("severity", "count", "character", "side", "context", "worse", "better", "note", "radiation", "duration", "impact", "onset", "pattern", "reading", "temperature", "hours", "weeks", "pillows", "timeOnFloor", "sleepHours")) continue

            if (isAgeExcluded(field.minAge, field.maxAge)) continue

            when (field.type) {
                FieldType.YESNO -> {
                    val text = field.help?.split("\n")?.firstOrNull() ?: yesNoText(field.label)
                    val help = field.help ?: Help.of("f_$f", f)
                    add(Ask("f_$f", f, text, Kind.YESNO, core = false, danger = field.danger, gate = field.gate, help = help))
                }
                FieldType.CHOICE -> {
                    val text = field.help?.split("\n")?.firstOrNull() ?: choiceText(f, field.label)
                    val help = field.help ?: Help.of("f_$f", f)
                    add(Ask("f_$f", f, text, Kind.CHOICE, field.choices.map { Choice(it, it.replaceFirstChar(Char::uppercase), listOf(it)) }, core = false, gate = field.gate, help = help))
                }
                else -> {}
            }
        }
        if (locatable(p) || "pattern" in p.fields || p.region == "whole") add(PATTERN)
        if (locatable(p)) { add(WORSE); add(BETTER) }
        if (p.id in BURNS) add(BURN_SIZE)
        add(COURSE)
        add(IMPACT)
        add(TOOK_MED)
        add(ANYTHING)
        // Extended questions are optional ("tell more"), gated, and can be stopped at any time.
        return out
    }

    private fun dangerQuestions(cat: Catalogue, p: Problem): List<Ask> {
        val fromFollowUps = p.followUps.mapNotNull { cat.questions[it] }.filter { it.priority >= 85 }.sortedByDescending { it.priority }.mapNotNull { fromQuestion(cat, it)?.copy(danger = true) }
        // audited problems keep their danger yes/no questions as fields flagged `danger`
        val fromFields = p.fields.mapNotNull { f ->
            val fd = cat.field(f) ?: return@mapNotNull null
            if (fd.danger && fd.type == FieldType.YESNO) Ask("f_$f", f, yesNoText(fd.label), Kind.YESNO, core = true, danger = true, help = fd.help ?: Help.of("f_$f", f), gate = fd.gate) else null
        }
        return (fromFollowUps + fromFields).distinctBy { it.field }
    }

    /** The (at most two) ungated danger questions asked first. */
    private fun coreDanger(cat: Catalogue, p: Problem): List<Ask> = dangerQuestions(cat, p).filter { it.gate == null }.take(2)

    private fun fromQuestion(cat: Catalogue, q: Question): Ask? {
        val help = q.help ?: Help.of(q.id, q.field)
        return when (q.type) {
            FieldType.YESNO -> Ask(q.id, q.field, q.ask, Kind.YESNO, gate = q.gate, help = help)
            FieldType.NUMBER -> Ask(q.id, q.field, q.ask, Kind.NUMBER, gate = q.gate, help = help)
            FieldType.TEMP -> Ask(q.id, q.field, q.ask, Kind.TEMP, gate = q.gate, help = help)
            FieldType.SCALE -> SEVERITY.copy(gate = q.gate, help = help)
            else -> null
        }
    }

    private fun yesNoText(label: String) = when {
        label.startsWith("Can ") || label.startsWith("Could ") -> "$label?"
        else -> "Is there any: ${label.lowercase()}?"
    }

    private fun choiceText(field: String, label: String) = when (field) {
        "colour" -> "What colour was it?"
        "phlegmColour" -> "What colour was the phlegm?"
        "content" -> "What came out?"
        "stoolType" -> "What was it like?"
        "dryWet" -> "Is the cough dry, or wet?"
        else -> "$label?"
    }

    // ── understanding one spoken answer ──

    sealed interface Heard {
        data class Value(val value: Any, val label: String) : Heard
        data object Skip : Heard
        data object Done : Heard
        data object Unclear : Heard
    }

    /** [text] is already transliterated to Latin letters (see Lang.toLatin). */
    fun understand(a: Ask, raw: String): Heard {
        val t = " " + Normalize.text(raw) + " "
        if (Lang.any(t, "done")) return Heard.Done
        if (Lang.any(t, "skip")) return Heard.Skip
        return when (a.kind) {
            Kind.YESNO -> when {
                Lang.any(t, "no") -> Heard.Value(false, "No")
                Lang.any(t, "yes") -> Heard.Value(true, "Yes")
                else -> Heard.Unclear
            }
            Kind.CHOICE, Kind.SCALE -> {
                // a spoken 0–10 number also works for "how bad"
                if (a.kind == Kind.SCALE) Lang.number(t)?.takeIf { it in 0..10 }?.let { return Heard.Value(it, "$it out of 10") }
                val c = a.choices.firstOrNull { c -> (c.words + c.label.lowercase()).any { w -> Regex("\\b${Regex.escape(w.lowercase())}\\b").containsMatchIn(t) } }
                if (c != null) Heard.Value(if (a.kind == Kind.SCALE) c.value.toInt() else c.value, c.label) else Heard.Unclear
            }
            Kind.MULTI -> {
                val cs = a.choices.filter { c -> (c.words + c.label.lowercase()).any { w -> Regex("\\b${Regex.escape(w.lowercase())}\\b").containsMatchIn(t) } }
                if (cs.isNotEmpty()) Heard.Value(cs.map { it.value }, cs.joinToString(", ") { it.label }) else Heard.Unclear
            }
            Kind.NUMBER -> Lang.number(t)?.let { Heard.Value(it, "$it") } ?: Heard.Unclear
            Kind.TEMP -> Regex("\\b(\\d{2,3}(?:\\.\\d)?)\\b").find(t)?.groupValues?.get(1)?.toDoubleOrNull()?.let { v ->
                val f = if (v in 34.0..43.0) Math.round((v * 9 / 5 + 32) * 10) / 10.0 else v
                if (f in 93.0..110.0) Heard.Value(f, "$f °F") else null
            } ?: Heard.Unclear
            Kind.FREE -> if (raw.isNotBlank()) Heard.Value(raw.trim(), raw.trim()) else Heard.Unclear
            Kind.BODY -> Heard.Unclear
        }
    }
}

/**
 * Indian languages for answers. Speech from the phone's recogniser arrives in its own script; it is
 * transliterated to Latin letters first, then matched against these romanised words.
 */
object Lang {
    data class Language(val tag: String, val name: String, val native: String)

    val ALL = listOf(
        Language("en-IN", "English", "English"), Language("hi-IN", "Hindi", "हिन्दी"), Language("ta-IN", "Tamil", "தமிழ்"),
        Language("te-IN", "Telugu", "తెలుగు"), Language("kn-IN", "Kannada", "ಕನ್ನಡ"), Language("ml-IN", "Malayalam", "മലയാളം"),
        Language("mr-IN", "Marathi", "मराठी"), Language("bn-IN", "Bengali", "বাংলা"), Language("gu-IN", "Gujarati", "ગુજરાતી"),
        Language("pa-IN", "Punjabi", "ਪੰਜਾਬੀ"), Language("or-IN", "Odia", "ଓଡ଼ିଆ"), Language("ur-IN", "Urdu", "اردو"),
    )

    private val WORDS = mapOf(
        "yes" to listOf("yes", "yeah", "yep", "yup", "sure", "haan", "han", "ha", "haa", "ji", "ji haan", "aamaa", "aama", "aam", "amam", "avunu", "aunu", "haudu", "houdu", "howdu",
            "athe", "ate", "atey", "ho", "hoy", "hyan", "hoi", "hya", "hmm", "ok", "okay", "correct", "sari", "sahi", "theek"),
        "no" to listOf("no", "nope", "not", "nahi", "nahin", "na", "naa", "illai", "illa", "ille", "kaadu", "ledu", "leda", "alla", "nako", "nai", "nei", "never"),
        "skip" to listOf("skip", "don't know", "dont know", "i don't know", "not sure", "pata nahi", "maalum nahi", "theriyala", "theriyadhu", "teliyadu", "gottilla", "ariyilla", "next"),
        "done" to listOf("that's all", "thats all", "done", "finished", "enough", "stop", "bas", "bus", "khatam", "podhum", "pothum", "chaalu", "saaku", "mathi", "mati"),
        "now" to listOf("now", "just now", "right now", "abhi", "abhi abhi", "ippo", "ippothan", "ippudu", "ippudey", "iga", "ippol", "atta", "ekhon", "hamna"),
        "today" to listOf("today", "this morning", "earlier", "aaj", "aaj subah", "innaiku", "inniki", "indru", "eeroju", "ivattu", "innu", "aaj sakali", "aajke"),
        "yesterday" to listOf("yesterday", "last night", "kal", "kal raat", "nethu", "netru", "ninna", "ninne", "innale", "kaal", "gatkal"),
        "days" to listOf("few days", "some days", "two days", "three days", "2 days", "3 days", "kuch din", "do din", "teen din", "konja naal", "rendu naal", "moonu naal", "rendu moonu naal", "konni rojulu", "days"),
        "week" to listOf("week", "a week", "weeks", "month", "long time", "hafta", "mahina", "bahut din", "vaaram", "maasam", "romba naal", "vaara", "varam", "long"),
    )

    fun words(key: String) = WORDS[key].orEmpty()
    fun any(t: String, key: String) = WORDS[key].orEmpty().any { w -> Regex("\\b${Regex.escape(w)}\\b").containsMatchIn(t) }

    private val NUMS: Map<String, Int> = buildMap {
        val lists = listOf(
            listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"),
            listOf("shunya", "ek", "do", "teen", "char", "paanch", "chhe", "saat", "aath", "nau", "das"),                  // Hindi / Urdu / Punjabi
            listOf("poojyam", "onnu", "rendu", "moonu", "naalu", "anju", "aaru", "ezhu", "ettu", "onbadhu", "pathu"),       // Tamil / Malayalam
            listOf("sunna", "okati", "rendu", "moodu", "naalugu", "aidu", "aaru", "edu", "enimidi", "tommidi", "padi"),    // Telugu
            listOf("sonne", "ondu", "eradu", "mooru", "naalku", "aidu", "aaru", "elu", "entu", "ombattu", "hattu"),         // Kannada
            listOf("shunya", "ek", "don", "teen", "chaar", "paach", "saha", "saat", "aath", "nau", "daha"),                 // Marathi
            listOf("shunyo", "ek", "dui", "tin", "char", "paanch", "chhoy", "shaat", "aat", "noy", "dosh"),                 // Bengali
        )
        for (l in lists) l.forEachIndexed { i, w -> putIfAbsent(w, i) }
        put("once", 1); put("twice", 2); put("thrice", 3); put("ek baar", 1); put("do baar", 2); put("oru thadava", 1); put("rendu thadava", 2)
    }

    /** A number said as digits or as a word in English or an Indian language. */
    fun number(t: String): Int? {
        Regex("\\b(\\d{1,3})\\b").find(t)?.let { return it.groupValues[1].toInt() }
        return NUMS.entries.sortedByDescending { it.key.length }.firstOrNull { (w, _) -> Regex("\\b${Regex.escape(w)}\\b").containsMatchIn(t) }?.value
    }
}
