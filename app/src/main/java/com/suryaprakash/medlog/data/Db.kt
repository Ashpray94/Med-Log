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
    @androidx.room.ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
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
)

/**
 * Everything the person records: symptoms, water, food, readings, SOS, check-ins, doctor visits.
 * [details] holds the structured facts as JSON (see nlu.Fact).
 */
@Entity(tableName = "notes", indices = [Index("occurredAt"), Index("problemId"), Index("kind"), Index("uid"), Index("updatedAt")])
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
    /** Shared between paired phones: the same entry has the same [uid] everywhere; [updatedAt] says which copy is newer. */
    @androidx.room.ColumnInfo(defaultValue = "") val uid: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
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
    const val OUTPUT = "OUTPUT"           // stool, urine, vomit
}

@Entity(tableName = "medicines", indices = [Index("uid"), Index("updatedAt")])
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
    val pillsLeft: Double? = null,
    val active: Boolean = true,
    val bloodThinner: Boolean = false,
    val changedAt: Long = System.currentTimeMillis(),
    val changeNote: String = "",      // "increased from 5 mg", for the doctor page
    val calendarEventId: Long? = null,
    /** what it looks like, so it can be told apart from the others: "round", "oval", "capsule", "oblong" … and a colour name */
    val shape: String = "",
    val color: String = "",
    /** Shared between paired phones: the same entry has the same [uid] everywhere; [updatedAt] says which copy is newer. */
    @androidx.room.ColumnInfo(defaultValue = "") val uid: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

object DoseStatus { const val DUE = "DUE"; const val TAKEN = "TAKEN"; const val SKIPPED = "SKIPPED"; const val MISSED = "MISSED"; const val SNOOZED = "SNOOZED" }

@Entity(tableName = "doses", indices = [Index(value = ["medicineId", "scheduledAt"], unique = true), Index("scheduledAt"), Index("uid"), Index("updatedAt")])
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
    /** Shared between paired phones: the same entry has the same [uid] everywhere; [updatedAt] says which copy is newer. */
    @androidx.room.ColumnInfo(defaultValue = "") val uid: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
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
)

/** Lines from imported old reports (retrieval-only search, carried over from MedLog v1). */
@Entity(tableName = "doc_lines", indices = [Index("source")])
data class DocLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val content: String,
    val importedAt: Long = System.currentTimeMillis(),
    val reportDate: Long? = null,
)

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
    @Query("UPDATE profile SET updatedAt = :t WHERE id = 1") suspend fun setUpdated(t: Long)
    @Query("SELECT * FROM profile WHERE id = 1") fun flow(): Flow<Profile?>
    @Query("SELECT * FROM profile WHERE id = 1") suspend fun get(): Profile?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(p: Profile)
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
    @Query("SELECT * FROM notes WHERE updatedAt > :since ORDER BY updatedAt LIMIT :limit") suspend fun changedSince(since: Long, limit: Int): List<Note>
    @Query("SELECT * FROM notes WHERE uid = :uid LIMIT 1") suspend fun byUid(uid: String): Note?
    /** The same entry by what it is, whatever its id: kind, problem, when it happened and when it was made (never shared by two different entries). */
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND kind = :kind AND IFNULL(problemId, '') = :problemId AND occurredAt = :at AND createdAt = :created AND text = :text LIMIT 1")
    suspend fun sameEntry(kind: String, problemId: String, at: Long, created: Long, text: String): Note?
    @Query("SELECT MAX(updatedAt) FROM notes") suspend fun lastChange(): Long?
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY occurredAt DESC LIMIT :limit") fun recentFlow(limit: Int = 500): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt") suspend fun between(from: Long, to: Long): List<Note>
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt") fun betweenFlow(from: Long, to: Long): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND kind = 'SYMPTOM' AND occurredAt >= :from ORDER BY occurredAt DESC") suspend fun symptomsSince(from: Long): List<Note>
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND kind = 'SYMPTOM' AND occurredAt >= :from ORDER BY occurredAt DESC") fun symptomsSinceFlow(from: Long): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND kind = :kind AND occurredAt >= :from ORDER BY occurredAt DESC") suspend fun kindSince(kind: String, from: Long): List<Note>
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND kind = :kind AND occurredAt >= :from ORDER BY occurredAt DESC") fun kindSinceFlow(kind: String, from: Long): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC") fun removedFlow(): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun get(id: Long): Note?
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
    @Query("SELECT * FROM medicines WHERE updatedAt > :since ORDER BY updatedAt LIMIT :limit") suspend fun changedSince(since: Long, limit: Int): List<Medicine>
    @Query("SELECT * FROM medicines WHERE uid = :uid LIMIT 1") suspend fun byUid(uid: String): Medicine?
    @Query("SELECT MAX(updatedAt) FROM medicines") suspend fun lastChange(): Long?
    @Query("SELECT * FROM medicines WHERE active = 1 ORDER BY name") fun activeFlow(): Flow<List<Medicine>>
    @Query("SELECT * FROM medicines WHERE active = 1 ORDER BY name") suspend fun active(): List<Medicine>
    @Query("SELECT * FROM medicines ORDER BY active DESC, name") suspend fun all(): List<Medicine>
    @Query("SELECT * FROM medicines WHERE id = :id") suspend fun get(id: Long): Medicine?
    @Insert suspend fun insert(m: Medicine): Long
    @Update suspend fun update(m: Medicine)
}

@Dao
interface DoseDao {
    @Query("SELECT * FROM doses WHERE updatedAt > :since ORDER BY updatedAt LIMIT :limit") suspend fun changedSince(since: Long, limit: Int): List<Dose>
    @Query("SELECT * FROM doses WHERE uid = :uid LIMIT 1") suspend fun byUid(uid: String): Dose?
    @Query("SELECT * FROM doses WHERE medicineId = :med AND scheduledAt = :at LIMIT 1") suspend fun at(med: Long, at: Long): Dose?
    @Query("SELECT MAX(updatedAt) FROM doses") suspend fun lastChange(): Long?
    @Query("SELECT * FROM doses WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt") fun betweenFlow(from: Long, to: Long): Flow<List<Dose>>
    @Query("SELECT * FROM doses WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt") suspend fun between(from: Long, to: Long): List<Dose>
    @Query("SELECT * FROM doses WHERE status IN ('DUE','SNOOZED') ORDER BY scheduledAt") suspend fun open(): List<Dose>
    @Query("SELECT * FROM doses WHERE medicineId = :med ORDER BY scheduledAt DESC LIMIT :n") suspend fun lastFor(med: Long, n: Int): List<Dose>
    @Query("SELECT * FROM doses WHERE id = :id") suspend fun get(id: Long): Dose?
    @Query("SELECT * FROM doses") suspend fun everything(): List<Dose>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(d: Dose): Long
    @Update suspend fun update(d: Dose)
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
    entities = [Profile::class, Helper::class, Note::class, Medicine::class, Dose::class, Appointment::class, DocLine::class, InboxItem::class],
    version = 4,
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
        fun open(ctx: Context): MedDb = open(ctx, "medlog.db")

        /** This phone's own records ("medlog.db"), or the copy of someone it helps ("mirror_<pairing>.db"). */
        fun open(ctx: Context, file: String): MedDb {
            // screenshot tests on the computer: a plain in-memory database (SQLCipher is phone-only native code)
            if (android.os.Build.FINGERPRINT == "robolectric")
                return Room.inMemoryDatabaseBuilder(ctx, MedDb::class.java).allowMainThreadQueries()
                    .addCallback(object : Callback() { override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = stamps(db, mirror = false) }).build()
            System.loadLibrary("sqlcipher")
            val key = Keys.databaseKey(ctx)
            return Room.databaseBuilder(ctx, MedDb::class.java, file)
                .openHelperFactory(SupportOpenHelperFactory(key))
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(M1_2, M2_3, M3_4)
                .addCallback(object : Callback() {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = stamps(db, file.startsWith("mirror_"))
                    // every open: the stamps are always the current version, and any row missing its id is put right
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) = stamps(db, file.startsWith("mirror_"))
                })
                .build()
        }

        private const val NOW_MS = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"

        /**
         * Every entry gets an id shared with paired phones, and a time it last changed, without any code having to
         * remember: new rows are stamped when they're added, and changed rows when they change. A change that
         * already carries its own time (one arriving from the other phone) is left as it is.
         */
        private fun stamps(db: androidx.sqlite.db.SupportSQLiteDatabase, mirror: Boolean = false) {
            for (t in listOf("notes", "medicines", "doses")) {
                for (old in listOf("uid", "new", "changed", "stamp", "keep")) db.execSQL("DROP TRIGGER IF EXISTS ${t}_$old")
                // a new row: give it an id and a time, in one step (rows arriving from the other phone already have both)
                db.execSQL("CREATE TRIGGER ${t}_new AFTER INSERT ON $t WHEN NEW.uid = '' OR NEW.updatedAt = 0 BEGIN " +
                    "UPDATE $t SET uid = CASE WHEN uid = '' THEN lower(hex(randomblob(16))) ELSE uid END, " +
                    "updatedAt = CASE WHEN updatedAt = 0 THEN $NOW_MS ELSE updatedAt END WHERE id = NEW.id; END")
                // a changed row: its time always moves forward, even twice in one millisecond, so this can never repeat itself
                db.execSQL("CREATE TRIGGER ${t}_changed AFTER UPDATE ON $t WHEN NEW.updatedAt = OLD.updatedAt BEGIN " +
                    "UPDATE $t SET updatedAt = MAX($NOW_MS, OLD.updatedAt + 1) WHERE id = NEW.id; END")
                // a change written from a copy made before the row was saved: it keeps its id and gets a time, never a blank one
                db.execSQL("CREATE TRIGGER ${t}_keep AFTER UPDATE ON $t WHEN NEW.uid = '' OR NEW.updatedAt = 0 BEGIN " +
                    "UPDATE $t SET uid = CASE WHEN NEW.uid != '' THEN NEW.uid WHEN OLD.uid != '' THEN OLD.uid ELSE lower(hex(randomblob(16))) END, " +
                    "updatedAt = CASE WHEN NEW.updatedAt = 0 THEN MAX($NOW_MS, OLD.updatedAt + 1) ELSE NEW.updatedAt END WHERE id = NEW.id; END")
                // rows that lost their id before this was fixed. On this phone's own records: a new id, sent again.
                // In a helper's copy they were mixed-up merges of different entries: dropped, and the person's phone sends them again.
                if (mirror) db.execSQL("DELETE FROM $t WHERE uid = ''")
                else db.execSQL("UPDATE $t SET uid = lower(hex(randomblob(16))), updatedAt = $NOW_MS WHERE uid = ''")
                db.execSQL("UPDATE $t SET updatedAt = $NOW_MS WHERE updatedAt = 0")
            }
            for (old in listOf("new", "changed")) db.execSQL("DROP TRIGGER IF EXISTS profile_$old")
            db.execSQL("CREATE TRIGGER profile_new AFTER INSERT ON profile BEGIN UPDATE profile SET updatedAt = MAX($NOW_MS, NEW.updatedAt + 1) WHERE id = NEW.id; END")
            db.execSQL("CREATE TRIGGER profile_changed AFTER UPDATE ON profile WHEN NEW.updatedAt = OLD.updatedAt BEGIN " +
                "UPDATE profile SET updatedAt = MAX($NOW_MS, OLD.updatedAt + 1) WHERE id = NEW.id; END")
        }

        /** 2.10: entries shared with helpers' phones. */
        private val M3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                for (t in listOf("notes", "medicines", "doses")) {
                    db.execSQL("ALTER TABLE $t ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $t ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE $t SET uid = lower(hex(randomblob(16))), updatedAt = $NOW_MS")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_${t}_uid ON $t (uid)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_${t}_updatedAt ON $t (updatedAt)")
                }
                db.execSQL("ALTER TABLE profile ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE profile SET updatedAt = $NOW_MS")
                stamps(db)
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
