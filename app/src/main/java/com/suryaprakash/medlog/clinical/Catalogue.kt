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
)

data class Question(val id: String, val field: String, val ask: String, val type: FieldType, val priority: Int)

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
) {
    private val byId = problems.associateBy { it.id }

    fun problem(id: String?): Problem? = id?.let { byId[it] }
    fun field(id: String): Field? = fields[id]
    fun inGroup(group: String) = problems.filter { it.group == group }

    companion object {
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
                )
            }
            val qo = o.getJSONObject("questions")
            val questions = qo.keys().asSequence().associateWith { k ->
                val q = qo.getJSONObject(k)
                Question(k, q.getString("field"), q.getString("ask"), FieldType.valueOf(q.getString("type").uppercase()), q.optInt("priority"))
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
            return Catalogue(o.getString("version"), o.optBoolean("reviewed"), groups, fields, questions, problems)
        }
    }
}
