package com.suryaprakash.medlog.feedback

import android.content.Context
import org.json.JSONObject
import java.io.File

/** What the report is about. The slug is the GitHub label. */
enum class Category(val label: String, val slug: String) {
    BUG("Bug", "bug"),
    WRONG("Wrong information", "wrong-information"),
    HARD("Hard to use", "hard-to-use"),
    IDEA("Idea", "idea");

    companion object {
        fun fromSlug(s: String) = entries.firstOrNull { it.slug == s } ?: BUG
    }
}

/** Where a report is in its life. Only the phone can say VERIFIED, when the person taps "It works now". */
enum class Status { QUEUED, SENT, CLOSED, VERIFIED }

/** One report, as saved in meta.json next to its picture. */
data class Report(
    val id: String,
    val createdAt: Long,
    val category: Category,
    val note: String,
    /** the page the person was on */
    val route: String,
    val version: String,
    val role: String,
    val device: String,
    val android: String,
    val status: Status = Status.QUEUED,
    /** GitHub issue number, 0 until it is created */
    val issue: Int = 0,
    val hasShot: Boolean = false,
    /** the picture was not sent because the tracker is not private (or could not be checked) */
    val textOnly: Boolean = false,
    /** link to the uploaded picture, once it is up */
    val imageUrl: String = "",
    /** last thing that went wrong, kept for finding out why */
    val error: String = "",
)

/** Pure (de)serialisation, so it can be tested without a phone. */
object ReportJson {
    fun toJson(r: Report): String = JSONObject().apply {
        put("id", r.id); put("createdAt", r.createdAt); put("category", r.category.slug); put("note", r.note)
        put("route", r.route); put("version", r.version); put("role", r.role); put("device", r.device); put("android", r.android)
        put("status", r.status.name); put("issue", r.issue); put("hasShot", r.hasShot); put("textOnly", r.textOnly)
        put("imageUrl", r.imageUrl); put("error", r.error)
    }.toString()

    fun fromJson(s: String): Report? = runCatching {
        val o = JSONObject(s)
        Report(
            id = o.getString("id"), createdAt = o.optLong("createdAt"), category = Category.fromSlug(o.optString("category")),
            note = o.optString("note"), route = o.optString("route"), version = o.optString("version"), role = o.optString("role"),
            device = o.optString("device"), android = o.optString("android"),
            status = Status.entries.firstOrNull { it.name == o.optString("status") } ?: Status.QUEUED,
            issue = o.optInt("issue"), hasShot = o.optBoolean("hasShot"), textOnly = o.optBoolean("textOnly"),
            imageUrl = o.optString("imageUrl"), error = o.optString("error"),
        )
    }.getOrNull()
}

/** Reports kept on the phone in filesDir/feedback/{id}/ (shot.jpg and meta.json). */
class FeedbackStore(private val root: File) {
    constructor(ctx: Context) : this(File(ctx.filesDir, "feedback"))

    private fun dir(id: String) = File(root, id)
    fun shotFile(id: String) = File(dir(id), "shot.jpg")

    @Synchronized fun save(r: Report, jpeg: ByteArray? = null) {
        val d = dir(r.id); d.mkdirs()
        if (jpeg != null) shotFile(r.id).writeBytes(jpeg)
        File(d, "meta.json").writeText(ReportJson.toJson(r))
    }

    @Synchronized fun load(id: String): Report? = File(dir(id), "meta.json").takeIf { it.exists() }?.let { ReportJson.fromJson(it.readText()) }

    /** Newest first. */
    @Synchronized fun all(): List<Report> =
        (root.listFiles() ?: emptyArray()).mapNotNull { load(it.name) }.sortedByDescending { it.createdAt }

    fun pending(): List<Report> = all().filter { it.issue == 0 }

    fun shot(id: String): ByteArray? = shotFile(id).takeIf { it.exists() }?.readBytes()
}
