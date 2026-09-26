package com.suryaprakash.medlog.nlu

/**
 * Turns spoken text into a form the rules can read reliably:
 * lower case, no apostrophes ("can't" → "cant"), number words → digits
 * ("one fifty by ninety" → "150 by 90", "twice" → "2 times", "ninety nine point five" → "99.5").
 */
object Normalize {
    private val UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fourty" to 40, "fifty" to 50, "sixty" to 60,
        "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )
    private val FREQ = mapOf("once" to "1 times", "twice" to "2 times", "thrice" to "3 times")

    fun text(raw: String): String {
        var s = raw.lowercase()
            .replace("’", "'")
            .replace(Regex("(\\w)'(\\w)"), "$1$2")   // can't → cant, didn't → didnt
            .replace("°", " degrees ")
            .replace("%", " percent ")
            .replace(Regex("[\"“”!?;:()\\[\\]]"), " ")
            .replace(Regex("(?<=\\d),(?=\\d{3})"), "")    // 1,000 → 1000
        s = s.replace(Regex("\\s+"), " ").trim()
        s = words(s)
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /** Replaces runs of number words with digits, token by token. */
    private fun words(s: String): String {
        val tokens = s.split(" ").toMutableList()
        val out = ArrayList<String>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val f = FREQ[t]
            if (f != null) { out.add(f); i++; continue }
            val r = readNumber(tokens, i)
            if (r == null) { out.add(t); i++ } else { out.add(r.first); i = r.second }
        }
        return out.joinToString(" ")
    }

    private fun isNumWord(t: String) = t in UNITS || t in TENS || t == "hundred" || t == "a" || t == "and" || t == "point" || t == "half"

    /**
     * Reads a spoken number starting at [start]. Returns the digit string and the index after it.
     * Handles: "ninety nine", "one hundred and two", "one fifty" (=150, how BP is usually said),
     * "ninety nine point five", "two and a half", "a hundred".
     */
    private fun readNumber(tk: List<String>, start: Int): Pair<String, Int>? {
        fun w(i: Int) = tk.getOrNull(i)
        fun small(i: Int): Pair<Int, Int>? {           // 0..99 from words, returns value and next index
            val a = w(i) ?: return null
            TENS[a]?.let { tens ->
                val u = w(i + 1)?.let { UNITS[it] }
                return if (u != null && u in 1..9) (tens + u) to i + 2 else tens to i + 1
            }
            UNITS[a]?.let { return it to i + 1 }
            return null
        }
        val first = w(start) ?: return null
        if (first == "a" && w(start + 1) == "hundred") {
            var v = 100; var i = start + 2
            if (w(i) == "and") small(i + 1)?.let { v += it.first; i = it.second } else small(i)?.let { v += it.first; i = it.second }
            return v.toString() to i
        }
        if (first == "half" && w(start + 1) == "an" ) return null
        val s1 = small(start) ?: return null
        var value = s1.first
        var i = s1.second
        if (w(i) == "hundred" && value in 1..9) {
            value *= 100; i++
            if (w(i) == "and") { small(i + 1)?.let { value += it.first; i = it.second } }
            else small(i)?.let { value += it.first; i = it.second }
        } else if (value in 1..9 && w(i)?.let { it in TENS } == true) {
            // "one fifty" → 150, "one twenty five" → 125
            small(i)?.let { value = value * 100 + it.first; i = it.second }
        } else if (value in 1..9 && w(i)?.let { it in UNITS && UNITS[it]!! >= 10 } == true) {
            // "one ten" → 110
            value = value * 100 + UNITS[w(i)!!]!!; i++
        }
        var text = value.toString()
        if (w(i) == "point") {
            val d = w(i + 1)?.let { UNITS[it] }
            if (d != null && d in 0..9) { text = "$value.$d"; i += 2 }
        } else if (w(i) == "and" && w(i + 1) == "a" && w(i + 2) == "half") {
            text = "$value.5"; i += 3
        }
        return text to i
    }

    /** Splits into clauses: negation and facts never cross these. */
    fun clauses(s: String): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        val rx = Regex("[.,]\\s|\\s(but|also|though|although|then|and then|plus)\\s|\\.$")
        var last = 0
        rx.findAll(s).forEach { m ->
            if (m.range.first > last) out += last to s.substring(last, m.range.first).trim()
            last = m.range.last + 1
        }
        if (last < s.length) out += last to s.substring(last).trim()
        return out.filter { it.second.isNotBlank() }
    }
}
