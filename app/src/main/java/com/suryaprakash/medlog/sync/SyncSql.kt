package com.suryaprakash.medlog.sync

/** How a column travels: integer, real or text. */
enum class K { I, R, T }

/** One shared column. [nullable] columns may be null; the others get a default when a peer leaves them out. */
data class Col(val n: String, val k: K, val nullable: Boolean = false)

/** A local numeric id that travels as the uid of the row it points to: [json] is the wire field, [col] the local column. */
data class Fk(val json: String, val col: String, val parent: String)

/**
 * One shared table. [cols] are the columns that travel (the row's own id, uid, updatedAt and updatedBy never do: the last three
 * travel in the Op). Columns not listed (pairId, audioPath, photoPath, calendarEventId ...) stay on the phone.
 * The profile has no uid column: its uid is the fixed string "profile" and its id is always 1.
 */
data class TableSpec(val name: String, val cols: List<Col>, val fk: Fk? = null, val hasUid: Boolean = true) {
    /** Every local column whose change counts as a shared change. */
    val watched: List<String> get() = cols.map { it.n } + listOfNotNull(fk?.col)
}

/**
 * All the SQL of two-way sharing (database version 4), as plain strings so the phone (Room migration, onOpen) and the JVM tests
 * run exactly the same statements.
 */
object SyncSql {
    private fun t(n: String, nullable: Boolean = false) = Col(n, K.T, nullable)
    private fun i(n: String, nullable: Boolean = false) = Col(n, K.I, nullable)

    val PROFILE = TableSpec("profile", listOf(t("name"), t("dob"), t("sex"), t("bloodGroup"), t("hospitalId"), t("conditions"), t("allergies"),
        t("doctorName"), t("doctorPhone"), i("onBloodThinner"), t("notes"), t("plan")), hasUid = false)
    val HELPERS = TableSpec("helpers", listOf(t("name"), t("phone"), t("relation"), i("sos"), i("alerts"), i("canSeeNotes"), i("sortOrder")))
    val NOTES = TableSpec("notes", listOf(t("kind"), t("problemId", true), i("occurredAt"), i("createdAt"), t("transcript", true), t("details"),
        i("severity", true), i("count", true), t("triage"), t("triageReasons"), i("deletedAt", true), t("text")), Fk("groupUid", "groupId", "notes"))
    val MEDICINES = TableSpec("medicines", listOf(t("name"), t("strength"), t("form"), t("amount"), t("food"), t("times"), t("days"), i("startDate"),
        i("endDate", true), i("critical"), i("asNeeded"), i("minGapHours"), t("purpose"), Col("pillsLeft", K.R, true), i("active"), i("bloodThinner"),
        i("changedAt"), t("changeNote"), t("shape"), t("color")))
    val DOSES = TableSpec("doses", listOf(i("scheduledAt"), t("status"), i("actedAt", true), t("reason", true), i("snoozeUntil", true), i("reminded"),
        i("helperAlerted"), t("shownBy", true)), Fk("medicineUid", "medicineId", "medicines"))
    val APPOINTMENTS = TableSpec("appointments", listOf(i("at"), t("doctor"), t("place"), t("purpose"), i("done")))
    val DOC_LINES = TableSpec("doc_lines", listOf(t("source"), t("content"), i("importedAt"), i("reportDate", true)))

    /** Parents come before their children (medicines before doses). */
    val TABLES: List<TableSpec> = listOf(PROFILE, HELPERS, MEDICINES, DOSES, NOTES, APPOINTMENTS, DOC_LINES)
    val UID_TABLES: List<TableSpec> = TABLES.filter { it.hasUid }
    fun spec(name: String): TableSpec? = TABLES.firstOrNull { it.name == name }

    /** Milliseconds since 1970, in SQL. */
    const val NOW = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"
    /**
     * The time a local edit is stamped with: now, but always above the highest edit time received from any phone (sync_state 'hlc',
     * a hybrid logical clock). A phone whose clock ran far ahead can then win only until someone edits the row again.
     */
    const val STAMP = "MAX($NOW, COALESCE(CAST((SELECT v FROM sync_state WHERE k='hlc') AS INTEGER), 0) + 1)"
    const val DEVICE = "(SELECT v FROM sync_state WHERE k='device')"
    const val SEQ = "CAST((SELECT v FROM sync_state WHERE k='seq') AS INTEGER)"
    private const val APPLYING = "(SELECT v FROM sync_state WHERE k='applying')"
    private const val RANDOM_ID = "lower(hex(randomblob(16)))"

    // These must be exactly what Room generates for the entities (a test compares them with app/schemas/.../4.json).
    val CREATE_TABLES = listOf(
        "CREATE TABLE IF NOT EXISTS `sync_rows` (`tbl` TEXT NOT NULL, `uid` TEXT NOT NULL, `origin` TEXT NOT NULL, `oseq` INTEGER NOT NULL, `at` INTEGER NOT NULL, `by` TEXT NOT NULL, `del` INTEGER NOT NULL, PRIMARY KEY(`tbl`, `uid`))",
        "CREATE INDEX IF NOT EXISTS `index_sync_rows_origin_oseq` ON `sync_rows` (`origin`, `oseq`)",
        "CREATE TABLE IF NOT EXISTS `sync_state` (`k` TEXT NOT NULL, `v` TEXT NOT NULL, PRIMARY KEY(`k`))",
        "CREATE TABLE IF NOT EXISTS `sync_have` (`origin` TEXT NOT NULL, `seq` INTEGER NOT NULL, PRIMARY KEY(`origin`))",
    )

    fun alterColumns(): List<String> = UID_TABLES.flatMap { s ->
        listOf(
            "ALTER TABLE `${s.name}` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE `${s.name}` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `${s.name}` ADD COLUMN `updatedBy` TEXT NOT NULL DEFAULT ''",
        )
    }

    /** This phone's id, the running number and the "applying" flag. Safe to run again: it only fills what is missing. */
    fun seedState(): List<String> = listOf(
        "INSERT OR IGNORE INTO sync_state(k, v) VALUES('device', lower(hex(randomblob(8))))",
        "INSERT OR IGNORE INTO sync_state(k, v) VALUES('seq', '0')",
        "INSERT OR IGNORE INTO sync_state(k, v) VALUES('applying', '0')",
    )

    /**
     * A row's uid when it has none. A dose's uid is made from its medicine's uid and its time, so two phones that both create
     * the 8:00 dose of the same medicine make the SAME row (the unique index on medicineId+scheduledAt would refuse two).
     */
    private fun uidExpr(s: TableSpec, row: String) =
        if (s.name == "doses") "COALESCE((SELECT m.uid FROM medicines m WHERE m.id = $row.medicineId AND m.uid <> '') || ':' || $row.scheduledAt, $RANDOM_ID)"
        else RANDOM_ID

    /**
     * Gives every row without a uid one, stamps rows never stamped, and registers in sync_rows every row that has no entry yet
     * (origin = this phone, increasing numbers), so existing data is shared once. Safe to run again.
     */
    fun backfill(): List<String> = buildList {
        for (s in UID_TABLES) {
            add("UPDATE `${s.name}` SET uid = ${uidExpr(s, s.name)} WHERE uid = ''")
            add("UPDATE `${s.name}` SET updatedAt = $NOW, updatedBy = $DEVICE WHERE updatedAt = 0")
            add("INSERT OR REPLACE INTO sync_rows(tbl, uid, origin, oseq, at, `by`, del) SELECT '${s.name}', uid, $DEVICE, $SEQ + ROW_NUMBER() OVER (ORDER BY id), updatedAt, updatedBy, 0 " +
                "FROM `${s.name}` WHERE NOT EXISTS (SELECT 1 FROM sync_rows r WHERE r.tbl = '${s.name}' AND r.uid = `${s.name}`.uid)")
            add(bumpSeqToOwnMax())
        }
        add("INSERT OR REPLACE INTO sync_rows(tbl, uid, origin, oseq, at, `by`, del) SELECT 'profile', 'profile', $DEVICE, $SEQ + 1, $NOW, $DEVICE, 0 " +
            "FROM profile WHERE NOT EXISTS (SELECT 1 FROM sync_rows r WHERE r.tbl = 'profile' AND r.uid = 'profile')")
        add(bumpSeqToOwnMax())
    }

    private fun bumpSeqToOwnMax() =
        "UPDATE sync_state SET v = MAX(CAST(v AS INTEGER), (SELECT COALESCE(MAX(oseq), 0) FROM sync_rows WHERE origin = $DEVICE)) WHERE k = 'seq'"

    private const val BUMP_SEQ = "UPDATE sync_state SET v = CAST(v AS INTEGER) + 1 WHERE k = 'seq'"
    private const val START_STAMP = "UPDATE sync_state SET v = '2' WHERE k = 'applying'" // stops the stamping UPDATE from firing the triggers
    private const val END_STAMP = "UPDATE sync_state SET v = '0' WHERE k = 'applying'"

    private fun rowsInsert(s: TableSpec) =
        "INSERT OR REPLACE INTO sync_rows(tbl, uid, origin, oseq, at, `by`, del) SELECT '${s.name}', uid, $DEVICE, $SEQ, updatedAt, updatedBy, 0 FROM `${s.name}` WHERE id = NEW.id"

    /** The change-capture triggers (plan B.2). They do nothing while "applying" is not '0', so incoming changes never echo. */
    fun triggers(): List<String> = buildList {
        val on = "WHEN $APPLYING = '0'"
        for (s in TABLES) {
            val n = s.name
            if (s.hasUid) {
                val stampNew = "UPDATE `$n` SET uid = CASE WHEN uid = '' THEN ${uidExpr(s, n)} ELSE uid END, updatedAt = $STAMP, updatedBy = $DEVICE WHERE id = NEW.id"
                add("CREATE TRIGGER IF NOT EXISTS sync_${n}_ai AFTER INSERT ON `$n` $on BEGIN $START_STAMP; $stampNew; $BUMP_SEQ; ${rowsInsert(s)}; $END_STAMP; END")
                // a screen that saves an old copy of the row may send an empty uid: keep the old one
                val stampUp = "UPDATE `$n` SET uid = CASE WHEN NEW.uid = '' THEN CASE WHEN OLD.uid = '' THEN ${uidExpr(s, n)} ELSE OLD.uid END ELSE NEW.uid END, updatedAt = $STAMP, updatedBy = $DEVICE WHERE id = NEW.id"
                val changed = (listOf("NEW.uid = ''") + s.watched.map { "OLD.`$it` IS NOT NEW.`$it`" }).joinToString(" OR ")
                add("CREATE TRIGGER IF NOT EXISTS sync_${n}_au AFTER UPDATE ON `$n` $on AND ($changed) BEGIN $START_STAMP; $stampUp; $BUMP_SEQ; ${rowsInsert(s)}; $END_STAMP; END")
                add("CREATE TRIGGER IF NOT EXISTS sync_${n}_ad AFTER DELETE ON `$n` $on AND OLD.uid <> '' BEGIN $BUMP_SEQ; " +
                    "INSERT OR REPLACE INTO sync_rows(tbl, uid, origin, oseq, at, `by`, del) VALUES('$n', OLD.uid, $DEVICE, $SEQ, $STAMP, $DEVICE, 1); END")
            } else {
                val rows = "INSERT OR REPLACE INTO sync_rows(tbl, uid, origin, oseq, at, `by`, del) VALUES('profile', 'profile', $DEVICE, $SEQ, $STAMP, $DEVICE, 0)"
                add("CREATE TRIGGER IF NOT EXISTS sync_${n}_ai AFTER INSERT ON `$n` $on BEGIN $BUMP_SEQ; $rows; END")
                val changed = s.watched.joinToString(" OR ") { "OLD.`$it` IS NOT NEW.`$it`" }
                add("CREATE TRIGGER IF NOT EXISTS sync_${n}_au AFTER UPDATE ON `$n` $on AND ($changed) BEGIN $BUMP_SEQ; $rows; END")
            }
        }
    }

    /** Removes the triggers, so [triggers] can install changed ones (CREATE TRIGGER IF NOT EXISTS keeps an old body). */
    fun dropTriggers(): List<String> = TABLES.flatMap { s -> listOf("ai", "au", "ad").map { "DROP TRIGGER IF EXISTS sync_${s.name}_$it" } }

    /**
     * Erases everything of this phone (Settings, Privacy, Delete everything) WITHOUT sharing it as deletes: "applying" is set while the
     * tables are emptied, so the triggers stay silent, and Room's clearAllTables is not used because its table order could empty
     * sync_state first. Afterwards the phone is a new device: a new id, number 0, nothing received, nothing known.
     */
    fun wipe(): List<String> = buildList {
        add("UPDATE sync_state SET v = '1' WHERE k = 'applying'")
        RestoreSql.DATA_TABLES.forEach { add("DELETE FROM `$it`") }
        add("DELETE FROM sync_rows"); add("DELETE FROM sync_have"); add("DELETE FROM sync_state")
        addAll(seedState())
    }

    /** Migration 3 -> 4. */
    fun migration3to4(): List<String> = alterColumns() + CREATE_TABLES + seedState() + backfill() + triggers()

    /** Every time the database opens: fill what is missing (a fresh install, or tables wiped) and make sure the triggers exist. */
    fun onOpen(): List<String> = seedState() + dropTriggers() + triggers()
}

/**
 * Restore of a backup (see data/Backup.kt) as SQL. Only columns that exist in BOTH databases are copied, so a backup made by an
 * older or newer app version restores; columns missing in the backup take their defaults. Pure so it is tested on the JVM.
 * [mainCols] and [bkCols] map table name to its column names (from PRAGMA table_info); a table missing from [bkCols] is skipped.
 * The statements expect the backup attached as "bk", and run in one transaction.
 */
object RestoreSql {
    val DATA_TABLES = listOf("profile", "helpers", "notes", "medicines", "doses", "appointments", "doc_lines", "inbox")
    val SYNC_TABLES = listOf("sync_rows", "sync_state", "sync_have")

    /** One column of the phone's table, from PRAGMA table_info. */
    data class ColInfo(val name: String, val type: String, val notNull: Boolean, val hasDefault: Boolean)

    fun statements(mainCols: Map<String, List<ColInfo>>, bkCols: Map<String, List<String>>): List<String> {
        fun copy(t: String): List<String> {
            val b = bkCols[t] ?: return emptyList()
            val m = mainCols[t] ?: return emptyList()
            val both = m.filter { it.name in b }.map { it.name }
            // a column the backup lacks that the phone's table insists on (a fresh install has no DEFAULT on columns that older
            // versions added) gets an empty value
            val extra = m.filter { it.name !in b && it.notNull && !it.hasDefault }
            val names = (both + extra.map { it.name }).joinToString(", ") { "`$it`" }
            val zero = { ty: String -> ty.uppercase().let { it.contains("INT") || it.contains("REAL") } }
            val values = (both.map { "`$it`" } + extra.map { if (zero(it.type)) "0" else "''" }).joinToString(", ")
            return listOf("DELETE FROM main.`$t`", "INSERT INTO main.`$t`($names) SELECT $values FROM bk.`$t`")
        }
        val hasSync = SYNC_TABLES.all { it in bkCols }
        val out = ArrayList<String>()
        out += SyncSql.seedState()
        out += "UPDATE main.sync_state SET v = '1' WHERE k = 'applying'" // the restore is not a change to share
        DATA_TABLES.forEach { out += copy(it) }
        if (hasSync) {
            // a backup of this app's own kind: the versions of the rows come with it, but the phone becomes a NEW device. The device id
            // in the backup may belong to a phone that kept running (or this same phone, which made more changes after the backup):
            // reusing it would hand out numbers other phones already used for different changes. The old id is now just another
            // phone whose changes this one holds up to the backup's number; the rest arrives from the family on the next HELLO.
            SYNC_TABLES.forEach { out += copy(it) }
            out += "INSERT OR REPLACE INTO main.sync_have(origin, seq) SELECT d.v, CAST(s.v AS INTEGER) FROM main.sync_state d, main.sync_state s " +
                "WHERE d.k = 'device' AND s.k = 'seq' AND CAST(s.v AS INTEGER) > 0"
            out += "UPDATE main.sync_state SET v = '1' WHERE k = 'applying'" // the backup's sync_state has just replaced the flag
            out += "DELETE FROM main.sync_state WHERE k IN ('device', 'seq')"
            out += SyncSql.seedState()
            out += SyncSql.backfill()
        } else {
            // an old backup (version 3): the rows have no uids yet, so they get NEW ones. Backup.import refuses this on a phone that
            // shares with family (the rows would appear twice on the other phones). Here the phone forgets everything it knew.
            out += "DELETE FROM main.sync_rows"
            out += "DELETE FROM main.sync_have"
            out += "DELETE FROM main.sync_state WHERE k IN ('device', 'seq', 'hlc')"
            out += SyncSql.seedState()
            out += SyncSql.backfill()
        }
        out += "UPDATE main.sync_state SET v = '0' WHERE k = 'applying'"
        return out
    }
}
