package com.suryaprakash.medlog.clinical

import org.json.JSONObject

/** One problem the person can report ("Vomiting"), with everything needed to record it the way a doctor takes a history. */
data class Problem(
    val id: String,
    val label: String,
    val group: String,
    val dept: String,
    val region: String,
    val glyph: String,
    val synonyms: List<String>,
    val fields: List<String>,
    val followUps: List<String>,
    /** The problem itself is a danger sign (fainting, vomiting blood...). */
    val red: Boolean,
)

enum class FieldType { NUMBER, SCALE, CHOICE, MULTI, TEXT, YESNO, TEMP }

data class Field(
    val id: String,
    val label: String,
    val type: FieldType,
    val choices: List<String>,
    val unit: String?,
    val danger: Boolean,
    val gate: Interview.Gate? = null,
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val help: String? = null,
)

data class Question(
    val id: String,
    val field: String,
    val ask: String,
    val type: FieldType,
    val priority: Int,
    val gate: Interview.Gate? = null,
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val help: String? = null,
)

data class Cond(val field: String, val isValue: Any? = null, val gte: Double? = null)

data class RedFlag(
    val id: String,
    val problems: List<String>,
    val all: List<Cond>,
    val level: String,
    val reason: String,
    val say: String,
)

data class Group(val id: String, val label: String, val glyph: String)

/**
 * The clinical content, loaded from assets/clinical/catalogue.json. The JSON is the single reviewed source:
 * a clinician edits it, tests check it, the app reads it.
 */
class Catalogue(
    val version: String,
    val reviewed: Boolean,
    val groups: List<Group>,
    val fields: Map<String, Field>,
    val questions: Map<String, Question>,
    val problems: List<Problem>,
    val redFlags: List<RedFlag> = emptyList(),
) {
    private val byId = problems.associateBy { it.id }

    fun problem(id: String?): Problem? = id?.let { byId[it] }
    fun field(id: String): Field? = fields[id]
    fun inGroup(group: String) = problems.filter { it.group == group }

    companion object {
        private fun parseGate(json: JSONObject?): Interview.Gate? {
            if (json == null) return null
            val field = json.getString("field")
            val any = mutableSetOf<Any?>()
            json.optJSONArray("any")?.let { a ->
                for (i in 0 until a.length()) {
                    when {
                        a.isNull(i) -> any.add(null)
                        i < a.length() -> {
                            val v = a.get(i)
                            when (v) {
                                is Boolean -> any.add(v)
                                is Number -> any.add(v)
                                is String -> any.add(v)
                                else -> any.add(v.toString())
                            }
                        }
                    }
                }
            }
            val negate = json.optBoolean("not", false)
            return Interview.Gate(field, any, negate)
        }

        fun parse(json: String): Catalogue {
            val o = JSONObject(json)
            val groups = o.getJSONArray("groups").let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map { Group(it.getString("id"), it.getString("label"), it.getString("glyph")) }
            }
            val fo = o.getJSONObject("fields")
            val fields = fo.keys().asSequence().associateWith { k ->
                val f = fo.getJSONObject(k)
                Field(
                    id = k,
                    label = f.getString("label"),
                    type = FieldType.valueOf(f.getString("type").uppercase()),
                    choices = f.optJSONArray("choices")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                    unit = f.optString("unit").ifEmpty { null },
                    danger = f.optBoolean("danger"),
                    gate = parseGate(f.optJSONObject("gate")),
                    minAge = f.optInt("minAge").takeIf { it != 0 },
                    maxAge = f.optInt("maxAge").takeIf { it != 0 },
                    help = f.optString("help").ifEmpty { null },
                )
            }
            val qo = o.getJSONObject("questions")
            val questions = qo.keys().asSequence().associateWith { k ->
                val q = qo.getJSONObject(k)
                Question(
                    k,
                    q.getString("field"),
                    q.getString("ask"),
                    FieldType.valueOf(q.getString("type").uppercase()),
                    q.optInt("priority"),
                    gate = parseGate(q.optJSONObject("gate")),
                    minAge = q.optInt("minAge").takeIf { it != 0 },
                    maxAge = q.optInt("maxAge").takeIf { it != 0 },
                    help = q.optString("help").ifEmpty { null },
                )
            }
            val pa = o.getJSONArray("problems")
            val problems = (0 until pa.length()).map { pa.getJSONObject(it) }.map { p ->
                fun list(key: String) = p.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                Problem(
                    id = p.getString("id"),
                    label = p.getString("label"),
                    group = p.getString("group"),
                    dept = p.getString("dept"),
                    region = p.getString("region"),
                    glyph = p.getString("glyph"),
                    synonyms = list("syn"),
                    fields = list("fields"),
                    followUps = list("fu"),
                    red = p.optBoolean("red"),
                )
            }
            val redFlags = o.optJSONArray("redFlags")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map { rf ->
                    RedFlag(
                        id = rf.getString("id"),
                        problems = rf.optJSONArray("problems")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                        all = rf.optJSONArray("all")?.let { a ->
                            (0 until a.length()).map { a.getJSONObject(it) }.map { c ->
                                Cond(
                                    field = c.getString("field"),
                                    isValue = when {
                                        c.has("is") -> {
                                            val v = c.get("is")
                                            when (v) {
                                                is Boolean -> v
                                                is Number -> v
                                                is String -> v
                                                else -> v.toString()
                                            }
                                        }
                                        else -> null
                                    },
                                    gte = c.optDouble("gte").takeIf { it != 0.0 },
                                )
                            }
                        } ?: emptyList(),
                        level = rf.getString("level"),
                        reason = rf.getString("reason"),
                        say = rf.getString("say"),
                    )
                }
            } ?: emptyList()
            return Catalogue(o.getString("version"), o.optBoolean("reviewed"), groups, fields, questions, problems, redFlags)
        }
    }
}
