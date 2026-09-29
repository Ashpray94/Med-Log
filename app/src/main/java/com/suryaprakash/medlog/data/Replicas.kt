package com.suryaprakash.medlog.data

import com.suryaprakash.medlog.MedLogApp
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Whose MedLog the screens show: null = this phone's own, else a person this (helper) phone helps. */
data class Viewed(val pairId: String, val name: String)

/**
 * The one place that knows "a replica is open". Screens read [state] (so they recompose on a switch); everything that would ring, text
 * or call from this phone for the person's data asks [active] / [notice] first. Background work never looks at it: it uses the own database.
 */
class Viewing {
    val state = MutableStateFlow<Viewed?>(null)
    val active: Boolean get() = state.value != null
    fun open(pairId: String, name: String) { state.value = Viewed(pairId, name) }
    fun back() { state.value = null }
    /** "This runs on Amma's phone." while a replica is open, else null. */
    fun notice(): String? = state.value?.let { "This runs on ${it.name.ifBlank { "their" }}${if (it.name.isBlank()) "" else "'s"} phone." }
}

/** File names and lifecycle of the replica databases (plan B.5). Pure, so the JVM tests cover it. */
object ReplicaFiles {
    private const val PREFIX = "medlog-"
    fun name(pairId: String) = PREFIX + pairId.map { if (it.isLetterOrDigit() || it == '-') it else '_' }.joinToString("") + ".db"
    fun channel(pairId: String) = "p-$pairId"
    /** The database file and its side files. */
    fun files(dir: File, pairId: String): List<File> = name(pairId).let { n -> listOf(n, "$n-wal", "$n-shm", "$n-journal").map { File(dir, it) } }
    fun delete(dir: File, pairId: String) = files(dir, pairId).fold(true) { ok, f -> (!f.exists() || f.delete()) && ok }
    /** Every replica file in [dir] (never the own medlog.db). */
    fun deleteAll(dir: File): Int = dir.listFiles { f -> f.name.startsWith(PREFIX) }.orEmpty().count { it.delete() }
    fun exists(dir: File, pairId: String) = File(dir, name(pairId)).exists()
}

/** Which replicas should run: a person is in the list AND their phone has handed over the family key. */
object ReplicaPlan {
    data class Diff(val attach: List<String>, val detach: List<String>)
    fun wanted(people: List<CaredFor>): List<CaredFor> = people.filter { it.familyKey.isNotBlank() }
    fun diff(wantedIds: Collection<String>, attached: Collection<String>) =
        Diff(wantedIds.filter { it !in attached }.distinct(), attached.filter { it !in wantedIds })
    /**
     * Same, but a channel whose family key changed (the person wiped and restored, so she has a NEW key) is in both lists: detach the runner
     * with the old key, then attach one with the new key (B78). [want] and [attached] map channel name to the family key (base64).
     */
    fun diffKeys(want: Map<String, String>, attached: Map<String, String>): Diff {
        val changed = want.filter { (n, k) -> attached[n]?.let { it != k } == true }.keys
        val d = diff(want.keys, attached.keys)
        return Diff((d.attach + changed).distinct(), (d.detach + changed).distinct())
    }
}

/** Opens (lazily) and drops the replica databases. Same key, schema and triggers as the own database. */
class Replicas(private val app: MedLogApp) {
    private val dbs = HashMap<String, MedDb>()
    private val repos = HashMap<String, Repo>()
    private val dir: File get() = app.getDatabasePath("medlog.db").parentFile!!

    @Synchronized fun db(pairId: String): MedDb = dbs.getOrPut(pairId) { MedDb.open(app, ReplicaFiles.name(pairId)) }
    @Synchronized fun repo(pairId: String): Repo = repos.getOrPut(pairId) { Repo(db(pairId), app.catalogue, app.describe) }

    /** Closes and deletes one person's replica. The caller detaches its sync channel first. */
    @Synchronized fun drop(pairId: String) {
        repos.remove(pairId)
        dbs.remove(pairId)?.let { runCatching { it.close() } }
        ReplicaFiles.delete(dir, pairId)
    }

    @Synchronized fun dropAll() {
        repos.clear()
        dbs.values.forEach { runCatching { it.close() } }
        dbs.clear()
        ReplicaFiles.deleteAll(dir)
    }
}
