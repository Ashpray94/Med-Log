# MedLog 3.0 work plan

Owner of the plan and of validation: the lead model (Opus). The code is written by worker agents, one per workstream.
Each worker gets one workstream with its own files. The lead reviews every diff, runs the build and tests,
and checks each item's acceptance line before merging it.

Bug IDs (B01…B51) refer to `docs/APP_ARCHITECTURE_AND_AUDIT.md`. B49–B51 were added after the audit:
- **B49**: the fever temperature question is only in the optional "tell more" part, so "fever ≥ 100.4 + confusion" and
  "fever in an older person" never fire from taps.
- **B50**: "Could you get up?" is only in "tell more", and "time on the floor" is never asked, so two of the four RED
  fall rules almost never fire.
- **B51**: "stiff neck" is never asked, so "fever with stiff neck" can never fire.

## Decisions from the owner (2026-09-29)

1. Feedback goes to **GitHub Issues**.
2. Scope: **B01–B07, B19, B22, B49–B51**, plus the points below.
3. Fever: the general alert stays **100 °F**. A helper can set a person's own limit, e.g. the owner's doctor says **102 °F** for them.
4. **Every measurement has helper-set limits** so the app doesn't flood people with false alarms.
5. Limits and all other data are **fully shared both ways** between the person's phone and every helper phone.
   Either side can create, change and delete. Nothing may be missing on any device. (Phase B)
6. **Setup asks the helper for the limits**, so they are set from day one.
7. SpO₂: below 90 is RED for a younger person. **Over 55: below 90 is AMBER**; RED is the helper's baseline.
8. BP: **over 55, never assume**. The helper enters the values the doctor accepts, and they become the AMBER and RED lines.
   The app may *suggest* values from the person's own readings, but the helper confirms them.
9. Cancer on chemotherapy or radiotherapy (doctor's numbers): vomiting **more than 4 a day**, loose stools **more
   than 4 a day**, or constipation **more than 2 days** means call the doctor.

## Build and test (every worker)

```
export ANDROID_HOME=/opt/android-sdk
echo "sdk.dir=/opt/android-sdk" > local.properties          # gitignored; needed in a fresh worktree
/opt/gradle-8.14.3/bin/gradle --console=plain -q testDebugUnitTest   # the unit tests (baseline: 40, all pass)
/opt/gradle-8.14.3/bin/gradle --console=plain -q assembleDebug       # full compile, including Compose and Room
```
If Maven answers 429 (Too Many Requests), wait 30 s and run again. A worker's job is done only when both commands
succeed and every test it added passes. Don't change files outside your workstream's list without saying so in
your report.

---

## Phase A

### W1: Safety fixes (B01, B03, B04, B05)

Files: `ui/screens/SettingsScreens.kt` (Privacy only), `MainActivity.kt`, `app/src/main/AndroidManifest.xml`,
`help/Nearby.kt`, `ui/screens/HelpScreens.kt` (PairScreen only), `meds/Scheduler.kt`, `ui/screens/MedAddFlow.kt`
(stop only), `ui/screens/HomeScreen.kt` (dose list only), `data/Db.kt` (a new DAO query if needed).

| ID | Change | Acceptance |
|---|---|---|
| B01 | Run the wipe off the main thread: `withContext(Dispatchers.IO) { app.db.clearAllTables() }` | "Yes, delete everything" doesn't crash and ends on setup |
| B03 | Keep `BROWSABLE` only if needed for Assistant; make `call`, `sos` and `help?send=` act only when the intent carries a secret extra that the widget, shortcuts and notifications add (`EXTRA_TRUSTED` = a random value kept in prefs), else open the page without acting | A plain `medlog://call` / `sos` / `help?send=` from outside opens the page and does nothing more; widget buttons still act |
| B04 | Pairing: neither side calls `acceptConnection` until the person taps **They match**; "They don't match" rejects; the key is sent only after both accepted | Unit or code-level proof: `acceptConnection` is reachable only from `confirmDigits` |
| B05 | When a medicine or feed is stopped, mark its open DUE/SNOOZED doses SKIPPED with reason "Stopped"; `Scheduler.tick` and `nextWake` ignore inactive medicines; Home's today list and counts show only active medicines' doses (keep TAKEN ones for the day) | Stopping at 8:05 leaves no open 8:00 dose; no helper alert fires for it |

### W2: Clinical rules (B02, B06, B07, B08, B19, B22, B49, B50, B51, cancer context, limits in the rules)

Files: `clinical/Triage.kt`, `clinical/Interview.kt`, `assets/clinical/catalogue.json`, `data/CarePlan.kt`,
`data/Repo.kt`, `ui/screens/TellScreen.kt`, `ui/screens/FoodReadings.kt` (ReadingsScreen and ReadingSheet only),
`ui/screens/DevicesScreen.kt`, `widget/Widget.kt` (NoteProblem only), `doctor/DoctorNote.kt`, `ui/screens/HealthScreen.kt`,
`ui/screens/NotesScreens.kt`, `ui/screens/OnboardingScreen.kt` (EMERGENCIES step and `emergenciesFor` use only),
new `clinical/Limits.kt`, tests in `app/src/test/…`.

**Limits model** (new `clinical/Limits.kt`, stored inside the care plan JSON so it is encrypted, backed up and synced):

```kotlin
/** One measure's lines. null = not set. Values are in the app's units (mmHg, %, mg/dL, °F, per minute, times a day, days). */
data class Band(val amberLow: Double? = null, val redLow: Double? = null, val amberHigh: Double? = null, val redHigh: Double? = null)

data class Limits(
    val bands: Map<String, Band> = emptyMap(),   // keys: "bpSys", "bpDia", "spo2", "sugar", "temp", "pulse", "vomit", "loose", "constipationDays"
    val doctorConfirmed: Boolean = false,          // the helper ticked "the doctor said these are OK"
    val setBy: String = "",                        // "helper", "self" or a helper's name
    val setAt: Long = 0,
)
```
`CarePlan` gets `limits: Limits` (JSON key `limits`), parsed tolerantly (missing = empty). `PersonContext` gets
`limits`, `cancerCare: Boolean` (conditions contain "Cancer" AND treatments contain "Chemotherapy" or the new
"Radiotherapy"; add "Radiotherapy" to `CarePlan.TREATMENTS`), and `ageYears` as today. `Repo.person()` fills them.

**Rule changes** (keep everything else as it is; bump `DangerRules.VERSION` to `rules-0.2.0-unreviewed`):

| Measure | No helper limit set | Helper limit set |
|---|---|---|
| SpO₂ | age ≤ 55 or unknown: < 90 RED, < 94 AMBER. **Age > 55: < 90 AMBER**, and the result carries `needsLimit = "spo2"` | < redLow → RED, < amberLow → AMBER |
| BP (reading) | age ≤ 55 or unknown: today's rules. **Age > 55: no AMBER/RED from the numbers alone**, result carries `needsLimit = "bp"`. The "≥ 180 or ≥ 120 **with symptoms** → RED" rule stays for everyone, because it is about the symptoms | ≥ redHigh → RED, ≥ amberHigh → AMBER, ≤ redLow → RED, ≤ amberLow → AMBER (sys and dia each) |
| Temperature | today's rules, except with `cancerCare`: ≥ 100.0 °F → RED "Fever during cancer treatment" | ≥ redHigh → RED, ≥ amberHigh → AMBER, ≤ 95 °F stays RED |
| Sugar, pulse | today's rules | the band decides |
| Vomiting / loose stools per 24 h | ≥ 6 AMBER (general); with `cancerCare` **> 4** AMBER | > amberHigh → AMBER, > redHigh → RED |
| Constipation | with `cancerCare`: more than 2 days → AMBER | band on `constipationDays` |

`Triage` gets `needsLimit: String? = null`. The screens show it as a quiet grey line, not a warning: "Your helper hasn't
set blood pressure limits yet. Ask them to set them in Helper controls." It never pushes alerts to helpers.

**Bug fixes**

| ID | Change | Acceptance (test) |
|---|---|---|
| B06 | The note being evaluated must not be counted twice: pass `excludeId` into `recentForRules`, or drop the `+ count` | One note "3 times" → no "6 times" AMBER; 6 real ones → AMBER |
| B07 | Same for dizziness (drop `+ 1` when the current note is already in `recent`) | 2 dizzy notes in 2 days → GREEN; 3 → AMBER |
| B19 | "Better" notes: `count = 0` and fact `better=true`; every count (`recentProblems.todayCount`, History day and By problem, Problem history, Doctor page `total`, My health symptom bars, widget "(2nd today)") skips notes whose `better` fact is `true`. The `better` field that holds a list ("what makes it better") is **not** a better-note | Headache once + "Yes, better" → "1 today", Times 1 |
| B08 | When the plan emergency path fires, call `repo.updateTriage(noteId, RED)` | The note's triage is RED in the database |
| B02 | `emergenciesFor` no longer adds chest_pain, breathless and fainted for everyone: only from the chosen risks (heart → chest_pain; breathing → breathless; falls/alone → fall, fainted; …). Remember an explicitly empty choice: store `emergenciesAsked = true` in the plan and don't refill when it is true. When a plan emergency is tapped, show a **10-second countdown page** "Calling your helpers in 10 … [Cancel – I'm OK] [Call now]" before `Alerts.emergency` | Asthma person with no "breathing" risk: "Hard to breathe" gives the normal questions |
| B22 | Temperature entry accepts °C: values 34–43 are converted to °F (one decimal) in the Tell number pad and the Readings sheet; the label says "°F or °C" | 38.5 → saved as 101.3 °F |
| B49 | Fever and chills: make the temperature question core (priority ≥ 85 in the catalogue, or add it in `Interview.core` for fever/chills) and allow three danger questions for fever | Fever flow asks confusion and temperature before "tell more" |
| B50 | Fall: `couldGetUp` becomes core; add question `q_floor` "How long were you on the floor?" (choices: under 10 min / 10–60 min / more than an hour = 5 / 30 / 90) asked only when couldGetUp = no | Fall + couldn't get up → RED; > 1 h → RED |
| B51 | Add `q_stiffneck` "Is your neck stiff, or does it hurt to bend your head down?" to fever (core) and headache (extended) | Fever 101 + stiff neck → RED |

Also in W2 (small, same files): after a RED from a Bluetooth reading, show the Danger page like a typed reading (B21, second half).

### W3: Limits pages and the setup step (after W2 is merged)

Files: `ui/screens/SettingsScreens.kt` (Helper controls section), `ui/screens/OnboardingScreen.kt` (new LIMITS step
after EMERGENCIES), `ui/screens/FoodReadings.kt` (show `needsLimit` line), new `ui/screens/LimitsScreen.kt`, `ui/Nav.kt`,
`MainActivity.kt` (route only).

- New page **"Personal limits"**, reachable from Helper controls (PIN-protected) and from the `needsLimit` line.
  One card per measure: current lines, "Suggest from readings" (from the last 14 readings of that type: median ± a
  fixed margin; the helper must still tap Save), number fields, a switch "The doctor said these are OK", Save.
  Header text: "Set by a helper. The app uses these instead of the general numbers."
- Setup step **"Personal limits (for the helper)"** after the emergencies step: "If you are the helper, set the limits
  the doctor agreed. You can do this later in Settings → Helper controls." Buttons: *Set limits now* (opens the page
  inside setup), *Later*. Counted in the progress bar. The DONE page shows a "Limits: Set / Not set" tile.
- Every edit writes `setBy`, `setAt` and goes through `Repo.saveCarePlan` (so Phase B can sync it).

### W4: Feedback, shake to report (independent)

Files: new `feedback/` package (`Shake.kt`, `Capture.kt`, `FeedbackScreen.kt`, `FeedbackSender.kt`, `FeedbackStore.kt`),
`MainActivity.kt` (a few lines to start/stop the shake detector and open the page), `ui/Nav.kt` (routes `Feedback`,
`MyReports`), `ui/screens/SettingsScreens.kt` (two rows: "Report a problem", "My reports"; and the setting),
`ui/screens/HelpScreens.kt` (HelperSettings: the same two rows), `data/Settings.kt` (`shakeToReport`),
`app/build.gradle.kts` (two `buildConfigField`s), `.github/workflows/release.yml` (pass the secret), tests.

- **Shake**: accelerometer while MainActivity is resumed; two peaks > 2.7 g within 1 s, 3-s cooldown. Setting
  "Shake to report a problem": default **on for a helper's phone, off for the person's** (tremor would trigger it).
  "Report a problem" rows always work, with or without shaking.
- **Capture**: screenshot of the current window (`window.decorView` → bitmap) *before* opening the page, plus the
  current route name, app version, role, device model, Android version and time.
- **Page**: the screenshot full width; draw on it with a red pen (undo, clear); category chips *Bug*, *Wrong
  information*, *Hard to use*, *Idea*; a text box with 🎤 speech typing; *Send*. A clear line: "The picture can show
  health details. It goes only to the MedLog team's private tracker."
- **Send** to GitHub: upload the JPEG with `PUT /repos/{repo}/contents/feedback/{id}.jpg`, then `POST /repos/{repo}/issues`
  (title "[Feedback] <category>: <first 60 chars>", body with the note, the image link, and a details table, label
  `feedback` + the category). Before the first upload `GET /repos/{repo}` and **refuse to send screenshots if the repo is
  not private** (send text only, and say so). Repo and token come from `BuildConfig.FEEDBACK_REPO` (default
  `Ashpray94/Med-Log-feedback`) and `BuildConfig.FEEDBACK_TOKEN` (from the gradle property `medlog.feedbackToken` or
  the environment variable `MEDLOG_FEEDBACK_TOKEN`; **never committed**). No token → reports stay queued on the phone
  and the page says "Saved on this phone. It will be sent when the app is set up to send reports."
- **Queue**: every report is saved first in `filesDir/feedback/{id}/` (shot.jpg, meta.json); a WorkManager job with a
  network constraint sends pending ones and records the issue number.
- **My reports**: every report with its status from GitHub: *Waiting to send*, *Sent · #12 Open*, *Fixed · #12 closed*.
  On a fixed one: *It works now* (comment "Verified on the phone" + label `verified`) or *Still broken* (reopen +
  comment with an optional new note and screenshot). This is how completion is verified.

---

## Phase B: everything shared both ways (after Phase A is merged and checked)

Goal from the owner: the person's phone and every paired helper phone hold the same data, and any of them can
create, change and delete. Nothing may be missing on any device.

Design (lead to refine before workers start):
- Every shared row (profile/plan with limits, helpers without keys, notes, medicines, doses, appointments, doc_lines)
  gets a stable `uid`, `updatedAt`, `updatedBy` (device id) and a `deleted` tombstone. Last write wins per row, ties
  broken by device id. Database version 4 with a migration that fills `uid` for existing rows.
- Changes go out as sealed operations on the **family mailbox** that already exists on the relay (every family member
  listens), and over Bluetooth when near. The relay keeps notes only 12 hours, so on every reconnect a device asks
  "send me everything since <my last version from you>", and any device answers from its own copy.
- A helper phone keeps one replica database per person it helps and can open the person's full app ("Open Amma's
  MedLog") with the same pages, reading and writing that replica. Alarms, SOS and fall detection still run only on the
  person's own phone.
- The Privacy page and the privacy policy change: health notes will pass through the relay, sealed with the family key.

Acceptance: a two-device test harness (two in-memory databases and a fake relay) shows create/update/delete from either
side arriving on the other, including after being offline for more than 12 hours, and conflicts resolving the same way
on both.

---

## Persona tests (one tester agent, after Phase A; again after Phase B)

The tester writes **scenario tests** that run in `testDebugUnitTest` (rules, questions, counts, limits, feedback queue
logic), and a **walk-through report** for what can't run without a phone (page by page, from the code: what the persona
sees, how many taps, what could confuse them). Emulators can't run in this environment, so there are no on-screen tests.

**Persona 1: Kamala, 67, oesophageal cancer, on chemotherapy.** She vomits often, shivers after vomiting, has a severe
cough, and is mildly confused about what to tap. Scenarios:
1. 3 vomits in a day → GREEN; 5 → AMBER "more than 4" (cancer care); a note with count 3 is not double-counted (B06).
2. Vomiting with blood → RED; can't keep water down + no urine, age 67 → RED.
3. Chills after vomiting, temperature 100.2 °F → RED (cancer default 100.0); the helper sets the fever limit to 102 →
   100.2 is GREEN and 102.4 is RED. 38.9 °C typed → 102.0 °F.
4. Severe cough 8/10, 1 week → not RED; cough with blood → RED; cough 3 weeks → AMBER.
5. Mild confusion answered yes during fever → RED "Confusion" (and the helpers are told once, not twice: B09 regression check).
6. "Hard to breathe" is **not** an automatic emergency unless the breathing risk was chosen (B02).
7. SpO₂ 88 at 67 with no limit → AMBER plus the "no limit set" line; with the helper's red line at 88 → RED.
8. BP 168/96 at 67 with no limit → no alarm plus the "no limit" line; with the helper's lines 160/100 AMBER, 180/110 RED → AMBER.
9. Walk-through: from Home to a saved vomiting note, count taps, list every word or button that could confuse her.

**Persona 2: Ravi, 34, helper, new to the app, testing with real data.**
1. Shake on a helper's phone opens the report page; on the person's phone it does not (default off).
2. A report with no token is queued, survives a restart (store round trip), and is sent once a token exists (fake HTTP).
3. A public feedback repo gets text only, never the screenshot.
4. Closed issue → "Fixed"; "Still broken" reopens it with the new note.
5. Setup's limits step can be skipped and found again in Helper controls; limits he saves are used by the rules at once.
6. Walk-through: every page he opens in his first 10 minutes, and what he wouldn't understand.

The tester reports pass/fail per scenario with the test name, and a list of new problems it found, ranked.
