package com.suryaprakash.medlog.clinical

/**
 * Help text for questions: what the question means, then why it is asked.
 * Two lines per entry, ≤12 words per line.
 */
object Help {
    private val BY_FIELD = mapOf(
        "f_dryWet" to "Dry means nothing comes up. Wet means you can feel phlegm.\nWet and dry coughs have different causes.",
        "f_phlegm" to "Phlegm is the thick wet stuff that comes up from the chest when you cough.\nColour and amount help your doctor tell what is wrong.",
        "f_phlegmColour" to "Colour tells your doctor what is happening.\nDark or deep colours can mean older infection or blood.",
        "f_shade" to "Colour tells your doctor what is happening.\nDark or deep colours can mean older infection or blood.",
        "f_colour" to "Colour tells your doctor what is happening.\nDark or deep colours can mean older infection or blood.",

        // Core and fixed questions
        "when" to "Knowing when it started helps understand the cause.\nSudden things need urgent attention.",
        "count" to "How often it happens tells doctors how serious it is.\nFrequent episodes need more care.",
        "where" to "Where it is helps find what caused it.\nPain in ribs is different from stomach pain.",
        "depth" to "Knowing how deep helps find the problem.\nSurface rashes need different care than deep pain.",
        "severity" to "How bad it is tells doctors how urgent it is.\nVery severe pain needs urgent attention.",
        "pain" to "How much it hurts helps doctors understand the problem.\nIntense pain may mean something serious.",
        "itch" to "How itchy something is helps find what caused it.\nSevere itch affects daily living.",
        "strength" to "How strong a sensation is helps doctors understand it.\nWorsening weakness needs urgent attention.",
        "burnLook" to "What the burn looks like shows how deep it is.\nCharred skin means a severe burn.",
        "burnSize" to "Burn size tells doctors how serious it is.\nLarge burns need urgent hospital care.",
        "character" to "What pain feels like helps doctors find the cause.\nSharp pain is different from aching pain.",

        // Pattern and modifiers
        "pattern" to "Whether pain comes and goes helps doctors understand it.\nConstant pain is different from intermittent.",
        "worse" to "What makes it worse helps find the cause.\nPain worse with movement suggests muscle problems.",
        "better" to "What helps find effective treatments.\nPain better with rest suggests muscle strain.",

        // Pattern and follow-up
        "more" to "More details help doctors make better decisions.\nYou can stop anytime if you're tired.",
        "tookMed" to "Whether you took medicine helps doctors understand what you tried.\nSome medicines change symptoms significantly.",
        "whichMed" to "Which medicine helps doctors see what worked or didn't.\nDifferent medicines help different problems.",
        "anything" to "Other details you think matter help your doctor.\nDon't hold back anything you think is important.",
    )

    /**
     * Get combined help text (what + why) for a question by id, or fall back to field.
     */
    fun of(id: String, field: String): String {
        return BY_FIELD[id] ?: BY_FIELD[field] ?: "$field.\nThis helps your doctor understand your condition."
    }

    /**
     * Extract the "what" part (first line).
     */
    fun what(help: String): String = help.split("\n").firstOrNull().orEmpty()

    /**
     * Extract the "why" part (second line).
     */
    fun why(help: String): String = help.split("\n").getOrNull(1).orEmpty()
}
