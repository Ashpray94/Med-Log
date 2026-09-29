package com.suryaprakash.medlog.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** The person using MedLog. One row. */
@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val dob: String = "",            // yyyy-MM-dd
    val sex: String = "",            // "F" / "M" / ""
    val bloodGroup: String = "",
    val hospitalId: String = "",
    val conditions: String = "",
    val allergies: String = "",
    val doctorName: String = "",
    val doctorPhone: String = "",
    val onBloodThinner: Boolean = false,
    val notes: String = "",
    /** The care plan from setup, as JSON ([CarePlan]): doctors, current symptoms, treatments, risks, emergencies. */
    @androidx.room.ColumnInfo(defaultValue = "") val plan: String = "",
    // Settings that describe the PERSON, shared with every phone (database version 5), one column each so each merges on its own.
    // null = not chosen yet: the phone's default applies. See data/PersonSettings.kt.
    val waterGoal: Int? = null,
    val diabetic: Boolean? = null,
    val emergencyNumber: String? = null,
    val checkInEnabled: Boolean? = null,
    val checkInTime: String? = null,
    val snoozeMinutes: Int? = null,
    val escalateMinutes: Int? = null,
    val escalateCriticalMinutes: Int? = null,
    val sosCountdown: Int? = null,
    val kcalTarget: Double? = null,
    val proteinTarget: Double? = null,
    /** foods the person added (JSON, see nutrition.Foods.customWith) */
    val customFoods: String? = null,
    /** pending "tell me more" reminders: "noteUid:dueAt;..." */
    val followups: String? = null,
)

/** Family, neighbours, carers. */
@Entity(tableName = "helpers")
data class Helper(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val relation: String = "",
    /** called and messaged in an SOS */
    val sos: Boolean = true,
    /** gets missed-medicine and check-in alerts */
    val alerts: Boolean = true,
    /** paired phone running MedLog Helper mode */
    val pairId: String? = null,
    val pairKey: String? = null,
    val canSeeNotes: Boolean = false,
    val sortOrder: Int = 0,
    /** Two-way sharing (database version 4): a stable id for this row on every phone, and who last changed it and when. Stamped by triggers. */
    @androidx.room.ColumnInfo(defaultValue = "") val uid: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "") val updatedBy: String = "",
)

/**
 * Everything the person records: symptoms, water, food, readings, SOS, check-ins, doctor visits.
 * [details] holds the structured facts as JSON (see nlu.Fact).
 */
@Entity(tableName = "notes", indices = [Index("occurredAt"), Index("problemId"), Index("kind")])
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,                 // see Kind
    val problemId: String? = null,
    val occurredAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val transcript: String? = null,
    val details: String = "{}",
    val severity: Int? = null,
    val count: Int? = null,
    val triage: String = "GREEN",
    val triageReasons: String = "",
    val audioPath: String? = null,
    val photoPath: String? = null,
    /** links notes told together ("vomiting + stomach pain") */
    val groupId: Long? = null,
    val deletedAt: Long? = null,
    val text: String = "",            // plain summary line, also used for search
    /** Two-way sharing (database version 4): a stable id for this row on every phone, and who last changed it and when. Stamped by triggers. */
    @androidx.room.ColumnInfo(defaultValue = "") val uid: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "") val updatedBy: String = "",
)

object Kind {
    const val SYMPTOM = "SYMPTOM"
    const val WATER = "WATER"
    const val FOOD = "FOOD"
    const val READING = "READING"
    const val MED_TAKEN = "MED_TAKEN"     // as-needed medicine taken
    const val SOS = "SOS"
    const val CHECKIN = "CHECKIN"
    const val VISIT = "VISIT"
    const val MESSAGE = "MESSAGE"
    const val IMPORTED = "IMPORTED"       // from an old report
    const val QUESTION = "QUESTION"       // question for the doctor
    const val FALL_ALERT = "FALL_ALERT"
}

@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val strength: String = "",        // "500 mg"
    val form: String = "tablet",      // tablet, capsule, syrup, drops, injection, inhaler, cream
    val amount: String = "1",         // how many per dose, "1", "½", "10 ml"
    val food: String = "any",         // before, after, with, any
    val times: String = "",           // "08:00,20:00"; empty = as needed
    val days: String = "",            // "" = every day, or "1,3,5" (Mon=1)
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val critical: Boolean = false,
    val asNeeded: Boolean = false,
    /** minimum hours between as-needed doses (double-dose guard) */
    val minGapHours: Int = 4,
    val purpose: String = "",
    val photoPath: String? = null,
    /**
     * The pill count as of [pillsAt] (the last refill or edit). What is left now is this minus the doses taken since, worked out by
     * [com.suryaprakash.medlog.meds.Pills], so doses taken on two phones at once are both counted.
     */
    val pillsLeft: Double? = null,
    val active: Boolean = true,
    val bloodThinner: Boolean = false,
    val changedAt: Long = System.currentTimeMillis(),
    val changeNote: String = "",      // "increased from 5 mg", for the doctor page
    val calendarEventId: Long? = null,
    /** what it looks like, so it can be told apart from the others: "round", "oval", "capsule", "oblong" … and a colour name */
    val shape: String = "",
    val color: String = "",
    /** When [pillsLeft] was counted (ms); doses taken after this time are subtracted from it. */
    @androidx.room.ColumnInfo(defaultValue = "0") val pillsAt: Long = 0,
    /** A feed's contents (parts with kcal and protein, tube or mouth) as JSON, see nutrition.Feeds; shared with the family, "" for a medicine. */
    @androidx.room.ColumnInfo(defaultValue = "") val feedInfo: String = "",
    /** Two-way sharing (database version 4): a stable id for this row on every phone, and who last changed it and when. Stamped by triggers. */
    @androidx.room.ColumnInfo(defaultValue = "") val uid: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "") val updatedBy: String = "",
)

object DoseStatus { const val DUE = "DUE"; const val TAKEN = "TAKEN"; const val SKIPPED = "SKIPPED"; const val MISSED = "MISSED"; const val SNOOZED = "SNOOZED" }

@Entity(tableName = "doses", indices = [Index(value = ["medicineId", "scheduledAt"], unique = true), Index("scheduledAt")])
data class Dose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val scheduledAt: Long,
    val status: String = DoseStatus.DUE,
    val actedAt: Long? = null,
    val reason: String? = null,
    val snoozeUntil: Long? = null,
    val reminded: Int = 0,
    val helperAlerted: Boolean = false,
    val shownBy: String? = null,      // "medlog" / "meetingtimer"
    /** Two-way sharing (database version 4): a stable id for this row on every phone, and who last changed it and when. Stamped by triggers. */
    @androidx.room.ColumnInfo(defaultValue = "") val uid: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "") val updatedBy: String = "",
)

@Entity(tableName = "appointments")
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val doctor: String = "",
    val place: String = "",
    val purpose: String = "",
    val calendarEventId: Long? = null,
    val done: Boolean = false,
    /** Two-way sharing (database version 4): a stable id for this row on every phone, and who last changed it and when. Stamped by triggers. */
    @androidx.room.ColumnInfo(defaultValue = "") val uid: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "") val updatedBy: String = "",
)

/** Lines from imported old reports (retrieval-only search, carried over from MedLog v1). */
@Entity(tableName = "doc_lines", indices = [Index("source")])
data class DocLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val content: String,
    val importedAt: Long = System.currentTimeMillis(),
    val reportDate: Long? = null,
    /** Two-way sharing (database version 4): a stable id for this row on every phone, and who last changed it and when. Stamped by triggers. */
    @androidx.room.ColumnInfo(defaultValue = "") val uid: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "") val updatedBy: String = "",
)

/** The version of every shared row this phone holds (see sync/SyncSql). One row per (table, uid), tombstones included. */
@Entity(tableName = "sync_rows", primaryKeys = ["tbl", "uid"], indices = [Index(value = ["origin", "oseq"])])
data class SyncRow(val tbl: String, val uid: String, val origin: String, val oseq: Long, val at: Long, val by: String, val del: Long)

/** The version of every column of every live shared row (database version 5): per-column last write wins. [col] is the wire name of the column. */
@Entity(tableName = "sync_cols", primaryKeys = ["tbl", "uid", "col"], indices = [Index(value = ["origin", "oseq"])])
data class SyncCol(val tbl: String, val uid: String, val col: String, val at: Long, val by: String, val origin: String, val oseq: Long)

/** device (this phone's id), seq (last local number), applying ("1" while incoming changes are written). */
@Entity(tableName = "sync_state")
data class SyncState(@PrimaryKey val k: String, val v: String)

/** The highest number this phone completely holds from each other device. */
@Entity(tableName = "sync_have")
data class SyncHave(@PrimaryKey val origin: String, val seq: Long)

/** Alerts received on a helper's phone. */
@Entity(tableName = "inbox")
data class InboxItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromName: String,
    val text: String,
    val kind: String,          // MESSAGE, SOS, MISSED_DOSE, CHECKIN, FALL
    val at: Long = System.currentTimeMillis(),
    val acked: Boolean = false,
    val audioPath: String? = null,
    val location: String? = null,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1") fun flow(): Flow<Profile?>
    @Query("SELECT * FROM profile WHERE id = 1") suspend fun get(): Profile?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(p: Profile)
    /** Writes the row in place, so only the columns whose value changed count as changed for sharing (put replaces the whole row). */
    @Update suspend fun update(p: Profile)
}

@Dao
interface HelperDao {
    @Query("SELECT * FROM helpers ORDER BY sortOrder, id") fun flow(): Flow<List<Helper>>
    @Query("SELECT * FROM helpers ORDER BY sortOrder, id") suspend fun all(): List<Helper>
    @Query("SELECT * FROM helpers WHERE pairId = :pairId") suspend fun byPair(pairId: String): Helper?
    @Insert suspend fun insert(h: Helper): Long
    @Update suspend fun update(h: Helper)
    @Query("DELETE FROM helpers WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY occurredAt DESC LIMIT :limit") fun recentFlow(limit: Int = 500): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt") suspend fun between(from: Long, to: Long): List<Note>
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt") fun betweenFlow(from: Long, to: Long): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND kind = 'SYMPTOM' AND occurredAt >= :from ORDER BY occurredAt DESC") suspend fun symptomsSince(from: Long): List<Note>
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND kind = 'SYMPTOM' AND occurredAt >= :from ORDER BY occurredAt DESC") fun symptomsSinceFlow(from: Long): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND kind = :kind AND occurredAt >= :from ORDER BY occurredAt DESC") suspend fun kindSince(kind: String, from: Long): List<Note>
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND kind = :kind AND occurredAt >= :from ORDER BY occurredAt DESC") fun kindSinceFlow(kind: String, from: Long): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC") fun removedFlow(): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun get(id: Long): Note?
    @Query("SELECT * FROM notes WHERE uid = :uid") suspend fun byUid(uid: String): Note?
    @Query("SELECT * FROM notes WHERE id = :id") fun flow(id: Long): Flow<Note?>
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND (text LIKE '%' || :q || '%' OR transcript LIKE '%' || :q || '%') ORDER BY occurredAt DESC LIMIT 50") suspend fun search(q: String): List<Note>
    @Query("SELECT * FROM notes") suspend fun everything(): List<Note>
    @Insert suspend fun insert(n: Note): Long
    @Update suspend fun update(n: Note)
    @Query("UPDATE notes SET deletedAt = :at WHERE id = :id") suspend fun remove(id: Long, at: Long = System.currentTimeMillis())
    @Query("UPDATE notes SET deletedAt = NULL WHERE id = :id") suspend fun restore(id: Long)
    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :before") suspend fun purge(before: Long)
    @Query("DELETE FROM notes") suspend fun wipe()
}

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines WHERE active = 1 ORDER BY name") fun activeFlow(): Flow<List<Medicine>>
    @Query("SELECT * FROM medicines WHERE active = 1 ORDER BY name") suspend fun active(): List<Medicine>
    @Query("SELECT * FROM medicines ORDER BY active DESC, name") suspend fun all(): List<Medicine>
    @Query("SELECT * FROM medicines WHERE id = :id") suspend fun get(id: Long): Medicine?
    @Query("SELECT * FROM medicines WHERE uid = :uid") suspend fun byUid(uid: String): Medicine?
    @Insert suspend fun insert(m: Medicine): Long
    @Update suspend fun update(m: Medicine)
}

/** How many doses of a medicine were taken since its pill count was made. */
data class TakenCount(val medicineId: Long, val n: Int)

@Dao
interface DoseDao {
    /** Per medicine with a pill count: the TAKEN doses acted after the time of the count (see meds.Pills). */
    @Query(com.suryaprakash.medlog.meds.Pills.TAKEN_SINCE)
    suspend fun takenSincePillsAt(): List<TakenCount>
    @Query("SELECT * FROM doses WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt") fun betweenFlow(from: Long, to: Long): Flow<List<Dose>>
    @Query("SELECT * FROM doses WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt") suspend fun between(from: Long, to: Long): List<Dose>
    @Query("SELECT * FROM doses WHERE status IN ('DUE','SNOOZED') ORDER BY scheduledAt") suspend fun open(): List<Dose>
    @Query("SELECT * FROM doses WHERE medicineId = :med ORDER BY scheduledAt DESC LIMIT :n") suspend fun lastFor(med: Long, n: Int): List<Dose>
    @Query("SELECT * FROM doses WHERE id = :id") suspend fun get(id: Long): Dose?
    @Query("SELECT * FROM doses WHERE uid = :uid") suspend fun byUid(uid: String): Dose?
    @Query("DELETE FROM doses WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM doses") suspend fun everything(): List<Dose>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(d: Dose): Long
    @Update suspend fun update(d: Dose)
    @Query("UPDATE doses SET status = 'SKIPPED', reason = :reason, actedAt = :at, snoozeUntil = NULL WHERE medicineId = :med AND status IN ('DUE','SNOOZED')") suspend fun skipOpen(med: Long, reason: String, at: Long)
    @Query("DELETE FROM doses WHERE medicineId = :med AND status = 'DUE' AND scheduledAt > :after") suspend fun dropFuture(med: Long, after: Long)
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments WHERE at >= :from ORDER BY at") fun upcomingFlow(from: Long): Flow<List<Appointment>>
    @Query("SELECT * FROM appointments WHERE at >= :from ORDER BY at") suspend fun upcoming(from: Long): List<Appointment>
    @Query("SELECT * FROM appointments") suspend fun everything(): List<Appointment>
    @Insert suspend fun insert(a: Appointment): Long
    @Update suspend fun update(a: Appointment)
    @Query("DELETE FROM appointments WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface DocLineDao {
    @Insert suspend fun insertAll(lines: List<DocLine>)
    @Query("SELECT * FROM doc_lines WHERE content LIKE '%' || :q || '%' ORDER BY reportDate DESC LIMIT 40") suspend fun search(q: String): List<DocLine>
    @Query("SELECT DISTINCT source FROM doc_lines") suspend fun sources(): List<String>
    @Query("SELECT * FROM doc_lines") suspend fun everything(): List<DocLine>
}

@Dao
interface InboxDao {
    @Query("SELECT * FROM inbox ORDER BY at DESC LIMIT 100") fun flow(): Flow<List<InboxItem>>
    @Insert suspend fun insert(i: InboxItem): Long
    @Query("UPDATE inbox SET acked = 1 WHERE id = :id") suspend fun ack(id: Long)
}

@Database(
    entities = [Profile::class, Helper::class, Note::class, Medicine::class, Dose::class, Appointment::class, DocLine::class, InboxItem::class, SyncRow::class, SyncCol::class, SyncState::class, SyncHave::class],
    version = 5,
    exportSchema = true,
)
abstract class MedDb : RoomDatabase() {
    abstract fun profile(): ProfileDao
    abstract fun helpers(): HelperDao
    abstract fun notes(): NoteDao
    abstract fun medicines(): MedicineDao
    abstract fun doses(): DoseDao
    abstract fun appointments(): AppointmentDao
    abstract fun docLines(): DocLineDao
    abstract fun inbox(): InboxDao

    companion object {
        fun open(ctx: Context, name: String = "medlog.db"): MedDb {
            System.loadLibrary("sqlcipher")
            val key = Keys.databaseKey(ctx)
            return Room.databaseBuilder(ctx, MedDb::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(key))
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(M1_2, M2_3, M3_4, M4_5)
                .addCallback(object : androidx.room.RoomDatabase.Callback() {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        for (sql in com.suryaprakash.medlog.sync.SyncSql.onOpen()) db.execSQL(sql)
                    }
                })
                .build()
        }

        /** Two-way sharing: uid/updatedAt/updatedBy, the sync tables, the first seeding of what is already there, the triggers. */
        private val M3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                for (sql in com.suryaprakash.medlog.sync.SyncSql.migration3to4()) db.execSQL(sql)
            }
        }

        /** Per-column versions for sharing, the feed's contents and the pill count's baseline on the medicine. */
        private val M4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                for (sql in com.suryaprakash.medlog.sync.SyncSql.migration4to5()) db.execSQL(sql)
            }
        }

        /** 2.9: what a medicine looks like. */
        private val M2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicines ADD COLUMN shape TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE medicines ADD COLUMN color TEXT NOT NULL DEFAULT ''")
            }
        }

        /** 2.8: the care plan column. */
        private val M1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN plan TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
