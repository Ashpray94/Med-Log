package com.suryaprakash.medlog.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, manifest = Config.NONE, sdk = [28])
class DatabaseUpgradeTest {
    @Test
    fun `shipped v4 rows keep stable ids and migrate with room validation`() = migrateFixture("schema-v4-shipped.json", shipped = true)

    @Test
    fun `draft v4 rows recover ids and timestamps from sync metadata`() = migrateFixture("schema-v4-draft.json", shipped = false)

    private fun migrateFixture(resource: String, shipped: Boolean) {
        val context = RuntimeEnvironment.getApplication()
        val name = "upgrade-${resource.hashCode()}-${System.nanoTime()}.db"
        val schema = JSONObject(javaClass.classLoader!!.getResourceAsStream(resource)!!.bufferedReader().use { it.readText() })
            .getJSONObject("database")
        val oldHelper = legacyHelper(context, name, schema)
        val oldDb = oldHelper.writableDatabase
        seedRows(oldDb, shipped)
        if (shipped) installRestampingTriggers(oldDb)
        oldHelper.close()

        // Opening as the current entity model runs M4_5 and Room's full schema validation.
        val db = Room.databaseBuilder(context, MedDb::class.java, name)
            .addMigrations(MedDb.M4_5)
            .allowMainThreadQueries()
            .build()
        try {
            db.openHelper.writableDatabase
            val note = kotlinx.coroutines.runBlocking { db.notes().get(41) }!!
            val medicine = kotlinx.coroutines.runBlocking { db.medicines().get(51) }!!
            val dose = kotlinx.coroutines.runBlocking { db.doses().get(61) }!!
            val appointment = kotlinx.coroutines.runBlocking { db.appointments().everything().single() }
            val profile = kotlinx.coroutines.runBlocking { db.profile().get() }!!

            assertEquals("Mira", profile.name)
            assertEquals("keep me", profile.notes)
            assertTrue(profile.updatedAt > 0)
            if (shipped) assertEquals(1234L, profile.updatedAt)
            assertEquals("persistent cough", note.text)
            assertEquals(500L, note.deletedAt)
            assertEquals("uid-note-41", note.uid)
            assertEquals(if (shipped) 1111L else 4444L, note.updatedAt)
            assertEquals("uid-med-51", medicine.uid)
            assertEquals(if (shipped) 2222L else 5555L, medicine.updatedAt)
            assertEquals("uid-dose-61", dose.uid)
            assertEquals(if (shipped) 3333L else 6666L, dose.updatedAt)
            assertEquals(71L, appointment.id)
            assertEquals("Dr Rao", appointment.doctor)

            val noteMeta = kotlinx.coroutines.runBlocking { db.sync().get("uid-note-41") }!!
            val medMeta = kotlinx.coroutines.runBlocking { db.sync().get("uid-med-51") }!!
            val doseMeta = kotlinx.coroutines.runBlocking { db.sync().get("uid-dose-61") }!!
            assertEquals(41L, noteMeta.localId)
            assertTrue(noteMeta.deleted)
            assertEquals(51L, medMeta.localId)
            assertEquals(61L, doseMeta.localId)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private fun legacyHelper(context: android.content.Context, name: String, schema: JSONObject): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.getJSONArray("indices")
                    for (j in 0 until indices.length())
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected fixture upgrade")
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun seedRows(db: SupportSQLiteDatabase, shipped: Boolean) {
        val profileColumn = if (shipped) ",updatedAt" else ""
        val profileValue = if (shipped) ",1234" else ""
        db.execSQL("INSERT INTO profile(id,name,dob,sex,bloodGroup,hospitalId,conditions,allergies,doctorName,doctorPhone,onBloodThinner,notes,plan$profileColumn) VALUES(1,'Mira','1930-01-01','F','','','asthma','','','','0','keep me','{}'$profileValue)")

        val rowFields = if (shipped) ",uid,updatedAt" else ""
        val noteData = if (shipped) ",'uid-note-41',1111" else ""
        val medData = if (shipped) ",'uid-med-51',2222" else ""
        val doseData = if (shipped) ",'uid-dose-61',3333" else ""
        db.execSQL("INSERT INTO notes(id,kind,problemId,occurredAt,createdAt,transcript,details,severity,count,triage,triageReasons,audioPath,photoPath,groupId,deletedAt,text$rowFields) VALUES(41,'SYMPTOM','cough',800,700,'told','{}',2,NULL,'AMBER','urgent',NULL,NULL,NULL,500,'persistent cough'$noteData)")
        db.execSQL("INSERT INTO medicines(id,name,strength,form,amount,food,times,days,startDate,endDate,critical,asNeeded,minGapHours,purpose,photoPath,pillsLeft,active,bloodThinner,changedAt,changeNote,calendarEventId,shape,color$rowFields) VALUES(51,'Aspirin','75 mg','tablet','1','after','08:00','',100,NULL,0,0,4,'heart',NULL,12,1,0,90,'',NULL,'round','white'$medData)")
        db.execSQL("INSERT INTO doses(id,medicineId,scheduledAt,status,actedAt,reason,snoozeUntil,reminded,helperAlerted,shownBy$rowFields) VALUES(61,51,900,'TAKEN',901,'on time',NULL,1,1,'medlog'$doseData)")
        db.execSQL("INSERT INTO appointments(id,at,doctor,place,purpose,calendarEventId,done) VALUES(71,1200,'Dr Rao','Clinic','Review',9,0)")
        if (!shipped) {
            db.execSQL("INSERT INTO sync_meta(uid,type,localId,updatedAt,deleted) VALUES('uid-note-41','note',41,4444,1)")
            db.execSQL("INSERT INTO sync_meta(uid,type,localId,updatedAt,deleted) VALUES('uid-med-51','medicine',51,5555,0)")
            db.execSQL("INSERT INTO sync_meta(uid,type,localId,updatedAt,deleted) VALUES('uid-dose-61','dose',61,6666,0)")
        }
    }

    /** The shipped open callback installed update triggers; migration must remove them before backfilling. */
    private fun installRestampingTriggers(db: SupportSQLiteDatabase) {
        for (table in listOf("notes", "medicines", "doses"))
            db.execSQL("CREATE TRIGGER `${table}_changed` AFTER UPDATE ON `$table` WHEN NEW.updatedAt = OLD.updatedAt BEGIN UPDATE `$table` SET uid='restamped', updatedAt=OLD.updatedAt+1 WHERE id=NEW.id; END")
    }
}
