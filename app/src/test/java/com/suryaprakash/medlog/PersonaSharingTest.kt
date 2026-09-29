package com.suryaprakash.medlog

import com.suryaprakash.medlog.clinical.Band
import com.suryaprakash.medlog.clinical.DangerRules
import com.suryaprakash.medlog.clinical.Level
import com.suryaprakash.medlog.clinical.Limits
import com.suryaprakash.medlog.clinical.PersonContext
import com.suryaprakash.medlog.data.CarePlan
import com.suryaprakash.medlog.data.Medicine
import com.suryaprakash.medlog.data.Profile
import com.suryaprakash.medlog.data.onto
import com.suryaprakash.medlog.meds.Pills
import com.suryaprakash.medlog.nlu.Fact
import com.suryaprakash.medlog.nlu.Reading
import com.suryaprakash.medlog.nlu.Source
import com.suryaprakash.medlog.nutrition.Feeds
import com.suryaprakash.medlog.sync.RestoreSql
import com.suryaprakash.medlog.sync.SqlSyncStore
import com.suryaprakash.medlog.sync.SyncEffects
import com.suryaprakash.medlog.sync.SyncRunner
import com.suryaprakash.medlog.sync.SyncSql
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.Random
import java.util.concurrent.Executors

/**
 * Round 2 persona tests: two-way sharing between Kamala's phone (the person) and Ravi's phone (a helper's replica of her data), plus
 * a daughter's phone that joins later. Real SQLite (JDBC) + the real triggers + SyncRunner + FakeRelay, as ReplicaTest does.
 * The rule under test (docs/WORK_PLAN.md): "Helper and Self must be a bi-directional CRUD. Nothing should miss on any device."
 * A test marked @Ignore("BUG Bnn: ...") is a real finding: the expectation is kept, only the build is kept green.
 * Tests named documented_* pin down a known limit (last write wins) so a change of behaviour is noticed.
 */
class PersonaSharingTest {
    private val files = ArrayList<File>()
    private val conns = ArrayList<Connection>()
    private val pools = ArrayList<java.util.concurrent.ExecutorService>()

    @After fun close() { pools.forEach { it.shutdownNow() }; conns.forEach { runCatching { it.close() } }; files.forEach { it.delete() } }

    private val hour = 3600_000L
    private val base = 1_800_000_000_000L          // "today 00:00"
    private val eightAm = base + 8 * hour

    // ───────── harness ─────────

    private fun schema(): org.json.JSONArray {
        val f = listOf("schemas", "app/schemas").map { File("$it/com.suryaprakash.medlog.data.MedDb/5.json") }.first { it.exists() }
        return JSONObject(f.readText()).getJSONObject("database").getJSONArray("entities")
    }

    private fun freshDb(device: String): Connection {
        val f = File.createTempFile("medlog", ".db").also { files += it }
        val c = DriverManager.getConnection("jdbc:sqlite:${f.absolutePath}").also { conns += it }
        val ents = schema()
        for (i in 0 until ents.length()) {
            val e = ents.getJSONObject(i); val t = e.getString("tableName")
            c.createStatement().use { it.execute(e.getString("createSql").replace("\${TABLE_NAME}", t)) }
            val idx = e.optJSONArray("indices")
            if (idx != null) for (j in 0 until idx.length()) c.createStatement().use { it.execute(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", t)) }
        }
        for (s in SyncSql.onOpen()) c.createStatement().use { it.execute(s) }
        x(c, "UPDATE sync_state SET v='$device' WHERE k='device'")
        return c
    }

    private fun x(c: Connection, sql: String) { c.createStatement().use { it.execute(sql) } }
    private fun xa(c: Connection, sql: String, vararg args: Any?) = JdbcSqlDb(c).exec(sql, args.toList())
    private fun q(c: Connection, sql: String) = JdbcSqlDb(c).query(sql)
    private fun one(c: Connection, sql: String): Any? = q(c, sql).firstOrNull()?.values?.firstOrNull()
    private fun n(c: Connection, sql: String): Long = (one(c, sql) as Long?) ?: 0L
    private fun pause() = Thread.sleep(5)

    private class Fx : SyncEffects {
        val calls = ArrayList<String>()
        var onMedicine: (String) -> Unit = {}
        override suspend fun medicineChanged(uid: String) { calls += "changed:$uid"; onMedicine(uid) }
        override suspend fun medicineStopped(uid: String) { calls += "stopped:$uid" }
        override suspend fun doseClosed(uid: String) { calls += "cancelDose:$uid" }
        override suspend fun reschedule() { calls += "reschedule" }
        override fun refreshWidget() { calls += "widget" }
    }

    /** One phone: its own database, its sync runner, the shared relay. [name] is also its device id (until it is wiped). */
    private inner class Ph(val name: String, val relay: FakeRelay, val fx: SyncEffects = SyncEffects.NONE) {
        val c = freshDb(name)
        val logs = ArrayList<String>()
        val out = ArrayList<String>()
        val disp = Executors.newSingleThreadExecutor().also { pools += it }.asCoroutineDispatcher()
        val runner = SyncRunner({ SqlSyncStore(JdbcSqlDb(c)) }, { out += it; relay.publish(name, it) }, fx, disp, CoroutineScope(disp),
            debounceMs = 1_000_000L, helloEveryMs = 1_000_000L, log = { logs += it })
        init { relay.join(name) { json -> runBlocking { runner.onMessage(json) } } }
        fun push() = runBlocking { runner.publishNow() }
        fun start() = runBlocking { runner.start() }
        fun hello() = runBlocking { runner.hello() }
        fun reopen() = runBlocking { runner.reopen() }
        val device: String get() = one(c, "SELECT v FROM sync_state WHERE k='device'") as String
    }

    private fun medicine(c: Connection, name: String, times: String = "08:00", pills: Int? = null, asNeeded: Int = 0, form: String = "tablet"): Long {
        x(c, "INSERT INTO medicines(name,strength,form,amount,food,times,days,startDate,critical,asNeeded,minGapHours,purpose,pillsLeft,active,bloodThinner,changedAt,changeNote,shape,color) " +
            "VALUES('$name','','$form','1','any','$times','',1,0,$asNeeded,4,'',${pills ?: "NULL"},1,0,1,'','','')")
        return medId(c, name)
    }
    private fun medId(c: Connection, name: String): Long = one(c, "SELECT id FROM medicines WHERE name = '$name'") as Long
    private fun medUid(c: Connection, name: String): String = one(c, "SELECT uid FROM medicines WHERE name = '$name'") as String
    private fun dose(c: Connection, med: Long, at: Long) = x(c, "INSERT OR IGNORE INTO doses(medicineId,scheduledAt,status,reminded,helperAlerted) VALUES($med, $at, 'DUE', 0, 0)")
    private fun note(c: Connection, text: String, problem: String? = null, count: Int? = null, triage: String = "GREEN", at: Long = 1000) =
        x(c, "INSERT INTO notes(kind, problemId, occurredAt, createdAt, details, count, triage, triageReasons, text) VALUES('SYMPTOM', ${problem?.let { "'$it'" } ?: "NULL"}, $at, $at, '{}', ${count ?: "NULL"}, '$triage', '', '$text')")

    private fun kamalaPlan(limits: Limits = Limits()) = CarePlan(
        doctors = listOf(CarePlan.Doctor("Dr Rao", "Cancer", "9800011111")), treatments = listOf("Chemotherapy"),
        emergencies = listOf("vomit_blood"), emergenciesAsked = true, limits = limits).toJson()

    private fun putProfile(c: Connection, r: Map<String, Any?>) = xa(c,
        "INSERT OR REPLACE INTO profile(id,name,dob,sex,bloodGroup,hospitalId,conditions,allergies,doctorName,doctorPhone,onBloodThinner,notes,plan) VALUES(1,?,?,?,?,?,?,?,?,?,?,?,?)",
        r["name"], r["dob"], r["sex"], r["bloodGroup"], r["hospitalId"], r["conditions"], r["allergies"], r["doctorName"], r["doctorPhone"], r["onBloodThinner"], r["notes"], r["plan"])
    private fun profileRow(c: Connection): Map<String, Any?> = q(c, "SELECT * FROM profile").first()

    /** What Repo.person() builds from the database, so the danger rules can be run against a phone's own copy of the data. */
    private fun personOf(c: Connection): PersonContext {
        val p = profileRow(c)
        val plan = CarePlan.parse(p["plan"] as String)
        val cond = p["conditions"] as String
        return PersonContext(67, (p["onBloodThinner"] as Long) != 0L, cond, plan.limits, DangerRules.cancerCareOf(cond, plan.treatments))
    }
    private fun temp(f: Double) = mapOf("temperature" to Fact(f, Source.ASKED))
    private fun chillsLevel(c: Connection, f: Double) = DangerRules.evaluate("chills", temp(f), emptyList(), emptyList(), personOf(c), base).level

    private fun canon(j: JSONObject?): String = j?.let { o -> o.keys().asSequence().sorted().joinToString(",") { "$it=${o.opt(it)}" } } ?: "-"

    /** Everything that is shared, with the winning version of each row: two phones that hold the same are identical. */
    private fun snap(c: Connection): List<String> {
        val st = SqlSyncStore(JdbcSqlDb(c))
        return q(c, "SELECT tbl, uid, origin, oseq, at, `by` AS b, del FROM sync_rows ORDER BY tbl, uid").map { r ->
            val row = if ((r["del"] as Long) == 0L) canon(st.readRow(r["tbl"] as String, r["uid"] as String)) else "deleted"
            val cols = q(c, "SELECT col, origin, oseq, at, `by` AS b FROM sync_cols WHERE tbl = '${r["tbl"]}' AND uid = '${r["uid"]}' ORDER BY col").joinToString(";") { "${it["col"]}=${it["origin"]}#${it["oseq"]}@${it["at"]}by${it["b"]}" }
            "${r["tbl"]}|${r["uid"]}|${r["origin"]}#${r["oseq"]}@${r["at"]}by${r["b"]}|$row|$cols"
        }
    }

    private fun profileOf(r: Map<String, Any?>) = Profile(1, r["name"] as String, r["dob"] as String, r["sex"] as String, r["bloodGroup"] as String, r["hospitalId"] as String,
        r["conditions"] as String, r["allergies"] as String, r["doctorName"] as String, r["doctorPhone"] as String, (r["onBloodThinner"] as Long) != 0L, r["notes"] as String, r["plan"] as String)

    /**
     * What "My details" > Save does now: it writes onto the FRESHEST row only what the page changed (Repo.updateProfile + Profile.onto), as a
     * plain UPDATE of the row, so only the columns whose value really changed are stamped.
     */
    private fun saveMyDetails(c: Connection, opened: Map<String, Any?>, edited: Profile) {
        val o = edited.onto(profileOf(opened), profileOf(profileRow(c)))
        xa(c, "UPDATE profile SET name=?, dob=?, sex=?, bloodGroup=?, hospitalId=?, conditions=?, allergies=?, doctorName=?, doctorPhone=?, onBloodThinner=?, notes=?, plan=? WHERE id = 1",
            o.name, o.dob, o.sex, o.bloodGroup, o.hospitalId, o.conditions, o.allergies, o.doctorName, o.doctorPhone, if (o.onBloodThinner) 1L else 0L, o.notes, o.plan)
    }

    private fun medicineOf(r: Map<String, Any?>) = Medicine(id = r["id"] as Long, name = r["name"] as String, strength = r["strength"] as String, form = r["form"] as String,
        amount = r["amount"] as String, food = r["food"] as String, times = r["times"] as String, days = r["days"] as String, startDate = r["startDate"] as Long,
        endDate = r["endDate"] as Long?, critical = (r["critical"] as Long) != 0L, asNeeded = (r["asNeeded"] as Long) != 0L, minGapHours = (r["minGapHours"] as Long).toInt(),
        purpose = r["purpose"] as String, photoPath = r["photoPath"] as String?, pillsLeft = r["pillsLeft"] as Double?, active = (r["active"] as Long) != 0L,
        bloodThinner = (r["bloodThinner"] as Long) != 0L, changedAt = r["changedAt"] as Long, changeNote = r["changeNote"] as String, calendarEventId = r["calendarEventId"] as Long?,
        shape = r["shape"] as String, color = r["color"] as String, pillsAt = r["pillsAt"] as Long, feedInfo = r["feedInfo"] as String)

    /** What a medicine page's Save does now: Repo.updateMedicine { fresh -> edited.onto(original, fresh) }, as a plain UPDATE of the row. */
    private fun saveMedicinePage(c: Connection, id: Long, original: Medicine, edited: Medicine) {
        val o = edited.onto(original, medicineOf(q(c, "SELECT * FROM medicines WHERE id = $id").first()))
        xa(c, "UPDATE medicines SET name=?, strength=?, form=?, amount=?, food=?, times=?, days=?, startDate=?, endDate=?, critical=?, asNeeded=?, minGapHours=?, purpose=?, pillsLeft=?, active=?, bloodThinner=?, changedAt=?, changeNote=?, shape=?, color=?, pillsAt=?, feedInfo=? WHERE id = ?",
            o.name, o.strength, o.form, o.amount, o.food, o.times, o.days, o.startDate, o.endDate, if (o.critical) 1L else 0L, if (o.asNeeded) 1L else 0L, o.minGapHours, o.purpose, o.pillsLeft,
            if (o.active) 1L else 0L, if (o.bloodThinner) 1L else 0L, o.changedAt, o.changeNote, o.shape, o.color, o.pillsAt, o.feedInfo, id)
    }

    /** Pills left, as the phone works it out (meds.Pills over the doses taken since the count). */
    private fun pillsLeft(c: Connection, name: String): Double? {
        val taken = q(c, Pills.TAKEN_SINCE).associate { it["medicineId"] as Long to (it["n"] as Long).toInt() }
        val m = medicineOf(q(c, "SELECT * FROM medicines WHERE name = '$name'").first())
        return Pills.left(m, taken[m.id] ?: 0)
    }
    private fun same(a: Ph, b: Ph) = assertEquals("${a.name} and ${b.name} differ", snap(a.c), snap(b.c))

    private inner class Fam {
        val relay = FakeRelay()
        val kfx = Fx()
        val kamala = Ph("kamala", relay, kfx)
        lateinit var ravi: Ph
        val dexa: Long; val pantoUid: String

        init {
            // Kamala's phone as it is after set-up: profile with her plan, three medicines, today's doses, notes, an appointment, two helpers
            putProfile(kamala.c, mapOf("name" to "Kamala", "dob" to "1959-03-12", "sex" to "F", "bloodGroup" to "B+", "hospitalId" to "H-118", "conditions" to "Cancer",
                "allergies" to "", "doctorName" to "Dr Rao", "doctorPhone" to "9800011111", "onBloodThinner" to 0L, "notes" to "", "plan" to kamalaPlan()))
            val panto = medicine(kamala.c, "Pantoprazole", "07:30", pills = 28)
            dexa = medicine(kamala.c, "Dexamethasone", "08:00", pills = 14)
            medicine(kamala.c, "Paracetamol", "", pills = 20, asNeeded = 1)
            dose(kamala.c, panto, base + 7 * hour + 1800_000); dose(kamala.c, dexa, eightAm); dose(kamala.c, dexa, base + 12 * hour)
            pantoUid = medUid(kamala.c, "Pantoprazole")
            note(kamala.c, "Cough for 3 weeks", "cough", triage = "AMBER")
            note(kamala.c, "Vomited twice", "vomiting", count = 2)
            x(kamala.c, "INSERT INTO appointments(at,doctor,place,purpose,done) VALUES(${base + 26 * hour},'Dr Rao','Oncology OPD','chemo cycle 3',0)")
            x(kamala.c, "INSERT INTO helpers(name,phone,relation,sos,alerts,pairId,pairKey,canSeeNotes,sortOrder) VALUES('Ravi','9811100001','son',1,1,'pair-ravi','secret-pair-key-ravi',1,0)")
            x(kamala.c, "INSERT INTO helpers(name,phone,relation,sos,alerts,pairId,pairKey,canSeeNotes,sortOrder) VALUES('Priya','9811100002','daughter',1,1,'pair-priya','secret-pair-key-priya',1,1)")
            x(kamala.c, "INSERT INTO doc_lines(source,content,importedAt) VALUES('Lab 3 Sep','Hb 10.2 g/dL',${base - 5 * 24 * hour})")
            kamala.push()
        }

        /** Ravi installs MedLog and pairs, a day later (the relay has forgotten Kamala's first message by then). */
        fun ravisPhonePairs(): Ph {
            relay.advance(13 * hour)
            ravi = Ph("ravi", relay)
            ravi.start(); relay.pump()
            return ravi
        }
    }

    // ───────── 1. Ravi pairs: the replica fills ─────────

    @Test fun s1_raviPairs_theReplicaFillsWithEverythingOfKamalas() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        same(f.kamala, ravi)
        assertEquals(3L, n(ravi.c, "SELECT count(*) FROM medicines"))
        assertEquals(3L, n(ravi.c, "SELECT count(*) FROM doses"))
        assertEquals(2L, n(ravi.c, "SELECT count(*) FROM notes"))
        assertEquals(1L, n(ravi.c, "SELECT count(*) FROM appointments"))
        assertEquals(2L, n(ravi.c, "SELECT count(*) FROM helpers"))
        assertEquals(1L, n(ravi.c, "SELECT count(*) FROM doc_lines"))
        assertEquals("Kamala", one(ravi.c, "SELECT name FROM profile"))
        assertEquals("Cancer", one(ravi.c, "SELECT conditions FROM profile"))
        // the dose points at the right medicine on Ravi's phone (local ids differ, uids match)
        assertEquals("Dexamethasone", one(ravi.c, "SELECT m.name FROM doses d JOIN medicines m ON m.id = d.medicineId WHERE d.scheduledAt = $eightAm"))
        // her plan travelled with the profile: the cancer doctor and her treatment
        val plan = CarePlan.parse(one(ravi.c, "SELECT plan FROM profile") as String)
        assertEquals("Dr Rao", plan.doctors.single().name); assertEquals(listOf("Chemotherapy"), plan.treatments)
        // nothing is created on the replica by itself, and nothing rang on Ravi's phone (a replica has SyncEffects.NONE, SyncHub.kt:105-108)
        assertEquals(0L, n(ravi.c, "SELECT count(*) FROM sync_rows WHERE origin = 'ravi'"))
    }

    @Test fun s1_theBluetoothPairingSecretsOfHerHelpersNeverLeaveHerPhone() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        assertTrue(f.kamala.out.isNotEmpty())
        assertTrue("a pair key travelled", f.kamala.out.none { it.contains("secret-pair-key") })
        assertEquals(2L, n(ravi.c, "SELECT count(*) FROM helpers WHERE pairKey IS NULL AND pairId IS NULL"))
    }

    // ───────── 2. Ravi sets her personal limits ─────────

    private fun ravisLimits() = Limits(
        mapOf("temp" to Band(amberHigh = 102.0, redHigh = 102.0),
            "bpSys" to Band(amberHigh = 160.0, redHigh = 180.0), "bpDia" to Band(amberHigh = 100.0, redHigh = 110.0),
            "spo2" to Band(redLow = 88.0)),
        doctorConfirmed = true, setBy = "Ravi", setAt = base)

    private fun ravisSetsLimits(ravi: Ph) {
        val plan = CarePlan.parse(one(ravi.c, "SELECT plan FROM profile") as String)
        xa(ravi.c, "UPDATE profile SET plan = ?", plan.copy(limits = ravisLimits()).toJson())
    }

    @Test fun s2_limitsSetOnRavisPhoneAreUsedByTheRulesOnKamalasPhone() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        // before: the general cancer rule, 100.2 F on chemotherapy is RED
        assertEquals(Level.RED, chillsLevel(f.kamala.c, 100.2))
        pause()
        ravisSetsLimits(ravi)
        f.kfx.calls.clear()
        ravi.push(); f.relay.pump()
        val limits = CarePlan.parse(one(f.kamala.c, "SELECT plan FROM profile") as String).limits
        assertTrue(limits.doctorConfirmed); assertEquals("Ravi", limits.setBy)
        assertEquals(Level.GREEN, chillsLevel(f.kamala.c, 100.2))          // her doctor said 102
        assertEquals(Level.RED, chillsLevel(f.kamala.c, 102.4))
        val bp = { s: Double, d: Double -> DangerRules.evaluate(null, emptyMap(), listOf(Reading("bp", s, d)), emptyList(), personOf(f.kamala.c), base) }
        assertEquals(Level.AMBER, bp(168.0, 96.0).level); assertNull(bp(168.0, 96.0).needsLimit)
        assertEquals(Level.RED, bp(182.0, 96.0).level)
        assertEquals(Level.RED, DangerRules.evaluate(null, emptyMap(), listOf(Reading("spo2", 87.0)), emptyList(), personOf(f.kamala.c), base).level)
        assertTrue(f.kfx.calls.toString(), "widget" in f.kfx.calls)
        same(f.kamala, ravi)
    }

    @Test fun s2_andBackAgain_kamalaChangesHerDoctorAndRaviSeesIt() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        pause(); ravisSetsLimits(ravi); ravi.push(); f.relay.pump()
        pause()
        val plan = CarePlan.parse(one(f.kamala.c, "SELECT plan FROM profile") as String)
        xa(f.kamala.c, "UPDATE profile SET plan = ?", plan.copy(doctors = plan.doctors + CarePlan.Doctor("Dr Iyer", "Family doctor", "9800022222")).toJson())
        f.kamala.push(); f.relay.pump()
        val onRavi = CarePlan.parse(one(ravi.c, "SELECT plan FROM profile") as String)
        assertEquals(listOf("Dr Rao", "Dr Iyer"), onRavi.doctors.map { it.name })
        assertEquals(102.0, onRavi.limits.band("temp")!!.redHigh!!, 0.0)     // and his limits are still there
    }

    /** B75 (fixed): "My details" > Save used to write the whole profile row it loaded when the page opened, plan included. */
    @Test fun s2_limitsRaviSetWhileKamalaHasMyDetailsOpenSurviveHerSave() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        val opened = profileRow(f.kamala.c)                                 // she opens "My details": the page holds this copy while section != null
        pause(); ravisSetsLimits(ravi); ravi.push(); f.relay.pump()
        assertEquals(Level.GREEN, chillsLevel(f.kamala.c, 100.2))           // the limits reached her phone
        pause()
        saveMyDetails(f.kamala.c, opened, profileOf(opened).copy(conditions = "Cancer, diabetes"))  // she taps Save: only what the page changed goes onto the freshest row
        f.kamala.push(); f.relay.pump()
        assertEquals("Cancer, diabetes", one(ravi.c, "SELECT conditions FROM profile"))
        assertEquals("the limits Ravi set were erased by Kamala's Save", Level.GREEN, chillsLevel(f.kamala.c, 100.2))
        assertEquals(Level.GREEN, chillsLevel(ravi.c, 100.2))
        same(f.kamala, ravi)
    }

    @Test fun s2_profileConcurrentEditsOfDifferentFieldsBothSurvive() {
        // Ravi (limits, in plan) and Kamala (conditions) edit the profile while neither has heard the other. Each column has its own version,
        // so both edits survive on both phones (before per-column versions only the later row survived).
        val f = Fam(); val ravi = f.ravisPhonePairs()
        pause(); ravisSetsLimits(ravi)
        pause(); xa(f.kamala.c, "UPDATE profile SET conditions = 'Cancer, diabetes'")
        ravi.push(); f.kamala.push(); f.relay.pump()
        same(f.kamala, ravi)
        assertEquals("Cancer, diabetes", one(ravi.c, "SELECT conditions FROM profile"))
        assertEquals(102.0, personOf(ravi.c).limits.band("temp")!!.redHigh!!, 0.0)
        assertEquals(102.0, personOf(f.kamala.c).limits.band("temp")!!.redHigh!!, 0.0)
    }

    // ───────── 3. A new medicine, a dose marked by the helper, and the same dose tapped by Kamala ─────────

    @Test fun s3_raviAddsAnAntiNauseaMedicine_kamalasPhoneGetsItAndReschedules_andPlansItsDoses() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        // Kamala's phone plans doses for a changed medicine (what OwnEffects.medicineChanged + Scheduler.reschedule do)
        f.kfx.onMedicine = { uid -> x(f.kamala.c, "INSERT OR IGNORE INTO doses(medicineId,scheduledAt,status,reminded,helperAlerted) SELECT id, ${base + 14 * hour}, 'DUE', 0, 0 FROM medicines WHERE uid = '$uid'") }
        pause(); medicine(ravi.c, "Ondansetron", "08:00,14:00,20:00", pills = 30)
        f.kfx.calls.clear()
        ravi.push(); f.relay.pump()
        val uid = medUid(ravi.c, "Ondansetron")
        assertEquals("Ondansetron", one(f.kamala.c, "SELECT name FROM medicines WHERE uid = '$uid'"))
        assertTrue(f.kfx.calls.toString(), "changed:$uid" in f.kfx.calls && "reschedule" in f.kfx.calls)
        assertEquals(1L, n(f.kamala.c, "SELECT count(*) FROM doses d JOIN medicines m ON m.id = d.medicineId WHERE m.uid = '$uid'"))
        // her new dose flows back to Ravi's replica
        f.kamala.push(); f.relay.pump()
        assertEquals(1L, n(ravi.c, "SELECT count(*) FROM doses d JOIN medicines m ON m.id = d.medicineId WHERE m.uid = '$uid'"))
        same(f.kamala, ravi)
    }

    @Test fun s3_raviMarksTheEightAmDoseTakenWhileSheSleeps_herPhoneCancelsThatAlarm() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        val doseUid = one(f.kamala.c, "SELECT uid FROM doses WHERE scheduledAt = $eightAm") as String
        assertEquals(doseUid, one(ravi.c, "SELECT uid FROM doses WHERE scheduledAt = $eightAm"))
        pause()
        x(ravi.c, "UPDATE doses SET status = 'TAKEN', actedAt = ${eightAm + 4 * 60_000} WHERE scheduledAt = $eightAm")
        f.kfx.calls.clear()
        ravi.push(); f.relay.pump()
        assertEquals("TAKEN", one(f.kamala.c, "SELECT status FROM doses WHERE scheduledAt = $eightAm"))
        assertEquals(listOf("cancelDose:$doseUid", "reschedule", "widget"), f.kfx.calls)
        // if she taps it now, the app finds it TAKEN already and asks first (Scheduler.take returns ALREADY): the status she would read
        assertEquals("TAKEN", one(f.kamala.c, "SELECT status FROM doses WHERE uid = '$doseUid'"))
    }

    @Test fun s3_bothMarkTheSameDoseTakenAtDifferentTimes_convergeToOneRow_andOnePillIsCounted() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        val noon = base + 12 * hour
        // Kamala taps "I took it" (dose TAKEN and one pill fewer), and a moment later Ravi taps it on her replica, before either has heard the other
        x(f.kamala.c, "UPDATE doses SET status = 'TAKEN', actedAt = ${noon + 60_000} WHERE scheduledAt = $noon")
        pause()
        x(ravi.c, "UPDATE doses SET status = 'TAKEN', actedAt = ${noon + 30_000} WHERE scheduledAt = $noon")
        f.kamala.push(); ravi.push(); f.relay.pump()
        same(f.kamala, ravi)
        for (p in listOf(f.kamala, ravi)) {
            assertEquals("no duplicate dose row on ${p.name}", 1L, n(p.c, "SELECT count(*) FROM doses WHERE scheduledAt = $noon"))
            assertEquals(3L, n(p.c, "SELECT count(*) FROM doses"))
            assertEquals("TAKEN", one(p.c, "SELECT status FROM doses WHERE scheduledAt = $noon"))
            assertEquals(noon + 30_000, n(p.c, "SELECT actedAt FROM doses WHERE scheduledAt = $noon"))   // the later tap (Ravi's) is the version both keep
            assertEquals(13.0, pillsLeft(p.c, "Dexamethasone"))
        }
    }

    @Test fun s3_twoPhonesMakeTheSameDoseSlot_itIsOneRow() {
        // both phones have the medicine; each creates the 14:00 dose of it (the unique index on medicine+time would refuse two)
        val f = Fam(); val ravi = f.ravisPhonePairs()
        val t = base + 14 * hour
        dose(f.kamala.c, f.dexa, t)
        dose(ravi.c, medId(ravi.c, "Dexamethasone"), t)
        f.kamala.push(); ravi.push(); f.relay.pump()
        same(f.kamala, ravi)
        assertEquals(1L, n(f.kamala.c, "SELECT count(*) FROM doses WHERE scheduledAt = $t"))
        assertEquals(1L, n(ravi.c, "SELECT count(*) FROM doses WHERE scheduledAt = $t"))
    }

    @Test fun s3_raviChangesTheTimes_dropsTheFutureDosesLocally_andKamalasPhonePlansThemAgainOnBothPhones() {
        // the medicine edit page does medicines.update + doses.dropFuture (MedsScreens.kt:284-285); Kamala's phone then plans doses again
        val f = Fam(); val ravi = f.ravisPhonePairs()
        val newSlot = base + 21 * hour
        f.kfx.onMedicine = { uid -> x(f.kamala.c, "INSERT OR IGNORE INTO doses(medicineId,scheduledAt,status,reminded,helperAlerted) SELECT id, $newSlot, 'DUE', 0, 0 FROM medicines WHERE uid = '$uid'") }
        pause()
        x(ravi.c, "UPDATE medicines SET times = '08:00,21:00', changeNote = 'times changed' WHERE name = 'Dexamethasone'")
        x(ravi.c, "DELETE FROM doses WHERE medicineId = ${medId(ravi.c, "Dexamethasone")} AND scheduledAt > ${eightAm + 1} AND status = 'DUE'")
        ravi.push(); f.relay.pump()
        f.kamala.push(); f.relay.pump()
        same(f.kamala, ravi)
        assertEquals("08:00,21:00", one(f.kamala.c, "SELECT times FROM medicines WHERE name = 'Dexamethasone'"))
        assertEquals(1L, n(f.kamala.c, "SELECT count(*) FROM doses WHERE scheduledAt = $newSlot"))
        assertEquals(1L, n(ravi.c, "SELECT count(*) FROM doses WHERE scheduledAt = $newSlot"))
        assertEquals(1L, n(ravi.c, "SELECT count(*) FROM doses WHERE scheduledAt = $eightAm"))
    }

    /** B76a (fixed): the pill count used to be a counter kept in a shared row; now it is the count at the last refill minus the TAKEN doses since (meds.Pills). */
    @Test fun s3_twoDifferentDosesTakenOnTwoPhonesAtOnce_bothPillsAreCounted() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        val noon = base + 12 * hour
        x(f.kamala.c, "UPDATE doses SET status = 'TAKEN', actedAt = ${eightAm + 1} WHERE scheduledAt = $eightAm")
        pause()
        x(ravi.c, "UPDATE doses SET status = 'TAKEN', actedAt = ${noon + 1} WHERE scheduledAt = $noon")
        f.kamala.push(); ravi.push(); f.relay.pump()
        same(f.kamala, ravi)
        assertEquals(2L, n(f.kamala.c, "SELECT count(*) FROM doses WHERE status = 'TAKEN'"))
        assertEquals("two doses were taken, so two pills are gone", 12.0, pillsLeft(f.kamala.c, "Dexamethasone"))
        assertEquals(12.0, pillsLeft(ravi.c, "Dexamethasone"))
    }

    /** B76b (fixed): the medicine edit page used to save every column of the copy it loaded. */
    @Test fun s3_aMedicineRaviStoppedWhileKamalaHadItsPageOpen_isNotStartedAgainByHerSave() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        val opened = medicineOf(q(f.kamala.c, "SELECT * FROM medicines WHERE name = 'Dexamethasone'").first())     // the page holds this copy
        pause(); x(ravi.c, "UPDATE medicines SET active = 0 WHERE name = 'Dexamethasone'")
        ravi.push(); f.relay.pump()
        assertEquals(0L, n(f.kamala.c, "SELECT active FROM medicines WHERE name = 'Dexamethasone'"))
        pause()
        // she only changes the purpose and taps Save: the page writes onto the freshest row only what it changed, so active stays 0
        saveMedicinePage(f.kamala.c, opened.id, opened, opened.copy(purpose = "with food"))
        f.kamala.push(); f.relay.pump()
        assertEquals("with food", one(ravi.c, "SELECT purpose FROM medicines WHERE name = 'Dexamethasone'"))
        assertEquals("the medicine Ravi stopped is active again", 0L, n(ravi.c, "SELECT active FROM medicines WHERE name = 'Dexamethasone'"))
        same(f.kamala, ravi)
    }

    // ───────── 4. Ravi offline two days ─────────

    @Test fun s4_kamalaLogsFiveVomitsWhileRaviIsOfflineTwoDays_afterHelloHeHasAllOfThem() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        f.relay.setOnline("ravi", false)
        for (i in 1..5) { pause(); note(f.kamala.c, "Vomited (episode $i)", "vomiting", count = 1, triage = "GREEN", at = base + i * hour); f.kamala.push(); f.relay.pump() }
        f.relay.advance(48 * hour)                     // longer than the relay's 12 hours: the notes are gone from the mailbox
        f.relay.setOnline("ravi", true)
        f.relay.pump()
        assertEquals("nothing arrives without a HELLO", 2L, n(ravi.c, "SELECT count(*) FROM notes"))
        ravi.start(); f.relay.pump()                   // he opens the app: HELLO
        assertEquals(7L, n(ravi.c, "SELECT count(*) FROM notes"))
        assertEquals(5L, n(ravi.c, "SELECT count(*) FROM notes WHERE text LIKE 'Vomited (episode %'"))
        same(f.kamala, ravi)
    }

    @Test fun s4_theRelayLosesHalfTheMessages_theSixHourlyHelloStillBringsEverything() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        f.relay.dropRate = 0.5
        for (i in 1..8) { pause(); note(f.kamala.c, "Cough spell $i", "cough"); f.kamala.push() }
        f.relay.pump()
        f.relay.dropRate = 0.0
        ravi.hello(); f.relay.pump()
        assertEquals(10L, n(ravi.c, "SELECT count(*) FROM notes"))
        same(f.kamala, ravi)
    }

    // ───────── 5. Both edit the same medicine at nearly the same time ─────────

    @Test fun s5_bothEditTheSameFieldOfOneMedicine_bothPhonesEndIdentical_andTheLaterEditWins() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        x(f.kamala.c, "UPDATE medicines SET times = '08:30' WHERE name = 'Dexamethasone'")
        pause()
        x(ravi.c, "UPDATE medicines SET times = '07:00' WHERE name = 'Dexamethasone'")
        ravi.push(); f.kamala.push(); f.relay.pump()
        same(f.kamala, ravi)
        assertEquals("07:00", one(f.kamala.c, "SELECT times FROM medicines WHERE name = 'Dexamethasone'"))
        // the other delivery order gives the same answer
        val g = Fam(); val ravi2 = g.ravisPhonePairs()
        x(g.kamala.c, "UPDATE medicines SET times = '08:30' WHERE name = 'Dexamethasone'")
        pause()
        x(ravi2.c, "UPDATE medicines SET times = '07:00' WHERE name = 'Dexamethasone'")
        g.kamala.push(); ravi2.push(); g.relay.pump()
        same(g.kamala, ravi2)
        assertEquals("07:00", one(g.kamala.c, "SELECT times FROM medicines WHERE name = 'Dexamethasone'"))
    }

    @Test fun s5_bothEditDifferentFieldsOfOneMedicine_bothPhonesEndIdentical() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        x(f.kamala.c, "UPDATE medicines SET purpose = 'nausea cover' WHERE name = 'Dexamethasone'")
        pause()
        x(ravi.c, "UPDATE medicines SET times = '07:00' WHERE name = 'Dexamethasone'")
        ravi.push(); f.kamala.push(); f.relay.pump()
        same(f.kamala, ravi)
    }

    @Test fun s5_bothEditDifferentFieldsOfOneMedicine_bothEditsSurvive() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        x(f.kamala.c, "UPDATE medicines SET purpose = 'nausea cover' WHERE name = 'Dexamethasone'")
        pause()
        x(ravi.c, "UPDATE medicines SET times = '07:00' WHERE name = 'Dexamethasone'")
        ravi.push(); f.kamala.push(); f.relay.pump()
        assertEquals("nausea cover", one(f.kamala.c, "SELECT purpose FROM medicines WHERE name = 'Dexamethasone'"))
        assertEquals("07:00", one(f.kamala.c, "SELECT times FROM medicines WHERE name = 'Dexamethasone'"))
    }

    // ───────── 6. Ravi deletes a wrong note; Kamala edited it meanwhile ─────────

    private fun raviAddsAWrongNote(f: Fam): Ph {
        val ravi = f.ravisPhonePairs()
        pause(); note(ravi.c, "Vomited 5 times", "vomiting", count = 5, triage = "AMBER", at = base + 3 * hour)
        ravi.push(); f.relay.pump()
        assertEquals("Vomited 5 times", one(f.kamala.c, "SELECT text FROM notes WHERE text LIKE 'Vomited 5%'"))
        return ravi
    }

    @Test fun s6_raviRemovesHisWrongNote_kamalasPhoneLosesIt() {
        val f = Fam(); val ravi = raviAddsAWrongNote(f)
        pause(); x(ravi.c, "UPDATE notes SET deletedAt = ${base + 4 * hour} WHERE text = 'Vomited 5 times'")   // "Remove this note" is a soft delete (Db.kt:243)
        ravi.push(); f.relay.pump()
        assertNotEquals(null, one(f.kamala.c, "SELECT deletedAt FROM notes WHERE text = 'Vomited 5 times'"))
        assertEquals(0L, n(f.kamala.c, "SELECT count(*) FROM notes WHERE deletedAt IS NULL AND text = 'Vomited 5 times'"))
        same(f.kamala, ravi)
    }

    @Test fun s6_kamalaEditedBeforeRaviRemoved_theRemoveAndHerCorrectionBothSurvive() {
        val f = Fam(); val ravi = raviAddsAWrongNote(f)
        pause(); x(f.kamala.c, "UPDATE notes SET text = 'Vomited 2 times', count = 2, triage = 'GREEN' WHERE text = 'Vomited 5 times'")   // her correction, first
        pause(); x(ravi.c, "UPDATE notes SET deletedAt = ${base + 4 * hour} WHERE text = 'Vomited 5 times'")                                   // his remove, later, from the old copy
        f.kamala.push(); ravi.push(); f.relay.pump()
        same(f.kamala, ravi)
        assertEquals(0L, n(f.kamala.c, "SELECT count(*) FROM notes WHERE deletedAt IS NULL AND problemId = 'vomiting' AND occurredAt = ${base + 3 * hour}"))
        // "Remove" is the column deletedAt and her correction is other columns: both survive, so "Bring back" would restore her 2 times, not his old 5
        assertEquals(2L, n(f.kamala.c, "SELECT count FROM notes WHERE problemId = 'vomiting' AND occurredAt = ${base + 3 * hour}"))
    }

    @Test fun s6_kamalaEditedAfterRaviRemoved_theNoteStaysRemovedWithHerCorrection() {
        val f = Fam(); val ravi = raviAddsAWrongNote(f)
        pause(); x(ravi.c, "UPDATE notes SET deletedAt = ${base + 4 * hour} WHERE text = 'Vomited 5 times'")
        pause(); x(f.kamala.c, "UPDATE notes SET text = 'Vomited 3 times', count = 3 WHERE text = 'Vomited 5 times'")     // she has not seen the remove yet
        ravi.push(); f.kamala.push(); f.relay.pump()
        same(f.kamala, ravi)
        // his remove (the column deletedAt) is not undone by her edit of other columns; "Bring back" restores her corrected note
        assertEquals(0L, n(ravi.c, "SELECT count(*) FROM notes WHERE deletedAt IS NULL AND occurredAt = ${base + 3 * hour}"))
        assertEquals("Vomited 3 times", one(ravi.c, "SELECT text FROM notes WHERE occurredAt = ${base + 3 * hour}"))
    }

    @Test fun s6_aHardDeleteOfAnAppointmentReachesKamala_andALaterEditBringsItBack_sameOnBoth() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        pause(); x(ravi.c, "DELETE FROM appointments")
        ravi.push(); f.relay.pump()
        assertEquals(0L, n(f.kamala.c, "SELECT count(*) FROM appointments"))
        assertEquals(1L, n(f.kamala.c, "SELECT count(*) FROM sync_rows WHERE tbl = 'appointments' AND del = 1"))
        // the tombstone is a real version: a much older edit cannot bring the row back, a newer one does
        pause(); x(f.kamala.c, "INSERT INTO appointments(at,doctor,place,purpose,done) VALUES(${base + 50 * hour},'Dr Rao','','review',0)")
        f.kamala.push(); f.relay.pump()
        assertEquals(1L, n(ravi.c, "SELECT count(*) FROM appointments"))
        same(f.kamala, ravi)
    }

    // ───────── 7. A second helper joins a week later ─────────

    @Test fun s7_daughterJoinsAWeekLater_getsEverythingIncludingTheTombstones() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        pause(); x(ravi.c, "DELETE FROM appointments"); x(ravi.c, "DELETE FROM helpers WHERE name = 'Priya'")
        note(ravi.c, "Ravi: gave ORS at 6 pm", "vomiting", count = 1); ravi.push(); f.relay.pump()
        pause(); x(f.kamala.c, "UPDATE notes SET deletedAt = 5 WHERE text = 'Cough for 3 weeks'"); f.kamala.push(); f.relay.pump()
        f.relay.advance(7 * 24 * hour)
        val priya = Ph("priya", f.relay)
        priya.start(); f.relay.pump()
        same(f.kamala, priya); same(ravi, priya)
        assertEquals(0L, n(priya.c, "SELECT count(*) FROM appointments"))
        assertEquals(1L, n(priya.c, "SELECT count(*) FROM helpers"))
        assertEquals(2L, n(priya.c, "SELECT count(*) FROM sync_rows WHERE del = 1"))
        assertEquals(3L, n(priya.c, "SELECT count(*) FROM notes"))
        assertEquals(3L, n(priya.c, "SELECT count(*) FROM medicines"))
        // she can work at once, and both other phones see her edit
        pause(); x(priya.c, "UPDATE medicines SET purpose = 'sugar' WHERE name = 'Pantoprazole'"); priya.push(); f.relay.pump()
        assertEquals("sugar", one(f.kamala.c, "SELECT purpose FROM medicines WHERE name = 'Pantoprazole'"))
        assertEquals("sugar", one(ravi.c, "SELECT purpose FROM medicines WHERE name = 'Pantoprazole'"))
    }

    @Test fun s7_kamalasPhoneIsOffWhenTheDaughterJoins_ravisReplicaHandsOverEverything() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        pause(); x(ravi.c, "DELETE FROM appointments"); ravi.push(); f.relay.pump()
        f.relay.setOnline("kamala", false)
        f.relay.advance(7 * 24 * hour)
        val priya = Ph("priya", f.relay)
        priya.start(); f.relay.pump()
        same(ravi, priya)
        assertEquals(3L, n(priya.c, "SELECT count(*) FROM medicines"))
        assertEquals("Kamala", one(priya.c, "SELECT name FROM profile"))
        assertEquals(1L, n(priya.c, "SELECT count(*) FROM sync_rows WHERE del = 1"))
        // when Kamala's phone comes back, she gets what Priya did in the meantime
        pause(); x(priya.c, "INSERT INTO appointments(at,doctor,place,purpose,done) VALUES(${base + 80 * hour},'Dr Iyer','Clinic','fever check',0)"); priya.push()
        f.relay.setOnline("kamala", true); f.kamala.hello(); f.relay.pump()
        assertEquals("Dr Iyer", one(f.kamala.c, "SELECT doctor FROM appointments"))
        same(f.kamala, priya)
    }

    // ───────── 8. Privacy wipe and restore ─────────

    @Test fun s8_kamalasPrivacyWipe_helpersKeepEverything_andNothingIsSentAsDeletes() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        val priya = Ph("priya", f.relay); priya.start(); f.relay.pump()
        val before = snap(ravi.c); val oldDevice = f.kamala.device
        val sent = f.relay.sent
        for (s in SyncSql.wipe()) x(f.kamala.c, s)
        assertEquals(0L, n(f.kamala.c, "SELECT count(*) FROM medicines") + n(f.kamala.c, "SELECT count(*) FROM notes") + n(f.kamala.c, "SELECT count(*) FROM helpers") + n(f.kamala.c, "SELECT count(*) FROM profile"))
        assertNotEquals(oldDevice, f.kamala.device)
        assertEquals(sent, f.relay.sent)
        f.relay.pump()
        assertEquals(before, snap(ravi.c)); same(ravi, priya)
        assertEquals(0L, n(ravi.c, "SELECT count(*) FROM sync_rows WHERE del = 1"))
        assertEquals(3L, n(ravi.c, "SELECT count(*) FROM medicines"))
    }

    @Test fun s8_kamalaRestoresHerBackup_newDeviceId_catchesUp_noDuplicates_andDeletesMadeMeanwhileStayDeleted() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        val backup = File.createTempFile("backup", ".db").also { files += it }
        x(f.kamala.c, "VACUUM INTO '${backup.absolutePath}'".also { backup.delete() })
        val oldDevice = f.kamala.device
        // after the backup: Ravi removes the appointment and adds a note, Kamala takes the 8 am dose and logs a vomit; all shared
        pause(); x(ravi.c, "DELETE FROM appointments"); note(ravi.c, "Ravi: temp 99.1 at 9 pm", "chills")
        x(f.kamala.c, "UPDATE doses SET status = 'TAKEN', actedAt = ${eightAm + 60_000} WHERE scheduledAt = $eightAm"); note(f.kamala.c, "Vomited once", "vomiting", count = 1)
        ravi.push(); f.kamala.push(); f.relay.pump()
        val priya = Ph("priya", f.relay); priya.start(); f.relay.pump()
        same(f.kamala, ravi); same(ravi, priya)
        val expectedNotes = n(ravi.c, "SELECT count(*) FROM notes")

        // her phone is wiped, then she restores the backup on it (or on a new phone: same steps)
        for (s in SyncSql.wipe()) x(f.kamala.c, s)
        x(f.kamala.c, "ATTACH DATABASE '${backup.absolutePath}' AS bk")
        fun cols(schema: String): Map<String, List<String>> = q(f.kamala.c, "SELECT name FROM $schema.sqlite_master WHERE type='table'").associate { t ->
            (t["name"] as String) to q(f.kamala.c, "PRAGMA $schema.table_info(`${t["name"]}`)").map { it["name"] as String }
        }
        val main = cols("main").mapValues { e -> e.value.map { RestoreSql.ColInfo(it, "", false, true) } }
        for (s in RestoreSql.statements(main, cols("bk"))) x(f.kamala.c, s)
        x(f.kamala.c, "DETACH DATABASE bk")
        val newDevice = f.kamala.device
        assertNotEquals(oldDevice, newDevice)
        assertEquals(1L, n(f.kamala.c, "SELECT count(*) FROM appointments"))          // the backup still has it: a zombie until she catches up
        f.kamala.reopen(); f.relay.pump()

        same(f.kamala, ravi); same(f.kamala, priya)
        assertEquals("the appointment Ravi removed after the backup stays removed", 0L, n(f.kamala.c, "SELECT count(*) FROM appointments"))
        assertEquals(expectedNotes, n(f.kamala.c, "SELECT count(*) FROM notes"))
        assertEquals("TAKEN", one(f.kamala.c, "SELECT status FROM doses WHERE scheduledAt = $eightAm"))    // the change her old id made after the backup came back
        assertEquals(n(f.kamala.c, "SELECT count(*) FROM medicines"), n(f.kamala.c, "SELECT count(DISTINCT uid) FROM medicines"))
        assertEquals(n(f.kamala.c, "SELECT count(*) FROM notes"), n(f.kamala.c, "SELECT count(DISTINCT uid) FROM notes"))
        assertEquals(3L, n(f.kamala.c, "SELECT count(*) FROM doses"))
        assertEquals(3L, n(ravi.c, "SELECT count(*) FROM medicines"))
        // and her new edits travel under the new id
        pause(); x(f.kamala.c, "UPDATE medicines SET purpose = 'after restore' WHERE name = 'Pantoprazole'"); f.kamala.push(); f.relay.pump()
        assertEquals("after restore", one(ravi.c, "SELECT purpose FROM medicines WHERE name = 'Pantoprazole'"))
        assertEquals(newDevice, one(ravi.c, "SELECT origin FROM sync_rows WHERE tbl = 'medicines' AND uid = '${f.pantoUid}'"))
        same(f.kamala, priya)
    }

    // ───────── 9. Clocks ─────────

    /** Runs [sql] on [p] as if its clock ran [ahead] ms fast: the rows it changes are stamped that much later. */
    private fun editWithClockAhead(p: Ph, ahead: Long, sql: String) {
        val seq = n(p.c, "SELECT CAST(v AS INTEGER) FROM sync_state WHERE k='seq'")
        x(p.c, sql)
        x(p.c, "UPDATE sync_rows SET at = at + $ahead WHERE origin = '${p.device}' AND oseq > $seq")
        x(p.c, "UPDATE sync_cols SET at = at + $ahead WHERE origin = '${p.device}' AND oseq > $seq")
    }

    @Test fun s9_raviClockIsOneHourAhead_afterHeHeardFromKamala_herLaterEditsStillWin() {
        val f = Fam(); val ravi = f.ravisPhonePairs()
        pause(); editWithClockAhead(ravi, hour, "UPDATE medicines SET purpose = 'ravi ahead' WHERE name = 'Dexamethasone'")
        ravi.push(); f.relay.pump()
        assertEquals("ravi ahead", one(f.kamala.c, "SELECT purpose FROM medicines WHERE name = 'Dexamethasone'"))
        assertTrue("the far clock is logged, not shown to anyone: ${f.kamala.logs}", f.kamala.logs.isEmpty() || f.kamala.logs.all { it.contains("ahead") })
        pause(); x(f.kamala.c, "UPDATE medicines SET purpose = 'kamala later' WHERE name = 'Dexamethasone'")    // stamped above the newest time she has seen
        f.kamala.push(); f.relay.pump()
        assertEquals("kamala later", one(ravi.c, "SELECT purpose FROM medicines WHERE name = 'Dexamethasone'"))
        assertEquals("kamala later", one(f.kamala.c, "SELECT purpose FROM medicines WHERE name = 'Dexamethasone'"))
        same(f.kamala, ravi)
    }

    @Test fun documented_s9_raviClockIsOneHourAhead_anEditKamalaMadeAfterHisButBeforeHearingItLosesToHim() {
        // Nobody can know whose clock is right: Kamala's edit is later in real time (a few ms here, as it would be minutes), but it was made
        // before she received Ravi's, so nothing raised her clock. Ravi's version is 1 hour ahead and wins on both phones.
        val f = Fam(); val ravi = f.ravisPhonePairs()
        pause(); editWithClockAhead(ravi, hour, "UPDATE medicines SET purpose = 'ravi ahead' WHERE name = 'Dexamethasone'")
        pause(); x(f.kamala.c, "UPDATE medicines SET purpose = 'kamala real-time later' WHERE name = 'Dexamethasone'")
        ravi.push(); f.kamala.push(); f.relay.pump()
        same(f.kamala, ravi)
        assertEquals("ravi ahead", one(f.kamala.c, "SELECT purpose FROM medicines WHERE name = 'Dexamethasone'"))
    }

    @Test fun s9_aRemoveFromAPhoneWithAFastClockNoLongerEatsAnEditMadeLaterInRealTime() {
        // Ravi removes a note (his clock is 1 hour ahead), then Kamala corrects the same note (real time later, but she has not received the
        // remove). "Remove" is the column deletedAt and her correction is other columns, so both survive (whole-row versions lost her correction).
        val f = Fam(); val ravi = raviAddsAWrongNote(f)
        pause(); editWithClockAhead(ravi, hour, "UPDATE notes SET deletedAt = ${base + 4 * hour} WHERE text = 'Vomited 5 times'")
        pause(); x(f.kamala.c, "UPDATE notes SET text = 'Vomited 3 times', count = 3 WHERE text = 'Vomited 5 times'")
        ravi.push(); f.kamala.push(); f.relay.pump()
        same(f.kamala, ravi)
        assertEquals(0L, n(f.kamala.c, "SELECT count(*) FROM notes WHERE deletedAt IS NULL AND occurredAt = ${base + 3 * hour}"))
        assertEquals("Vomited 3 times", one(f.kamala.c, "SELECT text FROM notes WHERE occurredAt = ${base + 3 * hour}"))
    }

    // ───────── data that stays on the phone ─────────

    @Test fun documented_unshared_photosVoiceAndCalendarLinksStayOnTheirPhone() {
        val f = Fam()
        x(f.kamala.c, "UPDATE medicines SET photoPath = '/data/strip.jpg', calendarEventId = 77 WHERE name = 'Dexamethasone'")
        x(f.kamala.c, "UPDATE notes SET audioPath = '/data/voice.m4a', photoPath = '/data/rash.jpg', transcript = 'I keep coughing' WHERE text = 'Cough for 3 weeks'")
        x(f.kamala.c, "UPDATE appointments SET calendarEventId = 5")
        x(f.kamala.c, "INSERT INTO inbox(fromName, text, kind, at, acked) VALUES('Ravi', 'I am coming', 'REPLY', 1, 1)")
        f.kamala.push()
        val ravi = f.ravisPhonePairs()
        assertEquals("I keep coughing", one(ravi.c, "SELECT transcript FROM notes WHERE text = 'Cough for 3 weeks'"))     // the words travel
        assertNull(one(ravi.c, "SELECT photoPath FROM medicines WHERE name = 'Dexamethasone'"))
        assertNull(one(ravi.c, "SELECT audioPath FROM notes WHERE text = 'Cough for 3 weeks'"))
        assertNull(one(ravi.c, "SELECT photoPath FROM notes WHERE text = 'Cough for 3 weeks'"))
        assertNull(one(ravi.c, "SELECT calendarEventId FROM appointments"))
        assertEquals(0L, n(ravi.c, "SELECT count(*) FROM inbox"))
    }

    /** BUG B77: a feed's contents live in the phone's settings, keyed by the local medicine id (FoodReadings.kt:319-321). */
    @Ignore("BUG B77: the parts (kcal, protein) of a feed are kept in phone-wide settings 'feed_info' under the local medicine id (FoodReadings.kt:319-321), so a feed made on one phone shows no kcal on the other")
    @Test fun s11_aFeedKamalaMadeShowsItsKcalOnRavisPhone_andNotSomeoneElsesFeed() {
        val f = Fam()
        val kSettings = Feeds.infoWith(null, medicine(f.kamala.c, "Ensure feed", "08:00,14:00", form = "feed"),
            Feeds.Info(listOf(Feeds.Part("Ensure", "200 ml", 300.0, 12.0)), tube = true))
        f.kamala.push()
        val ravi = f.ravisPhonePairs()
        // Ravi has his own feed with the same local id 1 on his own phone (his settings are his own)
        val raviOwn = freshDb("ravi-own")
        val raviOwnFeedId = medicine(raviOwn, "Ravi shake", "08:00", form = "feed")
        val raviSettings = Feeds.infoWith(null, raviOwnFeedId, Feeds.Info(listOf(Feeds.Part("Protein shake", "250 ml", 180.0, 25.0)), tube = false))
        val replicaFeedId = medId(ravi.c, "Ensure feed")
        val seen = Feeds.infoFrom(raviSettings, replicaFeedId)      // what Nutrition.kt:73-76 does on Ravi's phone for the replica's feed
        assertTrue("Ravi's phone shows his own shake as Kamala's feed: $seen", seen == null || seen.parts.single().name == "Ensure")
        assertEquals("Ensure feed's kcal must be readable on Ravi's phone", 300.0, seen?.kcal ?: -1.0, 0.0)
        assertNotEquals("", kSettings)
    }

    // ───────── whole-system convergence ─────────

    @Test fun fuzz_threePhonesRandomEditsWithLossAndOfflineTime_allEndIdentical() {
        for (seed in 1..6) {
            val rnd = Random(seed.toLong())
            val relay = FakeRelay(random = Random(seed.toLong()))
            val phones = listOf(Ph("kamala", relay), Ph("ravi", relay), Ph("priya", relay))
            var counter = 0
            fun edit(p: Ph) {
                val c = p.c
                when (rnd.nextInt(9)) {
                    0 -> medicine(c, "M${p.name}${counter++}", "08:00", pills = 10)
                    1 -> x(c, "UPDATE medicines SET purpose = 'p${counter++}' WHERE id = (SELECT id FROM medicines ORDER BY random() LIMIT 1)")
                    2 -> x(c, "UPDATE medicines SET active = 1 - active WHERE id = (SELECT id FROM medicines ORDER BY random() LIMIT 1)")
                    3 -> note(c, "n${p.name}${counter++}", "cough", at = 1000L + counter)
                    4 -> x(c, "UPDATE notes SET deletedAt = CASE WHEN deletedAt IS NULL THEN 9 END, text = 't${counter++}' WHERE id = (SELECT id FROM notes ORDER BY random() LIMIT 1)")
                    5 -> x(c, "INSERT INTO appointments(at,doctor,place,purpose,done) VALUES(${counter++},'Dr','','',0)")
                    6 -> x(c, "DELETE FROM appointments WHERE id = (SELECT id FROM appointments ORDER BY random() LIMIT 1)")
                    7 -> x(c, "INSERT OR IGNORE INTO doses(medicineId,scheduledAt,status,reminded,helperAlerted) SELECT id, ${base + rnd.nextInt(4) * hour}, 'DUE', 0, 0 FROM medicines ORDER BY random() LIMIT 1")
                    else -> x(c, "UPDATE doses SET status = 'TAKEN', actedAt = ${counter++} WHERE id = (SELECT id FROM doses ORDER BY random() LIMIT 1)")
                }
            }
            for (step in 0 until 70) {
                val p = phones[rnd.nextInt(3)]
                edit(p); Thread.sleep(2)
                if (rnd.nextInt(4) == 0) { relay.dropRate = if (rnd.nextBoolean()) 0.4 else 0.0; p.push(); if (rnd.nextBoolean()) relay.pump() }
                if (rnd.nextInt(10) == 0) { val o = phones[rnd.nextInt(3)]; relay.setOnline(o.name, rnd.nextBoolean()) }
            }
            relay.dropRate = 0.0
            phones.forEach { relay.setOnline(it.name, true); it.push() }
            relay.pump()
            phones.forEach { it.hello() }
            relay.pump()
            phones.forEach { it.hello() }
            relay.pump()
            same(phones[0], phones[1]); same(phones[0], phones[2])
        }
    }

    // ───────── 10. the gate (code reading, mirrored here where it can be) ─────────

    @Test fun s10_aReplicaRunsWithNoEffects_soNothingRingsOnTheHelpersPhoneWhenKamalasDataArrives() {
        // SyncHub.refreshReplicas attaches every replica with SyncEffects.NONE (SyncHub.kt:105-108). With NONE, the same batch that makes
        // Kamala's phone cancel an alarm (s3) does nothing at all on Ravi's phone, whatever arrives:
        val f = Fam()
        f.relay.advance(13 * hour)
        val ravi = Ph("ravi-fx-probe", f.relay, SyncEffects.NONE)
        ravi.start(); f.relay.pump()
        assertEquals(3L, n(ravi.c, "SELECT count(*) FROM doses"))
        // and the phone-level gate is a single flag
        val v = com.suryaprakash.medlog.data.Viewing()
        v.open("pair-ravi", "Kamala")
        assertEquals("This runs on Kamala's phone.", v.notice())
    }
}
