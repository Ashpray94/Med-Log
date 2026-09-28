# MedLog 2.9.0: architecture, page-by-page information map, and worst-case audit

This file is written from the source code on branch `claude/busy-lamport-3qr5wl` (commit `fe7b720`, "MedLog 2.9.0").
Every statement below was read from the code. Nothing is guessed. Where something can only be proven on a real
phone (Android background rules), it is marked **⚙ needs a device check** and kept apart from the proven bugs.

Every bug has an ID (**B01 … B48**), a severity, the file and line, a worst-case example, and a suggested fix.
The master list is in **Part 5**. Page sections link to the IDs.

---

## Contents

- [Part 0. How to read this file](#part-0-how-to-read-this-file)
- [Part 1. Architecture: what connects to what, and what data moves](#part-1-architecture-what-connects-to-what-and-what-data-moves)
- [Part 2. Every calculation, with its formula and an example](#part-2-every-calculation-with-its-formula-and-an-example)
- [Part 3. Navigation map and the parts shared by every page](#part-3-navigation-map-and-the-parts-shared-by-every-page)
- [Part 4. Page by page: what shows, where it comes from, how it behaves, and how it breaks](#part-4-page-by-page)
- [Part 5. Master bug list (ranked)](#part-5-master-bug-list-ranked)
- [Part 6. Dead settings, unused code, and things to check on a phone](#part-6-dead-settings-unused-code-and-things-to-check-on-a-phone)
- [Part 7. Space for your corrections and new features](#part-7-space-for-your-corrections-and-new-features)

---

## Part 0. How to read this file

### Severity

| Mark | Meaning |
|---|---|
| 🔴 Critical | Crash, a wrong emergency action, or a safety/security hole |
| 🟠 High | Wrong medical information, wrong alerts to family, or lost data |
| 🟡 Medium | A wrong number or word on screen, a confusing behaviour, a missing guard |
| 🔵 Low | Cosmetic or rare |
| ⚪ Dead | A setting or code that does nothing |

### Icon legend (the app's Material icons, shown here as emoji)

| Emoji | App icon | Where it appears |
|---|---|---|
| 🏠 | Home | Bottom bar |
| 🆘 | SOS | Bottom bar (red square), widget, Emergency page |
| 👪 | Groups | Bottom bar "Family", Alert-my-family card |
| 🔊 / 🔇 | VolumeUp / VolumeOff | "Read" toggle, top right of every page |
| ⬅️ | ArrowBack | Back button (top left) |
| ✖️ | Close | Close on task pages and sheets, remove chips |
| ➕ | Add | "Add" actions, dashed "Add …" cards |
| ➡️ | ArrowForward | The big "How are you feeling?" card, "Choose your messages" |
| › | KeyboardArrowRight | Chevron at the end of tappable rows |
| ⌄ | KeyboardArrowDown / ExpandMore | "More below" button, dropdown fields |
| 💊 | Medication | Medicines tile, feeds "By tube", permission rows |
| 🍽️ | Restaurant | Food & water tile, meal pictures |
| 🩺 | MonitorHeart | "BP & sugar" tile, blood pressure, readings in History |
| 🏥 | LocalHospital | "Doctor page" tile |
| 📈 | Insights | "My health" tile |
| ⚙️ | Settings | Settings tile, helper-phone settings |
| 🕘 | History | History card on Home |
| ⚠️ | Warning | "Reminders are off" card, "Couldn't send" |
| ✅ | Check / CheckCircle | Taken, chosen, "Yes" buttons |
| 📞 | Call | Call buttons, ambulance card |
| 💬 | Sms / ChatBubble | Text messages, "Ask how they are", custom help messages |
| 🎤 | Mic | "Speak" in every search box |
| 🔍 | Search | Search boxes, "Find a note" on History |
| ✏️ | Edit | "Change an answer", "Change the problem", "Change meal" |
| 🗑️ | Delete | "Remove this note", "Delete meal", "Stop this feed" |
| 📷 | CameraAlt | "Take a photo of the strip", "Take a photo" (reports) |
| 🩸 | Bloodtype | Sugar |
| ⚖️ | MonitorWeight | Weight |
| ❤️ | Favorite | Pulse |
| 🌬️ | Air | Oxygen |
| 🌡️ | Thermostat | Temperature |
| 🥛 | LocalDrink | Water, feeds "By mouth" |
| 🤒 | Sick | "Problems noted", "I don't feel well" |
| 🌅 ☀️ 🌇 🌙 | Sunrise / WbSunny / Sunset / NightsStay | Part of day on every dose and time card |
| 🖨️ 📤 | Print / Share | Doctor page bottom bar |
| 📝 | StickyNote2 | Visits, "other" notes in History |
| 📅 | CalendarMonth | Appointments, "Some days of the week" |
| 🔵 | Bluetooth | Pairing, Bluetooth machines |
| 💤 | Snooze | "In 10 min" on the medicine alarm |
| ▶️ | PlayArrow | "Play my voice" |
| ♻️ | Restore | "Removed notes", "Bring back" |
| 📇 | Contacts | "Choose from contacts" |
| 👤➕ | PersonAdd | Add a helper / doctor |
| 🚶 | DirectionsWalk | "Please come", "Help me walk", "I'm coming" |
| 🚻 | Wc | "Help with bathroom" |
| 📨 | Send | "Send" a custom message |
| 🕒 | Schedule | "Sending…", "Any time" (food) |
| 🔁 | Repeat | "Every day" |
| 📆 | Today | "Only when needed" |
| 🚫🍽️ | NoFood | "Before food" |
| 🍜 | RamenDining | "With food" |
| 😊 😐 😟 | (text emoji in the app) | Check-in and "How are you?" answers |
| ■ ▲ ● | (text shapes in the app) | Triage marks: Red "Urgent", Amber "Watch", Green "OK" |

### Words used in this file

- **Note**: one row in the `notes` table. Symptoms, water, food, readings, SOS, check-ins, visits, questions and messages are all notes, told apart by `kind`.
- **Dose**: one planned medicine time (`doses` table), e.g. "Metformin at 8:00 AM on 28 Sept".
- **Triage**: the traffic light the app gives a symptom note: `GREEN` (OK), `AMBER` (call doctor today), `RED` (get help now).
- **Helper**: a family member in the `helpers` table. **Paired helper**: one whose phone was linked by Bluetooth pairing (has `pairId` and `pairKey`).
- **Person's phone** (role `self`) vs **helper's phone** (role `helper`).

---

## Part 1. Architecture: what connects to what, and what data moves

### 1.1 The whole system on one page

```
 ┌──────────────────────────────── THE PERSON'S PHONE (role = "self") ────────────────────────────────┐
 │                                                                                                   │
 │  ENTRY POINTS                         UI (Jetpack Compose)                    BACKGROUND           │
 │  ────────────                         ───────────────────                    ──────────           │
 │  App icon ─────────────┐                                                                          │
 │  Home-screen widget ───┤   medlog://… deep links                                                  │
 │  Quick-settings tile ──┼──────────────▶ MainActivity ──▶ Nav (back stack) ──▶ 35 screens          │
 │  App shortcuts ────────┤                    │                                   │                 │
 │  Notifications ────────┤                    │ reads Settings (StateFlow)        │ calls            │
 │  Web links (BROWSABLE)─┘ ⚠ B03              ▼                                   ▼                 │
 │                                   ┌──────────────────┐   ┌───────────────────────────────────┐    │
 │  DoseActivity (medicine alarm) ◀──┤  SettingsStore   │   │  Repo  (all note writes)          │    │
 │  AlertActivity (SOS / fall /      │  SharedPrefs     │   │  Scheduler (doses, alarms)        │    │
 │     check-in / "How are you?")    │  "medlog_settings"│  │  Care (check-in, visits, refills) │    │
 │                                   │  NOT encrypted    │  │  Alerts (SMS + nearby + relay)    │    │
 │                                   │  NOT in Backup    │  │  Sos / SosService                 │    │
 │                                   └──────────────────┘   │  Nearby (Bluetooth / Wi-Fi Direct) │   │
 │                                                          │  Relay (internet mailbox)          │   │
 │                                                          │  DangerRules (triage)              │   │
 │                                                          │  Nutrition, DoctorNoteBuilder, Pdf │   │
 │                                                          └───────────────┬───────────────────┘    │
 │                                                                          │ Room DAOs              │
 │                                                                          ▼                        │
 │                          ┌────────────────────────────────────────────────────────────────┐       │
 │                          │  medlog.db  (Room + SQLCipher, key sealed by Android Keystore)  │      │
 │                          │  profile · helpers · notes · medicines · doses · appointments  │       │
 │                          │  doc_lines · inbox                                              │      │
 │                          └────────────────────────────────────────────────────────────────┘       │
 │                                                                                                   │
 │  AlarmManager ── one exact alarm ──▶ AlarmReceiver ──▶ Scheduler.tick() ──▶ Care.tick()           │
 │  BootReceiver (restart / time change) ──▶ reschedule + FallService + Nearby + QuickNotification   │
 │  FallService (accelerometer, optional) ──▶ AlertActivity "Did you fall?"                          │
 │  NearbyService (foreground) ──▶ Relay.listen (replies and "How are you?" from helpers)            │
 └───────────────────────────────────────────────────────────────────────────────────────────────────┘
        │ SMS / phone calls          │ Bluetooth + Wi-Fi Direct      │ HTTPS               │ HTTPS
        │ (mobile network)           │ (Google Nearby Connections)   │ (ntfy.sh relay)     │ (GitHub)
        ▼                            ▼                               ▼                     ▼
 ┌──────────────────┐   ┌──────────────────────────────────┐  ┌────────────────┐  ┌──────────────────┐
 │ Helpers' phones  │   │  HELPER'S PHONE (role "helper")   │  │ ntfy relay     │  │ Update check     │
 │ (any phone):     │   │  NearbyService advertises          │◀▶│ sees only      │  │ latest.json once │
 │ SMS + calls      │   │  "H|pairId", Relay listens DOWN    │  │ sealed bytes   │  │ a day (sideload) │
 └──────────────────┘   │  inbox table, AlertSound, alarm UI │  └────────────────┘  └──────────────────┘
                        └──────────────────────────────────┘
 Other connections:  Google Calendar (CalendarContract, optional) · Meeting Timer app (BridgeReceiver,
 signature permission) · Bluetooth health machines (BLE GATT) · phone's speech typing (RecognizerIntent)
 · phone's text-to-speech · ML Kit text reading (import) · Android print/share.
```

### 1.2 Where every piece of data lives

| Store | Encrypted | In Backup file | In Setup file | What is in it |
|---|---|---|---|---|
| `medlog.db` table `profile` (1 row) | ✅ | ✅ | Partly (not `plan`, see B15) | name, dob, sex, blood group, hospital id, conditions, allergies, doctor name/phone, blood thinner, notes, **plan** (JSON: doctors, symptoms, treatments, risks, emergencies) |
| `helpers` | ✅ | ✅ | ✅ (without pairing keys) | name, phone, relation, sos, alerts, pairId, pairKey, canSeeNotes, sortOrder |
| `notes` | ✅ | ✅ | ❌ | every symptom, water, food, reading, SOS, check-in, visit, question, message |
| `medicines` | ✅ | ✅ | ❌ | name, strength, form, amount, food, times, days, dates, critical, asNeeded, gap, purpose, photo path, pillsLeft, active, bloodThinner, change note, calendar id, shape, colour |
| `doses` | ✅ | ✅ | ❌ | medicineId, scheduledAt, status, actedAt, reason, snoozeUntil, reminded, helperAlerted, shownBy |
| `appointments` | ✅ | ✅ | ❌ | at, doctor, place, purpose, calendarEventId, done |
| `doc_lines` | ✅ | ✅ | ❌ | every text line from imported reports (for search) |
| `inbox` | ✅ | ✅ | ❌ | alerts received on a helper's phone, family chat |
| SharedPrefs `medlog_settings` (typed keys) | ❌ | ❌ | ✅ (except onboarded, role, pairedWith, calendarId) | all `Settings` fields (look, sound, SOS, reminders, languages, messages …) |
| SharedPrefs `x_…` keys | ❌ | ❌ | ❌ | `people` (who a helper phone helps + keys), `followups`, `feed_info` (feed kcal/protein), `custom_foods`, `kcal_target`, `protein_target`, `fired_<time>` (Care), `checkin_day`, `checkin_shown`, `asked_better_<id>`, `widget_noted`, `relay_since`, `own_family_key`, `family_sent_<pairId>`, `my_name`, `device_id`, `onboard_step`, `update_checked` |
| Files `filesDir/photos` | ❌ (app-private) | ❌ | ❌ | medicine strip photos, report photos |
| Files `filesDir/audio` | ❌ (app-private) | ❌ | ❌ | voice messages received (deleted after `keepAudioDays`) |
| SharedPrefs `medlog_keys` | sealed by Keystore | – | – | the database key, encrypted |

Consequences you will see later: feed calories (B16), custom foods (B16), nutrition targets (B16) and the pairing
list on a helper phone (B16, B36) are **not** in the Backup, **not** encrypted, and **not** cleared by "Delete everything".

### 1.3 Who reads and writes each table (CRUD map)

`C` create · `R` read · `U` update · `D` delete (soft delete for notes = set `deletedAt`)

| Screen / component | profile | helpers | notes | medicines | doses | appointments | doc_lines | inbox |
|---|---|---|---|---|---|---|---|---|
| Setup (Onboarding) | C R U | C R U D | – | R | – | – | – | – |
| Home | R | – | R (symptoms) C (better) | R | R U (take/untake) | – | – | – |
| Tell how you feel | R | R | C U R | R | – | – | – | – |
| Medicines, Add/Change medicine | – | – | C (as-needed taken) R | C R U | R U D (future DUE) | – | – | – |
| Medicine alarm (DoseActivity) | – | – | – | R | R U | – | – | – |
| Food & water, Food pick, New feed | – | – | C U D (water, food) R | C U (feeds) R | R U | – | – | – |
| BP & sugar | R | R (alerts) | C D R | R (blood thinner) | – | – | – | – |
| My health | – | – | R | – | R | – | – | – |
| Nutrition | – | – | R | R | R | – | – | – |
| Doctor page, PDF | R | – | R C (questions) | R | R | – | – | – |
| After the visit | R | – | C (visit, question) | – | – | C U | – | – |
| Appointments | – | – | – | – | – | C R D | – | – |
| History, Problem history, Note, Search, Removed | – | – | R U (remove/restore) | – | R | – | R | – |
| Family (Help), Messages, Helpers, Helper edit, Pair | R | C R U D | C (sent / replies) | – | – | – | – | – |
| Emergency, SOS | R | R | C (SOS) R | – | – | – | – | – |
| Helper home (helper phone) | – | – | – | – | – | – | – | C R U |
| Settings, Backup, Privacy | R U | R | R | R | – | – | – | – |
| Import | – | – | C (IMPORTED) | – | – | – | C | – |
| Bluetooth machines | R | R | C (reading) | R | – | – | – | – |
| Scheduler (background) | R | – | C (missed twice) | R | C R U D | – | – | – |
| Care (background) | R | – | R | – | – | R | – | – |
| Widget | R | – | C (symptom) R | R | R U | – | – | – |
| MedLogApp start | – | – | D (purge removed > 30 days) | R | C | – | – | – |

### 1.4 The main data flows, step by step

#### A. Telling how you feel (Home ➡️ Tell)

```
 Person taps a problem ─▶ begin(pid)
    │  1. Repo.saveTold()  ── INSERT notes(kind=SYMPTOM, problemId, occurredAt=now, triage=GREEN, groupId=id)
    │                          + readings + medicines told in the same sentence (only from Assistant/links)
    │  2. queue = Interview.core(problem)   (WHEN, 0–2 danger questions, COUNT?, WHERE?, burn?, SCALE?)
    │  3. persist(): updateFacts ─▶ DangerRules.evaluate(problem, facts, readings, recent 3 days, person)
    │                              ─▶ updateTriage
    │                              ─▶ if RED: Alerts.dangerToHelpers (SMS to helpers + nearby) and DANGER page
    │  4. if problem ∈ plan.emergencies: Sos.start(countdown=false) and DANGER page   ⚠ B02 B08
    ▼
 Each answer ─▶ facts[field] = value ─▶ persist() again ─▶ next question (0.45 s later)
    ▼
 After core questions: "Can you tell me a little more?" (Yes ─▶ up to 9 extended questions; No ─▶ follow-up in 30 min)
    ▼
 SUMMARY page ─▶ Save ─▶ persist() ─▶ FollowUp.schedule / remove ─▶ reschedule alarm ─▶ "Saved. Get well soon." ─▶ Home
                                      with Undo for 3 s (removes the main note only, B37)
```

What the note stores (example): `details = {"started":{"v":"Earlier today","s":"ASKED"},"severity":{"v":6,"s":"ASKED"},"site":{"v":"Left knee","s":"ASKED"},"pin":{"v":"front:38,120","s":"ASKED"}}`, `severity=6`, `text` = one plain line built by `Describe.line()` from the problem name and the facts, `triage="GREEN"`.

#### B. The life of one medicine dose (state machine)

```
                      Scheduler.reschedule()  (creates rows for now-3h … now+2 days)
                                   │
                                   ▼
   ┌──────────── DUE ───────────────────────────────────────────────────────────┐
   │  at scheduledAt (+2 min if Meeting Timer is on and hasn't shown it)        │
   │     ─▶ reminded=1, DoseAlert: full-screen card + alarm sound + notification│
   │  +snooze min ─▶ reminded=2 (louder)   +2×snooze ─▶ reminded=3 (louder)     │
   │  +30 min (15 if "Important") ─▶ helperAlerted=true, SMS "hasn't marked…"   │
   │  +3 h ─▶ MISSED  (+ "Missed X twice in a row" note if Important)           │
   └──────┬───────────────────┬──────────────────────┬─────────────────────────┘
          │ "I took it"       │ "In 10 min"          │ "Skip" + reason
          ▼                   ▼                      ▼
       TAKEN              SNOOZED                  SKIPPED
   actedAt=now,        snoozeUntil=now+10,      actedAt, reason
   pillsLeft −= amount  reminded=0 → rings       ("Feeling sick", "Ran out",
   (B10)                again at snoozeUntil      "Doctor said stop", "Other")
          │ "Undo"
          ▼
        DUE (actedAt cleared, pills added back)
```

⚠ A dose of a medicine that was **stopped** stays `DUE` if its time had already passed; the background tick still
alerts helpers and marks it missed (B05).

#### C. SOS (bottom-bar 🆘 ➡️ Emergency ➡️ hold "Alert my family", or an emergency problem, or a fall)

```
 Sos.start(reason, countdown)
   ├─ countdown (5/10/15 s, spoken) ── "Cancel – I'm OK" stops here (not logged)
   ├─ location: GPS/network, up to 12 s, else last known
   ├─ SMS to every helper with sos=true: "MedLog: <name> pressed the SOS button … Where they are: maps link"
   ├─ Nearby.broadcast("SOS") ─▶ paired helper phones ring (Bluetooth nearby + internet relay)
   ├─ optional WhatsApp group call (Accessibility service presses Call)
   ├─ call helper 1 on speaker ─▶ wait for the call to end ─▶ 15 s "Did X answer?" ─▶ helper 2 …
   ├─ "Calling 108 in 20" ─▶ direct call to the emergency number
   └─ saveLog ─▶ notes(kind=SOS, details={log:[…], where:"…"})   ⚠ B17 loses reason/location on "Help is coming"
```

#### D. A help message ("I need water") from Family, the widget or a link

```
 HelpMessages.send(text)
   ├─ notes(kind=MESSAGE, "Sent: I need water")
   ├─ if any paired helper: Nearby.broadcast("MESSAGE") and wait up to 15 s for "got it" receipts
   ├─ SMS "MedLog: Amma says "I need water"" to every helper (alerts or sos) whose phone did not say "got it"
   ├─ status: sending ─▶ sent ─▶ answered (first reply) or noanswer (after 3 min) or failed (nothing went out)
   └─ helper replies ("I'm coming", "In 5 min", "I'll call", "Can't now") come back through the relay / Bluetooth,
      are spoken aloud, and are saved as notes "Ravi replied: I'm coming"
```

#### E. Pairing a helper's phone (face to face)

```
 Helper phone: "Start pairing" ─▶ advertise "P|<helper name>"
 Person phone: "Look for my helper's phone" ─▶ discovers ─▶ taps the name ─▶ "Which of your helpers is Ravi?"
   ─▶ requestConnection ─▶ both sides acceptConnection IMMEDIATELY (B04) ─▶ 4 digits shown
   ─▶ person phone sends {pairId, key(32 bytes), name, relay URL, family key} in the clear inside the Nearby link
   ─▶ helper phone stores it in prefs `people`; person phone stores pairId/pairKey on the helper row
 "They match" button ─▶ Nearby.confirmDigits() ─▶ does nothing (the keys were already exchanged)
```

#### F. Morning check-in

```
 Care.tick at check-in time (08:00/09:00/10:00/11:00, or 1 pm / 6 pm from setup)
   ├─ AlertActivity CHECKIN: 😊 Good · 😐 OK · 😟 Not well · Tell how I feel   (⚙ may not open from background)
   ├─ notification "How are you today? Tap to answer" ─▶ opens medlog://checkin ─▶ Home (no check-in buttons, B12)
   └─ +2 h and not answered ─▶ SMS to helpers: "…hasn't answered the morning check-in yet."
```

### 1.5 Settings that change behaviour elsewhere (cross-links)

| Setting | Where it takes effect |
|---|---|
| `bigMode` (Large words) | Whole app zooms 1.2× (MedTheme); grids switch from 3 to 2 columns (Home tiles, problem grid) |
| `highContrast` | Palette switches to black on white |
| `boldText` | Base font weight Medium |
| `readAloud`, `autoRead` | "Read" toggle visible; every page spoken 0.35 s after opening |
| `speechRate` | Text-to-speech speed |
| `steadyTouch` | Taps closer than 0.6 s ignored (0.25 s normally), **across all buttons** |
| `touchToHear` | First tap speaks the label, second tap (within 4 s) acts |
| `leftHand` | Bottom bar order reversed (Family · SOS · Home) |
| `lessMotion` | Animations off |
| `hidden` | Hides Home tiles/sections: meds, food, readings, doctor, reports (and "help", which nothing checks ⚪) |
| `emergencyNumber` | Ambulance card, SOS last step, Danger page |
| `sosCountdown` | SOS countdown seconds |
| `snoozeMinutes`, `escalateMinutes`, `escalateCriticalMinutes` | Dose reminder repeats and helper alerts |
| `checkInEnabled`, `checkInTime` | Care check-in and the "no answer" SMS |
| `waterGoal` | Water glass on Food & water, doctor PDF water line |
| `messages` | Family page tiles, first 3 on the widget |
| `languages` | Every text is translated through I18n (only Tamil and Hindi dictionaries ship: `assets/i18n/ta.json`, `hi.json`) |
| `internetLink`, `relayUrl` | Whether help messages use the internet relay |
| `useMeetingTimer` | First reminder waits 2 min for Meeting Timer |
| `calendarId`, `calendarNeutralTitles` | Medicine events in Google Calendar |
| `fallDetection` | Starts/stops FallService |
| `persistentNotification` | "Always-there buttons" notification |
| `weeklySummary` | Sunday 6 pm "Your week" notification (B29) |
| `helperPin` | Locks the Settings page only (B32) |

---

## Part 2. Every calculation, with its formula and an example

Each formula is copied from the code and paraphrased. "Example" is a worked case.

### 2.1 Home

| Number on screen | Formula | Example |
|---|---|---|
| Greeting | hour < 12 → "Good morning"; < 17 → "Good afternoon"; else "Good evening" | 02:00 → "Good morning" (B44) |
| Title | first word of `profile.name`, else the greeting | "Kamala Devi" → **Kamala** |
| Subtitle | today as `EEEE, d MMMM` | "Monday, 28 September" |
| "Today's medicines" caption | `taken = doses today with status TAKEN`; "Nothing to take today" / "All N taken" / "X of N taken". Doses from **all** medicines, active or not, including feeds | 3 doses, 1 taken → "1 of 3 taken" |
| Dose card state | `taken` = TAKEN; `missed` = MISSED or SKIPPED; `dueNow` = not taken, not missed, and `scheduledAt ≤ now + 10 min` | 8:00 dose at 7:52 → amber "Due now · 8:00 AM" |
| "Recent" tiles | `Repo.recentProblems(3)` (see 2.2) | Headache, Dizzy, Cough |
| "N today" under a tile | sum of `count ?: 1` for today's notes of that problem (includes "better" notes, B19) | 2 headache notes → "2 today" |
| "Is your … better now?" card | first recent problem that is `ongoing`, last noted > 20 h ago, not asked today | Headache last at 9 am yesterday → asked today |
| History card line | "Last: <label>, <d MMMM>" of the most recent of the 3 recent problems, else "Everything you have noted" | "Last: Headache, 28 September" |

### 2.2 Recent problems (Home tiles, "ongoing", widget "(2nd today)")

```
for each problem noted in the last 30 days:
  score   = Σ exp(−age / 3 days)                  (a note today ≈ 1.0, 3 days ago ≈ 0.37)
  ongoing = last note < 2 days ago  AND  no "better" note at or after the last note
  score  += 100 if ongoing, +50 if in watch list
sort by score, take 3 (Home) or 12 (widget count)
```
Example: Headache noted today (1.0) and yesterday (0.72) → 1.72 + 100 = 101.7 → first.

### 2.3 Problem suggestions (Tell ➡️ "How are you feeling?", setup symptoms, widget tiles)

```
yours     = problems the person logged in 180 days, ranked by Σ exp(−age/20 days), top 6
suggested = score from:  setup symptoms +5 each · conditions' typical problems +3 · age list +1.5
            (≥55 years or unknown → older list; else adult list) · time of day +0.6
            each list: weight − 0.05 × position;  removes anything already in "yours";  top 6
```
Example: 72 years, Diabetes, nothing logged yet, 7 am → "Common for you": **Tired** (3.0−0.3 from Diabetes + 1.5−0.1 from the older list = 4.1), High sugar (3.0), Low sugar (2.95), Very thirsty (2.9), Passing urine often (2.85), Tingling (2.8).

### 2.4 Triage (DangerRules, `clinical/Triage.kt`, version `rules-0.1.0-unreviewed`)

The first matching group decides nothing alone; all reasons are collected, then **any RED → RED**, else **any AMBER → AMBER**, else GREEN.

| Rule | Level | Example that triggers it |
|---|---|---|
| Problem is self_harm or fact selfHarm | RED (calm mental-health page, helpline 14416) | "Thoughts of self-harm" |
| Problem is fainted, fits, face droop, speech trouble, one side weak, vision loss, vomit blood, black stool, cough blood, choking, broken bone, confusion | RED | Tap "Fainted" |
| Stroke signs (face, arm, speech) = yes | RED | "Is one side of the face drooping?" → Yes |
| Chest pain / tightness: arm/jaw, sweating or breathless = yes → RED; severity ≥ 7 → RED; else AMBER; heart condition and not RED → RED | RED/AMBER | Chest pain 4/10 → AMBER "must be checked today" |
| Breathless + at rest | RED | |
| Lips/face swelling; allergy + breathless; choking | RED | |
| Blood in vomit, coffee-ground vomit, black stool; bleeding not stopping | RED | |
| Worst-ever headache; headache + vision change; stiff neck + fever ≥ 100.4; fits; lost consciousness | RED | |
| Fall/head injury: hit head on blood thinner; head injury on blood thinner; couldn't get up; ≥ 60 min on floor → RED; else AMBER | RED/AMBER | Fall, got up, no head → AMBER |
| Temperature ≥ 104 or ≤ 95 → RED; ≥ 100.4 + confusion → RED; ≥ 100.4 and age ≥ 65 → AMBER; ≥ 102 → AMBER | RED/AMBER | 101 °F at 70 years → AMBER |
| Sugar < 54 → RED + first aid; < 70 with sweating/confusion → RED; < 70 → AMBER; ≥ 400 → RED; > 300 → AMBER | | Sugar 62 → AMBER, "Take 3 teaspoons of sugar…" |
| SpO₂ < 90 → RED; < 94 → AMBER | | 92 % → AMBER |
| BP ≥ 180 or dia ≥ 120 with symptoms → RED; ≥ 180 or dia ≥ 110 → AMBER; sys < 90 → AMBER | | 185/100 → AMBER |
| Pulse ≥ 130 or < 40 | AMBER | |
| Vomiting / loose motions: last 24 h total ≥ 6 → AMBER (**double counts**, B06); can't keep water down; no urine 8 h; blood in stool; no water + no urine + ≥ 65 → RED | | |
| No urine / cannot pass | AMBER | |
| Fever: first fever note in last 3 days is ≥ 2 days old | AMBER | |
| One swollen leg, cough ≥ 3 weeks, blood in urine/stool, bleeding after menopause, lump, jaundice, swallowing (can't swallow water → RED), weight loss, rash after new medicine, wound + fever/redness, hallucinations, heavy bleeding, near fall, animal bite, burns (deep → RED; > palm and ≥ 65 → RED; > palm → AMBER; blisters → AMBER; face/hands/feet/neck → AMBER), bleeding/cut on a blood thinner | mostly AMBER | |
| Dizzy: (dizzy notes in 2 days) + 1 ≥ 3 → AMBER (**off by one**, B07) | AMBER | 2 real dizzy notes → "Dizzy 3 times in 2 days" |

Age comes from `profile.dob`; "blood thinner" from profile or any active medicine marked blood thinner.

### 2.5 Medicines

| Value | Formula | Example |
|---|---|---|
| Dose times | for each day from (from − 1 day) to `to`: skip before start, after end, or not in chosen weekdays; one time per `times` slot | "08:00,20:00" every day → 2 doses a day |
| Pills left after "taken" | `pillsLeft − amount` where amount "½" → 0.5, "1½" → **10.5** (B10), "5 ml" → not a number → 1 | 30 tablets, amount "1½" → **19.5** after one dose |
| Days left (refill) | `floor(pillsLeft / (amount × times per day))`; warning at exactly 5, 2, 0 days; helpers told at ≤ 2 | 10 tablets, 1 twice a day → 5 days → "About 5 days of X left" |
| As-needed double-dose guard | any "Took <same name>" note within `minGapHours` → "Already taken" page | Paracetamol at 2 pm, again at 4 pm (gap 4 h) → asked |
| Critical "missed twice" | last 2 doses both MISSED → note "Missed X twice in a row" (hidden in History, B30) | |

### 2.6 Food, water, feeds, nutrition

| Value | Formula | Example |
|---|---|---|
| Water today | Σ `count` of WATER notes since local midnight | 5 taps → "5 of 8" |
| Portion kcal | pieces: `kcal × pieces × size` (small 0.7, medium 1, large 1.4); grams/ml: `kcal × amount / serving grams` | 2 medium idli = 2 × 58 = **116 kcal** |
| First tap amount | piece → 1; grams/ml → serving if ≤ 30, else min(serving, 100 g or 150 ml) | Rice (150 g serving) → first tap 100 g = 130 kcal |
| Step | piece 1; ml 5 or 50; grams 5 or 25 | |
| Today's kcal on Food tab | Σ meal `kcal` | |
| Nutrition average | Σ kcal of logged days ÷ number of logged days (today excluded unless it is the only one) | |
| Targets | doctor's numbers if set, else latest weight × 30 kcal and × 1 g protein | 60 kg → 1800 kcal, 60 g protein |
| Findings | kcal % < 60 RED, < 85 AMBER, else GREEN; protein % < 60 RED, < 80 AMBER; weight change ≤ −5 % RED, ≤ −2 % AMBER, **anything else "steady" GREEN** (B23); missed feeds ≥ 3 RED; unlogged days AMBER | |
| Feed times | `7 + 14 × i ÷ (n − 1)` o'clock for n feeds a day (1 feed → 8 am) | 4 feeds → 7, 11, 16, 21 |

### 2.7 My health charts (`HealthScreen.kt`)

| Measure | Big number | Chart | Row "latest" |
|---|---|---|---|
| Weight, BP, Sugar, Pulse, Oxygen, Temp | last reading in span; caption "Latest, d MMMM · range lo to hi" | line, real time on x, normal band shaded (BP 90–140, sugar 70–180, pulse 50–100, oxygen 94–100, temp 96–99.5) | last reading ever (365 days) |
| Water | average of the bars that have data ("Average a day") | W/M: glasses per day; 6M: per week = Σ ÷ days logged; Y: per month the same | latest day's glasses |
| Medicines taken | average % | % taken of doses scheduled that day (up to now) | latest day % |
| Problems noted | **Σ of bar values** ("In total") | W/M: notes per day (**number of notes, not times**, B20); 6M/Y: **average per day** in that week/month (B20) | latest day count |

### 2.8 Doctor page and PDF (`DoctorNote.kt`)

| Value | Formula |
|---|---|
| Period | today+1 midnight minus 7/14/30/90 days |
| Symptoms (count card) | number of different problems in the period |
| Need care | problems whose worst note is RED + AMBER |
| Doses taken % | Σ taken ÷ Σ scheduled (up to now) for non-as-needed medicines shown |
| Symptom "Times" | Σ `count ?: 1` (includes "better" notes, B19); "in N days" |
| "Worst pain" | highest severity of any kind (B42) |
| Over time | ≥ 3 days with notes: last day's count > first day's → "Getting worse"; less → "Happening less often"; else if any "better" → "Getting better" |
| Most important (max 3) | urgent reasons first; then top GREEN problems; then medicines missed ≥ 25 % of ≥ 3 doses |
| Reading tile "Outside the usual range" | BP sys ≥ 140 or < 90 or dia ≥ 90; sugar ≥ 200 or < 70; SpO₂ < 94; temp ≥ 100.4; pulse > 100 or < 50 |
| Patterns noticed (max 2) | began ≤ 7 days after a medicine start/change; after meals ≥ 60 %; fluid loss with low water or no urine; within 2 h of a dose ≥ 3 times and ≥ 60 %; breathless lying flat + swollen ankles; weight ± ≥ 2 kg; ≥ 2 doses skipped "Feeling sick" |

---

## Part 3. Navigation map and the parts shared by every page

### 3.1 Navigation map

```
MainActivity start
  ├─ not onboarded ───────────────▶ SETUP (Onboarding, 24 steps) ─▶ Home
  ├─ role = helper ───────────────▶ HELPER HOME
  └─ else ────────────────────────▶ HOME
HOME
  ├─ ➡️ How are you feeling? ─────▶ TELL (pick ▸ questions ▸ summary ▸ danger)
  ├─ Recent tile ─────────────────▶ TELL (straight to questions)
  ├─ Dose card name ──────────────▶ MEDICINES
  ├─ ➕ Add / dashed card ────────▶ ADD MEDICINE (sheet over Home)
  ├─ 🕘 History card ─────────────▶ HISTORY ─▶ PROBLEM HISTORY ─▶ NOTE ─▶ TELL (add more)
  │                                  └─ 🔍 ─▶ FIND A NOTE ─▶ REMOVED NOTES
  ├─ 🍽️ Food & water ─────────────▶ FOOD & WATER ─▶ WHAT DID YOU EAT? / NEW FEED
  ├─ 🩺 BP & sugar ───────────────▶ READINGS ─▶ (Trends) MY HEALTH
  ├─ 🏥 Doctor page ──────────────▶ FOR THE DOCTOR ─▶ NUTRITION / AFTER THE VISIT / APPOINTMENTS
  ├─ 📈 My health ────────────────▶ MY HEALTH ─▶ NUTRITION
  ├─ 💊 Medicines ────────────────▶ MEDICINES ─▶ CHANGE MEDICINE
  └─ ⚙️ Settings ─────────────────▶ SETTINGS ─▶ (sections) · SEEING AND HEARING · PERMISSIONS · BACKUP
                                                · PRIVACY · MY HELPERS · MY MESSAGES · IMPORT · BLUETOOTH MACHINES
BOTTOM BAR (every person-phone page): 🏠 Home · 🆘 SOS ─▶ EMERGENCY · 👪 Family ─▶ FAMILY (Help)
Full-screen activities (over the lock screen): MEDICINE ALARM · SOS PANEL · DID YOU FALL? · CHECK-IN · "X ASKS: HOW ARE YOU?" · HELPER ALERT
Only by deep link: DID I TAKE MY MEDICINES? (medlog://open?name=didtake)
```

Deep links (`medlog://…`, also openable from web pages, B03): `tell`, `tell?problem=&text=&note=`, `meds`, `help`, `help?send=<key>`, `messages`,
`call` (calls the first helper), `emergency`, `sos` (starts SOS), `doctor`, `reports`, `helper`, `history`, `checkin` (→ Home), `feature?feature=…`,
`open?name=` (meds, medadd, didtake, food, readings, family, messages, helpers, helperadd, pair, visit, appointments, reports, settings, easy,
permissions, backup, privacy, search, removed, import, devices, history).

### 3.2 The page frame ("Screen") used by most pages

```
┌──────────────────────────────────────────────┐
│ ⬅️ (or small grey EYEBROW text)      🔊 Read │  ← 56 dp row. Read = switch: green 🔊 when on, white 🔇 when off
│ Title (one line, … if too long)              │  ← sc.title 28 sp (33 sp look in Large)
│ Subtitle (one line)                          │
├──────────────────────────────────────────────┤
│  content, scrolls                            │
│  ...                                         │
│          ┌──────────────┐                    │  ← "More below ⌄" pill when more content is below
│          │ More below ⌄ │                    │
├──────────────────────────────────────────────┤
│ [pinned action area, only on some pages]     │
├──────────────────────────────────────────────┤
│   🏠 Home      [ SOS ]      👪 Family         │  ← bottom bar, person's phone only. Current tab teal.
└──────────────────────────────────────────────┘
      ┌────────────────────────────────┐
      │ Saved.                   Undo  │         ← Undo bar, dark, 3 seconds, above the bottom bar
      └────────────────────────────────┘
```

| Element | Data | Behaviour | Example |
|---|---|---|---|
| ⬅️ Back | – | `nav.back()` or the page's own back | |
| 🔊 Read | `settings.readAloud`, `autoRead` | Hidden if Read aloud is "Never". Tap on → saves `autoRead=true` and reads this page; tap off → stops. When on, **every page** is read 0.35 s after opening | "Medicines. Today: 8:00 AM, Metformin, Due now." |
| Title | page | max 1 line, ellipsis | "Here's what I noted" |
| More below | scroll state | appears only when the page scrolls further; tap scrolls 80 % of a screen | |
| 🏠 Home | – | `nav.home()` | |
| 🆘 SOS | – | opens the Emergency page (does **not** start SOS by itself) | |
| 👪 Family | – | Home, then Family page | |
| Undo bar | `UndoHost` | 3 s, then gone; runs the undo lambda | "Saved." / "Meal deleted." / "Note removed." |

Tapping: all buttons use `steady()`. It acts when the finger **lifts**. Any two taps closer than 0.25 s (0.6 s with Steady touch) anywhere in the app are
treated as one, because one global timestamp is shared by every button.

### 3.3 The task frame ("FlowScreen") used by setup, add medicine and doctor/helper forms

```
┌──────────────────────────────────────────────┐
│ (⬅️)        Task name small        🔊 Read (✖️)│
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬░░░░░░░░░░░░░░░░░░░░░░░░ │  ← progress: one segment per step
│                                              │
│ The question, largest words                  │
│ A line of help                               │
│                                              │
│  answers …                                   │
├──────────────────────────────────────────────┤
│ [ Skip / Previous ]   [      Next       ]    │  ← main action; quiet second choice on the left
└──────────────────────────────────────────────┘
```
In a sheet (adding a medicine over another page): a grey handle, ✖️ Close on the left, "Previous" instead of Back.

---

## Part 4. Page by page

Each page has:
1. **Layout** as it normally looks, with example data.
2. **Elements**: what each thing shows, where the data comes from, what a tap does.
3. **Worst case**: the same page when things go wrong, drawn, with the bug IDs.

---

### 4.1 First-time setup (`OnboardingScreen.kt`)

24 steps. Progress bar counts 18 of them. Every answer is saved as soon as it is given. The step number is saved in
prefs (`onboard_step`) so reopening the app returns to the same question.

```
Step order:
WELCOME ─▶ WHO ─▶ (ORDER, only if both) ─▶ SIZE ─▶ LANGS ─▶ NAME ─▶ BORN ─▶ SEX ─▶ CONDITIONS ─▶ SYMPTOMS
─▶ MEDS ─▶ TREATMENTS ─▶ ALLERGY ─▶ RISKS ─▶ DOCTORS (⇄ DOCTOR_FORM) ─▶ HELPERS (⇄ HELPER_FORM)
─▶ EMERGENCIES ─▶ CHECKIN ─▶ PERMISSIONS ─▶ WIDGET ─▶ DONE
(READ exists in the code but next() skips it: ⚪ unreachable)
```

**Welcome**

```
┌──────────────────────────────────────────────┐
│                                      🔊 Read │
│ Welcome to MedLog                            │
│ Everything stays on this phone.              │
│   ┌──────────────────────┐ ┌──              │  ← carousel, moves every 3.5 s
│   │        [👆]           │ │               │    (Tell how you feel · Medicines on time ·
│   │  Tell how you feel    │ │               │     Help in one tap · One page for the doctor)
│   │ Tap a picture. A few  │ │               │
│   │ short questions.      │ │               │
│   └──────────────────────┘ └──              │
│              ● ━━ ● ●                        │
├──────────────────────────────────────────────┤
│ [ Skip ]           [        Start         ]  │
└──────────────────────────────────────────────┘
Skip ─▶ sheet: "Skip setup completely" (finishes, onboarded=true) · "Use a setup file" (loads a JSON setup file)
```

**How will you use MedLog?** Two big cards: 👤 "My health" / 👪 "I help someone". Tap one or both.
- only "I help someone" → role = helper, goes to Helper home and opens Pair.
- both → "Whose setup first?" (Mine first / Theirs first).

**Make MedLog easy for you** (accordion, one group open at a time)

| Group | Card | Setting it changes | Real effect |
|---|---|---|---|
| Seeing | Regular words / Large words | `bigMode` | Whole app 1.2× |
| Seeing | Words look faint | `boldText` | Medium weight |
| Seeing | Hard to see edges | `highContrast` | Black on white |
| Reading | Reading long text | `readAloud`, `autoRead` | Every page read out |
| Reading | Not sure what a button does | `touchToHear` | Tap to hear, tap again to press |
| Hearing | Missing alarms: "Flash and vibrate with every alert" | `flashAlerts` | ⚪ **Nothing reads it** |
| Hearing | Voice too fast | `speechRate` 0.8 / 1.0 | Works, but Settings then shows no option selected (B35) |
| Hands | Shaky hands | `steadyTouch` | 0.6 s tap guard |
| Hands | I use my left hand | `leftHand` | Bottom bar reversed |
| Movement | Moving screens bother me | `lessMotion` | No animation |

**Other steps**

| Step | Question | Saves | Example |
|---|---|---|---|
| LANGS | Which languages do you read? (12 tiles: English, हिन्दी, தமிழ் …) | `languages` (first = main) | Tamil then English |
| NAME | What is your name? | `profile.name` | "Kamala Devi" |
| BORN | Which year were you born? (year wheel 1920–now) | `profile.dob = "<year>-07-01"` (B41) | 1952 → "1952-07-01", "That's about 74 years old." |
| SEX | Woman / Man / I'd rather not say | `profile.sex` F/M/"" | |
| CONDITIONS | 12 illnesses + "Something else" | `profile.conditions` "Diabetes, High BP, glaucoma" | |
| SYMPTOMS | "What do you feel these days?" picture tiles, first "Common with Diabetes" | `plan.symptoms` | Tired, Knee pain |
| MEDS | Medicine cards + "Add a medicine" (opens the medicine flow) | medicines table | |
| TREATMENTS | Insulin, Dialysis, Oxygen, Physio, Chemo, Blood thinner, Inhaler, Wound dressing | `plan.treatments` (+ `onBloodThinner`) | |
| ALLERGY | "I have no allergies" / "I'm allergic to something" → text | `profile.allergies` | "Penicillin, rash" |
| RISKS | Falls, Low sugar spells, Breathing trouble, Heart attack before, Stroke before, Fits, Serious allergy, Gets lost, Lives alone | `plan.risks` | |
| DOCTORS | List + "Add a doctor" (name, speciality chips, phone / contacts) | `plan.doctors` | "Dr Rao · Heart · 98…" |
| HELPERS | Explainer (📨 text → 📞 call one by one → 🏥 then 108) or helper cards with "Connect phone", ⋯ (Send a text, Call earlier, Change, Remove) | helpers table | |
| EMERGENCIES | "Which of these are emergencies for you?" picture tiles, **pre-ticked** | `plan.emergencies` | see B02 |
| CHECKIN | 🌅 8 am · ☀️ 10 am · ☀️ 1 pm · 🌇 6 pm · "Don't ask me every day" | `checkInEnabled`, `checkInTime` | |
| PERMISSIONS | Reminders, Texts, Calls, Location switches, then exact alarm / lock screen / battery | Android permissions | |
| WIDGET | Picture of the widget, "Add to home screen" | – | |
| DONE | 6 tiles: Doctors, Helpers, Medicines, Illnesses, Emergencies, Check-in; "MedLog is not a doctor" | onboarded=true, role=self, reschedule | |

**Worst case: the emergencies step**

```
┌──────────────────────────────────────────────┐
│ ⬅️           Setting up               🔊 Read │
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬░░░░░░░░ │
│ Which of these are emergencies for you?      │
│ Your helpers are called straight away.       │
│ ┌──────────────┐ ┌──────────────┐            │
│ │ ✅ [picture] │ │ ✅ [picture] │            │  ← Chest pain, Hard to breathe and Fainted are
│ │  Chest pain  │ │Hard to breathe│           │    pre-ticked for EVERY person (B02)
│ └──────────────┘ └──────────────┘            │
│ ┌──────────────┐ ┌──────────────┐            │
│ │ ✅ Fainted   │ │   Fall        │           │
│ └──────────────┘ └──────────────┘            │
│  …  (untick them all, go Back, come to Helpers again → they are ticked again, B02)
├──────────────────────────────────────────────┤
│ [ Skip ]           [         Next         ]  │
└──────────────────────────────────────────────┘
Later, a person with COPD taps "Hard to breathe" on Home (it is also suggested to them as "Common with Asthma or COPD"):
  ─▶ no questions, no countdown ─▶ SMS with location to all SOS helpers ─▶ helper phones ring ─▶ calls each helper
  ─▶ "Calling 108 in 20" ─▶ if nobody taps, the phone dials 108 on speaker.
```

Other worst cases here:
- Year of birth becomes a fake full date "1952-07-01" and is shown as a real date in Settings → My details (B41).
- "Skip setup completely" does not arm the medicine alarm (`finishNow` doesn't call `Scheduler.reschedule`); the app arms it at the next app start or the next medicine save. 🔵
- The Treatments "Blood thinner" tap writes from the profile copy drawn on screen, not the latest one; very quick taps on two treatments can undo each other. 🔵

---

### 4.2 Home (`HomeScreen.kt`)

```
┌──────────────────────────────────────────────┐
│ GOOD MORNING                         🔊 Read │  ← eyebrow = greeting (only when a name exists)
│ Kamala                                       │  ← first name, or the greeting
│ Monday, 28 September                         │
├──────────────────────────────────────────────┤
│ [    Me    |  I help someone  ]              │  ← PersonaSwitch (asks before switching)
│ ┌──────────────────────────────────────────┐ │
│ │ How are you feeling?               ( ➡️ ) │ │  ← teal hero card → Tell
│ │ Tap to choose                            │ │
│ └──────────────────────────────────────────┘ │
│ [A new MedLog is ready …]  (only when found) │
│ ┌─⚠️ Reminders are off ─────────────────────┐ │  ← only if exact alarms or notifications not allowed
│ │   Tap to turn them on                    │ │     → Permissions
│ └──────────────────────────────────────────┘ │
│ ┌──────────────────────────────────────────┐ │
│ │ [🤕] Is your headache better now?        │ │  ← only for an ongoing problem noted > 20 h ago
│ │ [ ✅ Yes, better ]   [ Still there ]      │ │
│ └──────────────────────────────────────────┘ │
│ Today's medicines                   [➕ Add] │
│ 1 of 3 taken                                 │
│ ┌─────────────────────────────── (green) ──┐ │
│ │ 🌅 Morning · 8:00 AM                     │ │
│ │ [pill] Metformin 500 mg                  │ │
│ │        1 tablet · For diabetes           │ │
│ │ ✅ Taken at 8:04 AM               Undo   │ │
│ └──────────────────────────────────────────┘ │
│ ┌─────────────────────────────── (amber) ──┐ │
│ │ ☀️ Due now · 1:00 PM                      │ │
│ │ [pill] Amlodipine 5 mg                   │ │
│ │ [          I took it           ]         │ │
│ └──────────────────────────────────────────┘ │
│ ┌─────────────────────────────── (white) ──┐ │
│ │ 🌙 Night · 9:00 PM                        │ │
│ │ [pill] Atorvastatin 10 mg                │ │
│ │ [          Log taken           ]         │ │
│ └──────────────────────────────────────────┘ │
│ Recent                                       │
│ ┌────────┐ ┌────────┐ ┌────────┐             │
│ │ [pic]  │ │ [pic]  │ │ [pic]  │             │
│ │Headache│ │ Dizzy  │ │ Cough  │             │
│ │2 today │ │        │ │        │             │  ← amber "N today"
│ └────────┘ └────────┘ └────────┘             │
│ ┌──────────────────────────────────────────┐ │
│ │ [🕘] History                           › │ │
│ │      Last: Headache, 28 September        │ │
│ └──────────────────────────────────────────┘ │
│ More                                         │
│ ┌────────┐ ┌────────┐ ┌────────┐             │
│ │  🍽️    │ │  🩺    │ │  🏥    │             │
│ │Food &  │ │BP &    │ │Doctor  │             │
│ │water   │ │sugar   │ │page    │             │
│ └────────┘ └────────┘ └────────┘             │
│ ┌────────┐ ┌────────┐ ┌────────┐             │
│ │  📈    │ │  💊    │ │  ⚙️    │             │
│ │My      │ │Medicines│ │Settings│            │
│ │health  │ │        │ │        │             │
│ └────────┘ └────────┘ └────────┘             │
├──────────────────────────────────────────────┤
│   🏠 Home      [ SOS ]      👪 Family         │
└──────────────────────────────────────────────┘
```

| Element | Shows | Source | Tap | Example |
|---|---|---|---|---|
| Me / I help someone | role | `settings.role` | dialog → switch role (helper → Helper home; self → Home or setup if no name) | |
| How are you feeling? | – | – | Tell (pick) | |
| Update card | new version | `Updater.state` | Update now / Allow updates | "A new MedLog is ready: 2.9.1" |
| Reminders are off | – | exact-alarm and notification permission | Permissions page | |
| Is your X better now? | ongoing problem | `recentProblems(3)` + `asked_better_<id>` | Yes → saves a "better" note (B19); Still there → Tell for that problem | |
| Today's medicines | caption | today's doses of **all** medicines | Add → add medicine sheet | "1 of 3 taken" |
| Dose card | when, pill picture, name + strength, "1 tablet · For diabetes", state | dose + medicine | name row → Medicines; button → take; Undo → untake | see 2.1 |
| Recent | up to 3 problem tiles | `recentProblems(3)` | Tell straight to questions | |
| History card | last noted problem | recent list | History | |
| More tiles | 6 tiles (fewer when hidden by a helper) | `settings.hidden` | pages | |

**Worst case**

```
┌──────────────────────────────────────────────┐
│ GOOD MORNING                         🔊 Read │  ← it is 2:10 AM (B44)
│ Kamala                                       │
├──────────────────────────────────────────────┤
│ Today's medicines                   [➕ Add] │
│ 1 of 4 taken                                 │  ← counts a stopped medicine and a feed (B05, B26)
│ ┌─────────────────────────────── (amber) ──┐ │
│ │ 🌅 Due now · 8:00 AM                     │ │  ← this medicine was STOPPED at 8:05.
│ │ [pill] Warfarin 5 mg                     │ │    Its 8:00 dose stays open: at 8:30 helpers get
│ │ [          I took it           ]         │ │    "hasn't marked the 8:00 AM medicine (Warfarin)…" (B05)
│ └──────────────────────────────────────────┘ │
│ ┌─────────────────────────────── (white) ──┐ │
│ │ ☀️ Afternoon · 1:00 PM                    │ │  ← opened at 12:45 and left open: stays white
│ │ [pill] Amlodipine 5 mg                   │ │    "Log taken" at 1:05 because Home never refreshes
│ │ [          Log taken           ]         │ │    by itself (B34)
│ └──────────────────────────────────────────┘ │
│ Recent                                       │
│ ┌────────┐                                   │
│ │Headache│                                   │
│ │2 today │                                   │  ← told once, then tapped "Yes, better": counted twice (B19)
│ └────────┘                                   │
│ ┌──────────────────────────────────────────┐ │
│ │ [🕘] History                           › │ │
│ │      Last: Headache, 28 September        │ │  ← the "better" tap counts as the last headache note
│ └──────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```

---

### 4.3 Tell how you feel (`TellScreen.kt`, `Common.kt`, `Interview.kt`, `Triage.kt`)

Five phases: **CONFIRM** (only for words from Assistant/links: "Is it headache?" Yes/No) → **PICK** → **ASK** → **SUMMARY**, or **DANGER** at any point.

**PICK: "How are you feeling?"**

```
┌──────────────────────────────────────────────┐
│ ⬅️                                   🔊 Read │
│ How are you feeling?                         │
│ ┌──────────────────────────────────────────┐ │
│ │ 🔍 Search problems              [🎤 Speak] │ │  ← typing filters at once; Speak = phone's speech typing
│ └──────────────────────────────────────────┘ │
│ You told me before                           │  ← only what was logged (180 days), max 6
│ [Headache] [Dizzy] [Knee pain]               │
│ Others you may have                          │  ← "Common for you" when nothing logged yet
│ [Tired] [High sugar] [Low sugar] …           │
│ Head and face        (every body area)       │
│ [Headache] [Migraine] [Dizzy] …              │
│ …                                            │
│   🏠 Home      [ SOS ]      👪 Family         │
└──────────────────────────────────────────────┘
```

Search: the typed words go through the sentence parser, then name and synonym matches, max 12. Example: "my head hurts" → Headache.

**ASK: one question at a time**

```
┌──────────────────────────────────────────────┐
│ ⬅️      [pic] Knee pain                   ✏️  │  ← ✏️ = change the problem (back to PICK)
│ Question 3 of 4                              │  ← only on core questions
│ How much does it hurt? Say a number from     │
│ 0 to 10.                                     │
│ வலி எவ்வளவு? (translation, if the language   │  ← only for a non-English main language
│ has one)                                     │
│ ┌──────┐ ┌──────┐ ┌──────┐                   │
│ │ 😀 0 │ │ 🙂 2 │ │ 😐 4 │                   │  ← Wong-Baker faces, value 0–10
│ │No hurt│ │a little│ │a bit more│            │
│ └──────┘ └──────┘ └──────┘                   │
│ ┌──────┐ ┌──────┐ ┌──────┐                   │
│ │ 😟 6 │ │ 😣 8 │ │ 😭 10│                   │
│ └──────┘ └──────┘ └──────┘                   │
│ Or tap the exact number                      │
│ [0][1][2][3][4][5]                           │
│ [6][7][8][9][10]                             │
├──────────────────────────────────────────────┤
│ [   Skip   ]            [   Finish   ]       │
│   🏠 Home      [ SOS ]      👪 Family         │
└──────────────────────────────────────────────┘
```

Answer types:

| Kind | Looks like | Stored as | Example |
|---|---|---|---|
| YESNO | ✅ Yes (green) / ✖️ No (white), with translation | true/false | "Did you hit your head?" |
| CHOICE | picture tiles 2 across, or plain buttons | the choice's value | When: Just now · Earlier today · Yesterday · A few days ago · A week or more |
| SCALE | 6 face tiles 3 across + exact 0–10 keys | integer | 6 |
| MULTI | tiles with ✅, then "That's it" | list | Feels like: Sharp, Throbbing |
| NUMBER | count tiles (Once, 2, 3, 4, 5 or more=6) or number pad 0–999 | integer | 3 |
| TEMP | number pad, **°F only 93–110** (B22) | decimal | 101.2 |
| BODY | Front/Back switch, body picture to tap, "Show the whole body", "It's here: left knee", "It's all over my body" | site label + pin "front:x,y" | "Left knee" |
| FREE | search box with 🎤, "Save this" | text | "Paracetamol 650" |

Core questions (max): When · up to 2 danger questions · How many times (countable problems) · Where (locatable) · burn look/size · How bad.
Then "Can you tell me a little more? It helps your doctor." → Yes: up to 9 of depth, feels like, remaining danger questions,
catalogue follow-ups, yes/no fields, pattern, worse with, better with, took medicine (→ which), anything else.

**SUMMARY: "Here's what I noted"**

```
┌──────────────────────────────────────────────┐
│                                      🔊 Read │
│ Here's what I noted                          │
│ ┌──────────────────────────────────────────┐ │
│ │ [pic] Knee pain                          │ │
│ │ ──────────────────────────────────────── │ │
│ │ Started     Earlier today, before 10:06 AM│ │
│ │ Where       Left knee                     │ │
│ │ How bad     Bad                           │ │
│ └──────────────────────────────────────────┘ │
│ ┌── (amber, only if AMBER) ────────────────┐ │
│ │ ▲ Please call your doctor today.          │ │
│ │ A fall: tell the doctor today             │ │
│ │ [📞 Call Dr Rao   Bones and joints ]      │ │  ← doctor whose speciality fits the problem
│ └──────────────────────────────────────────┘ │
│ [ ✅             Save                  ]      │
│ [          Tell a little more          ]      │  ← only if extended questions were not answered
│ [ ✏️        Change an answer            ]      │
│   🏠 Home      [ SOS ]      👪 Family         │
└──────────────────────────────────────────────┘
```

**DANGER: "Get help now"** (RED)

```
┌──────────────────────────────────────────────┐
│                                      🔊 Read │
│ Get help now                                 │
│ This could be serious. Get help now.   (red) │
│ ┌──────────── red gradient ────────────────┐ │
│ │               ( 📞 )                      │ │
│ │            Call 108                       │ │  ← ONE TAP dials (direct call if allowed)
│ │     Ambulance · free · any time           │ │
│ └──────────────────────────────────────────┘ │
│ ┌── WHILE YOU WAIT (only for low sugar) ───┐ │
│ │ Take 3 teaspoons of sugar in water …      │ │
│ └──────────────────────────────────────────┘ │
│ ┌──── dark ────────────────────────────────┐ │
│ │ (👪) Hold to alert my family              │ │  ← hold 2 s → SOS without countdown
│ │     Texts your location to 3, then calls… │ │
│ └──────────────────────────────────────────┘ │
│ Call one person:  (R) Ravi  (L) Latha  (➕)   │
│ WHY                                          │
│ ● Chest pain with spreading pain, sweating … │
│ Your helpers have been sent a message.       │  ← shown whenever helpers exist (B31)
│ [     This is wrong – change it      ]        │  ← goes to SUMMARY
└──────────────────────────────────────────────┘
Mental-health variant (self_harm): "You are not alone" · Call helpline 14416 · Call <first helper> · I'm safe for now
```

**Worst cases in Tell**

```
A) Vomiting told 3 times in one note
   ┌──────────────────────────────────────────┐
   │ ▲ Please call your doctor today.          │
   │ Vomiting 6 times in 24 hours              │  ← really 3. The note is counted from the database
   └──────────────────────────────────────────┘    AND again from the answer (B06)
B) Second dizzy note in two days
   │ ▲ Dizzy 3 times in 2 days                 │  ← really 2 (B07)
C) "Hard to breathe" is one of the person's emergencies (default for everyone, B02)
   ─▶ DANGER page, SOS already running, no questions asked.
   ─▶ The note is saved with triage GREEN: History and the doctor page show no "Urgent" mark (B08).
D) A RED note, then "This is wrong – change it" ─▶ SUMMARY ─▶ Save
   ─▶ persist() runs again, sees RED, and texts all helpers a SECOND time, then goes Home anyway (B09).
E) Words from Google Assistant: "vomiting and stomach pain, took paracetamol" saves 3 notes.
   Save ─▶ Undo removes only the vomiting note; stomach pain and "Took paracetamol" stay (B37).
F) Temperature question: the person's thermometer says 38.5 °C
   ┌──────────────────────────────────────────┐
   │            38.5 °F                        │
   │ That number looks wrong. Please check.    │  ← only 93–110 °F accepted (B22)
   │ [ Done ] (disabled)                       │
   └──────────────────────────────────────────┘
G) Back on the first question leaves a half-filled note saved (by design: "nothing is ever lost"),
   with no follow-up reminder and no message saying it was kept. 🔵
H) Typing "fitness" in search offers "Fit / seizure"; "computing" offers "Burning urine" (synonym "uti") (B43).
```

---

### 4.4 Medicines (`MedsScreens.kt`)

```
┌──────────────────────────────────────────────┐
│ ⬅️                                   🔊 Read │
│ Medicines                                    │
│ What to take, and when                       │
├──────────────────────────────────────────────┤
│ Today                                        │
│ 1 of 3 taken                                 │
│ [dose cards, same as Home; name row → change medicine]
│ When needed                                  │
│ Only when you need one                       │
│ ┌──────────────────────────────────────────┐ │
│ │ [pill] Paracetamol          [I took one] │ │  ← double-dose guard (minGapHours)
│ │        For fever                         │ │
│ └──────────────────────────────────────────┘ │
│ My medicines                        [➕ Add] │
│ 4 medicines · tap one to change              │
│ ┌──────────────────────────────────────────┐ │
│ │ [pill] Metformin 500 mg                › │ │
│ │        For diabetes                      │ │  ← or "Add what it's for" in teal
│ │        8:00 AM and 8:00 PM · 1 tablet    │ │
│ └──────────────────────────────────────────┘ │
│   🏠 Home      [ SOS ]      👪 Family         │
└──────────────────────────────────────────────┘
"Already taken" page (as-needed): "You already took Paracetamol at 2:10 PM. It is safest to wait 4 hours between doses."
  [ ✅ Yes, again ] [ ✖️ No, wait ]
```

Data: today's doses (live), active medicines except feeds (live). Opening the page re-arms the alarm.
Tapping "I took one" saves a MED_TAKEN note, says "Noted. You took Paracetamol.", Undo 3 s. **It does not lower the pill count.** 🔵

**Worst case**

```
│ Today                                        │
│ 1 of 4 taken                                 │  ← 4 counted, 2 cards shown: the feed and the stopped
│ [card: Metformin ✅]                          │    medicine are counted but hidden (B26)
│ [card: Amlodipine]                           │
│ My medicines                                 │
│ [Syrup "Cremaffin", 10 ml, Tablets left 150] │  ← after one dose: 149 "tablets" (a syrup counts 1 per dose),
│                                              │    and "1½ tablets" medicines lose 10.5 per dose (B10)
```

---

### 4.5 Add or change a medicine (`MedAddFlow.kt`)

Opens as a sheet over the page behind it (that page shrinks and darkens). Steps for a new one:

```
NAME ─▶ KIND ─▶ STRENGTH ─▶ HOW OFTEN ─▶ (WHICH DAYS) ─▶ TIMES + AMOUNT  or  GAP ─▶ FOOD ─▶ (LOOKS LIKE) ─▶ CHECK AND SAVE
Changing an existing medicine opens straight on CHECK AND SAVE; each row there opens its step and "Done" returns.
```

| Step | Screen | Saved to | Example |
|---|---|---|---|
| Name | "What's the medicine called?" text box, 📷 "Take a photo of the strip" → reads the strip, "Tap the name if you see it:" chips | name, photoPath (and strength if read) | "Metformin" |
| Kind | Tablet · Capsule · Syrup · Drops · Injection · Inhaler · Cream, each with a drawn picture | form | Tablet |
| Strength | number box + mg/mcg/g/ml/% · "I don't know" | strength "500 mg" | |
| How often | 🔁 Every day · 📅 Some days of the week · 📆 Only when needed | asNeeded, days | |
| Which days | Monday … Sunday tiles | days "1,4" | Mon, Thu |
| Times | "How much each time" counter (½ 1 1½ 2 3 4 or 1–20 ml); time cards 🌅 8:00 AM · 1 tablet with ✖️; dashed "➕ Add a time" → wheel sheet (hour, minute in 5s, AM/PM) | amount, times "08:00,20:00" | |
| Gap | amount + Wait at least 4/6/8/12 hours | minGapHours | |
| Food | 🚫🍽️ Before · 🍽️ After · 🍜 With · 🕒 Any time | food | |
| Looks like | pill drawing; shape Round/Oval/Capsule/Long/Square/Diamond; 10 colours | shape, color | White round |
| Check and save | picture, name, rows (Name, Kind, Strength, How often, Times + "1 each time", Food, Looks like), Details (What it's for → picture picker; Tablets left → counter +10/+30; How long → days counter, 0 = Always), Alerts (Important medicine; It's a blood thinner), red outline "Stop this medicine" | everything | |

Save: inserts or updates, deletes future DUE doses, re-arms alarm, syncs calendar, closes.

```
Check and save
┌──────────────────────────────────────────────┐
│ ✖️        Change medicine                    │
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬ │
│ Check and save                               │
│ ┌──────────────────────────────────────────┐ │
│ │ [pill] Metformin                          │ │
│ │        500 mg · Tablet                    │ │
│ └──────────────────────────────────────────┘ │
│ Name                 Metformin             › │
│ Kind                 Tablet                › │
│ Strength             500 mg                › │
│ How often            Every day             › │
│ Times                8:00 AM, 8:00 PM      › │
│   1 each time                                │
│ Food                 After food            › │
│ Looks like           White round           › │
│ Details                                      │
│ What it's for        Diabetes              › │
│ Tablets left         60                    › │
│ How long             Always                › │
│ Alerts                                       │
│ Important medicine                    (  ●) │
│ It's a blood thinner                  (●  ) │
│ [        Stop this medicine        ] (red)   │  ← ONE TAP, no "Are you sure?" (B28)
├──────────────────────────────────────────────┤
│ [ Previous ]        [        Save        ]   │
└──────────────────────────────────────────────┘
```

**Worst case**

```
│ How long             Always                › │  ← the medicine really ends on 5 October (set earlier as 7 days).
│                                              │    The page shows "Always" (the box starts empty), and choosing
│                                              │    "Always" (0) keeps the old end date anyway (B27)
│ Tablets left         9                     › │  ← really 9.5; opening and saving drops the half (B46)
│ [        Stop this medicine        ]         │  ← a shaky tap stops a heart medicine: its reminders end at once,
│                                              │    and a dose already due keeps alerting helpers (B28, B05)
```

---

### 4.6 The medicine alarm (`DoseActivity.kt`, full screen over the lock screen)

```
┌──────────────────────────────────────────────┐
│ Medicine time (2)                    🔊 Read │  ← alarm tone until the card has loaded and starts speaking (max 1 min)
├──────────────────────────────────────────────┤
│ ┌── red border if Important, else teal ────┐ │
│ │ [photo or pill] Metformin                │ │
│ │                 1 tablet · 500 mg        │ │
│ │                 After food · due 8:00 AM │ │
│ │                 Important medicine (red) │ │
│ │ [ ✅            I took it              ]  │ │
│ │ [ 💤 In 10 min ]      [    Skip     ]    │ │
│ └──────────────────────────────────────────┘ │
│ (second medicine card)                       │
│ [ ✅          I took them all           ]     │
└──────────────────────────────────────────────┘
Feeds show [ ✅ Given ] [ Not given ] instead.
Skip ─▶ "Why skip?": Feeling sick · Ran out · Doctor said stop · Other · Back
Already taken ─▶ "You already took X at 8:04 AM. Take it again?" [Yes, again] [No]  (Yes texts helpers "marked … as taken twice")
Nothing due ─▶ "No medicine due" [Close]
```

Shows doses from the last 3 hours that are DUE or SNOOZED (snooze ended). Speaks "Time for your medicine. Metformin, 500 mg. 1 tablet. After food."
Notification buttons: "I took it/them", "In 10 min".

**Worst case**: the person taps "I took them all" when one was already taken: the "already" answer is ignored (no double-dose question for the batch). 🔵

---

### 4.7 Did I take my medicines? (only through `medlog://open?name=didtake`)

```
┌──────────────────────────────────────────────┐
│ Did I take my medicines?                     │
│ ┌── green if nothing pending, else amber ──┐ │
│ │ Yes. Today you took: Metformin at 8:04 AM.│ │
│ │ Still to take now: Amlodipine.            │ │
│ │ Later today: Atorvastatin at 9:00 PM.     │ │
│ └──────────────────────────────────────────┘ │
│ [pill] ✓ Metformin · 8:04 AM                 │
│ [ Take them now ]  (→ Medicines)             │
└──────────────────────────────────────────────┘
```

**Worst case**: a medicine stopped today appears as **"null at 8:04 AM"** in the spoken and written sentence, because only
active medicines are looked up (B25). Missed and skipped doses are not mentioned at all. 🔵

---

### 4.8 Food & water (`FoodReadings.kt`)

Three views in one segmented switch: **Water | Food | Feeds**. The last view is remembered while the app is open.

```
Water                                           Food                                     Feeds
┌──────────────────────────────────────┐        ┌──────────────────────────────────┐     ┌──────────────────────────────────┐
│ Water              [Change goal]     │        │ Today               [My health]  │     │ Today's feeds     [➕ Add feed]  │
│ Goal: 8 glasses a day                │        │ 820 kcal · 31 g protein          │     │ 2 of 4 given                     │
│ ┌──────────────────────────────────┐ │        │ [ ➕        Add food          ]   │     │ [dose card: 🌅 7:00 AM · Morning │
│ │  ┌──────┐   5 of 8               │ │        │ ┌──────────────────────────────┐ │     │  feed · 200 ml · Feed by tube    │
│ │  │░░░░░░│   glasses today        │ │        │ │ [🍽️] Idli             ┌─────┐│ │     │  [Given] [Not given]]            │
│ │  │██████│                        │ │        │ │ 2 medium · 8:10 AM    │ 272 ││ │     │                                  │
│ │  │██████│   [ ➕ Add a glass ]    │ │        │ │ ───────────────────── │kcal ││ │     │ (tap card name → "Stop this feed")│
│ │  │██████│   [  Remove one   ]    │ │        │ │ Sambar      100 g     │ 8 g ││ │     └──────────────────────────────────┘
│ │  └──────┘                        │ │        │ │ Coffee      150 ml    └─────┘│ │
│ └──────────────────────────────────┘ │        │ └──────────────────────────────┘ │
└──────────────────────────────────────┘        └──────────────────────────────────┘
```

| Element | Source | Tap | Example |
|---|---|---|---|
| Glass (one segment per glass in the goal) | water today, goal | – | 5 of 8 filled |
| Add a glass | – | WATER note count 1; Undo | |
| Remove one | newest water note today | soft-deletes it; Undo | |
| Change goal | – | counter 1–16 | 10 |
| Today kcal · protein | Σ meal details | – | |
| Meal card | items JSON | tap → Change meal / Delete meal (Undo) | |
| Feeds | feed medicines + today's doses | Given / Not given / Undo; "Stop this feed" (no confirm, B28) | |

**Worst case**

```
Water goal set to 12 here ─▶ Settings → Looking after you shows 6 / 8 / 10 with none selected (B48).
Feeds: "Stop this feed" is one tap from the card menu (B28).
Feed calories live only in prefs (feed_info): after a Backup restore on a new phone, every "given" feed counts 0 kcal (B16).
```

---

### 4.9 What did you eat? (`FoodPick.kt`)

```
┌──────────────────────────────────────────────┐
│ ⬅️                                   🔊 Read │
│ What did you eat?                            │
│ 🔍 Search dishes                    [🎤 Speak] │
│ [Cuisine ⌄] [Breakfast] [Lunch & dinner] [Snacks] [Desserts] [Drinks] [Fruits]
│ Eat again · Your usual dishes                │  ← top 4 from the last 30 days
│ ┌──────────────────────────────────────────┐ │
│ │ Idli                          ┌────────┐ │ │
│ │ 1 medium · 58 kcal            │  🍽️    │ │ │
│ │ 2 g protein                   │ [ADD]  │ │ │  ← after ADD: [ − 1 + ] and Small/Medium/Large
│ └───────────────────────────────└────────┘ │ │
│ Breakfast · 24 dishes                        │
│ … dish rows …                                │
│ ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐ │
│   ➕ Something else                           │  ← your own dish: name, By weight/By piece/Liquid, kcal, protein
│ └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘ │
├──────────────────────────────────────────────┤
│ 3 items                 [      Done      ]   │  ← pinned only once something is chosen
│ 386 kcal                                     │
└──────────────────────────────────────────────┘
Adding a main dish opens "With your idli?" (sambar, coconut chutney, tomato chutney, coffee).
```

Default meal filter by clock: 5–10 Breakfast, 11–15 Lunch, 16–18 Snacks, otherwise Lunch & dinner.
Saved note: `transcript = "idli 2 medium, sambar 100 g"`, `details = {items:[…], kcal:386, protein:12.3}`.

**Worst case**: a custom dish is kept in prefs (`custom_foods`) only: not in Backup, not deleted by "Delete everything" (B16, B36).

---

### 4.10 New feed (`FoodReadings.kt → FeedNewScreen`)

```
┌──────────────────────────────────────────────┐
│ ⬅️                                   🔊 Read │
│ New feed                                     │
│ Each feed: 380 kcal · 14 g protein           │
│ How it's given · Missed feeds alert helpers  │
│ ┌──────────────┐ ┌──────────────┐            │
│ │   🥛         │ │   💊         │            │
│ │ By mouth     │ │ By tube ✓    │            │  ← By tube also marks it Important (15-min helper alert)
│ └──────────────┘ └──────────────┘            │
│ Name · [Feed name: Morning feed]             │
│ What goes in · 2 items               [➕ Add] │
│ ┌ Ensure · 2 scoops · 230 kcal · 9 g protein ✖️┐
│ How much, how often · 7 am, 11 am, 4 pm, 9 pm│
│ Each feed        [ − 200 ml + ]              │  ← 50–600 ml in 50s
│ Feeds a day      [ −   4    + ]              │  ← 1–8, times are worked out, not chosen
├──────────────────────────────────────────────┤
│ [              Save feed               ]     │
└──────────────────────────────────────────────┘
```

Worst case: feed times can't be chosen or changed; a feed can't be edited after saving (only stopped). 🔵

---

### 4.11 BP, sugar & more (`FoodReadings.kt → ReadingsScreen`)

```
┌──────────────────────────────────────────────┐
│ ⬅️                                   🔊 Read │
│ BP, sugar & more                             │
│ Tap what you measured                        │
│ (amber card here if the last reading was AMBER, + Call doctor button)
│ Readings · 6 in 2 weeks            [Trends]  │  ← Trends → My health
│ ┌────────────────┐ ┌────────────────┐        │
│ │ 🩺             │ │ 🩸             │        │
│ │ 138/86         │ │ 142 mg/dL      │        │
│ │ Blood pressure │ │ Sugar          │        │
│ │ Today          │ │ Thursday, 24 …  │       │
│ └────────────────┘ └────────────────┘        │
│ ┌────────────────┐ ┌────────────────┐        │
│ │ ⚖️  62.4 kg    │ │ ❤️  78          │        │
│ │ Weight         │ │ Pulse          │        │
│ └────────────────┘ └────────────────┘        │
│ ┌────────────────┐ ┌────────────────┐        │
│ │ 🌬️  96%        │ │ 🌡️  –          │        │
│ │ Oxygen         │ │ Temperature    │        │
│ │                │ │ No reading     │        │
│ └────────────────┘ └────────────────┘        │
└──────────────────────────────────────────────┘
Tapping a tile opens a sheet:
  BP: [Top] [Bottom] (sys 60–260, dia 30–160, top > bottom) → "Save 138/86"
  Weight: Bluetooth scale card ("Step on your scale" → live kg → "Steady" → Save) or "Type it instead" (20–250 kg)
  Others: one box. Sugar 20–600 mg/dL · Oxygen 50–100 % · Temperature 93–110 °F · Pulse 30–220
  "Last · <reading> · Delete" row at the top (deletes the latest, Undo)
```

After Save: the reading is checked by DangerRules with a problem chosen from the reading:
BP sys < 100 → "low_bp" else "high_bp"; sugar < 100 → "low_sugar" else "high_sugar"; oxygen → "low_oxygen"; temp ≥ 100.4 → "fever".
RED → Danger page + helpers texted. AMBER → amber card on this page.

**Worst case**

```
│ ▲ Please call your doctor today.             │
│ Low BP (85/60) with dizziness                │  ← the person never said they were dizzy (B21)
Temperature sheet, 38.5 typed ─▶ "Please check the number." (°F only, B22)
Sugar 380 ─▶ AMBER "High sugar (380)"; sugar 400 ─▶ RED Danger page (as designed)
```

---

### 4.12 My health (`HealthScreen.kt`)

```
┌──────────────────────────────────────────────┐
│ ⬅️                                   🔊 Read │
│ My health                                    │
│ Your numbers over time                       │
│ [  W  |  M  |  6M  |  Y  ]                   │
│ ┌──────────────────────────────────────────┐ │
│ │ (🩺) Blood pressure                       │ │
│ │      Latest, 28 September · range 118 to 152
│ │ 138/86 mmHg                               │ │
│ │  ░░░░░░░░░░ normal band ░░░░░░░░░░   152  │ │
│ │  ●──●────●─────●──●                  121  │ │
│ │  ○──○────○─────○──○                   90  │ │  ← second, lighter line = bottom number
│ │ 29 Aug    8 Sep    18 Sep    28 Sep       │ │
│ └──────────────────────────────────────────┘ │
│ Nutrition · Last 2 weeks           [Report]  │  ← only when food/feeds/weight exist
│ [verdict card] [Calories 72%] [Protein 58%] [Weight −1.2]
│ All measures · Tap one to see its chart      │
│ (⚖️) Weight       62.4 kg        ╱╲_╱        │
│ (🩺) Blood pressure 138/86 mmHg  ─╲╱─         │
│ (🥛) Water        5 glasses      ▁▃▅         │
│ (💊) Medicines taken 67%         ▅▅▃         │
│ (🤒) Problems noted 2 noted      ▁▁▃         │
│ No data yet                                  │
│ (❤️) Pulse · (🌬️) Oxygen · (🌡️) Temperature · (🩸) Sugar   "No data"
└──────────────────────────────────────────────┘
```

Formulas: see Part 2.7. The chart opens on the first measure that has data.

**Worst case**

```
[ 6M ] chosen, measure "Problems noted"
│ 9  In total, last 6 months               │  ← wrong. A week with 3 notes on one day counts 3; a week with 1 note
│                                          │    on each of 5 days counts 1 (average per day). Real total 8 is
│                                          │    shown as 4 in that 2-week example; over 6 months it is far off (B20)
W/M "Problems noted" counts NOTES, not times: "Vomiting, 6 times" is one bar of 1 (B20).
"Better" taps are counted as problems (B19).
Water "Average a day" skips days with no water logged, so 1 day of 8 glasses in a week shows "8" (B20).
"Medicines taken" today includes doses due later this morning that are still inside their 3-hour window. 🔵
```

---

### 4.13 Nutrition (`NutritionScreen.kt`, `Nutrition.kt`)

```
┌──────────────────────────────────────────────┐
│ ⬅️                                   🔊 Read │
│ Nutrition                                    │
│ What went in, and weight                     │
│ [  1W  |  2W  |  1M  ]                       │
│ ┌── border = colour of the verdict ────────┐ │
│ │ Eating less than needed: 1320 of 1800    │ │
│ │ kcal a day (73%)                         │ │
│ │ ● Protein low: 38 of 60 g a day (63%)     │ │
│ │ ● Weight steady: down 0.4 kg in 12 days   │ │
│ │ ● Nothing logged on 3 days                │ │
│ └──────────────────────────────────────────┘ │
│ ┌─────────┐ ┌─────────┐ ┌─────────┐          │
│ │Calories │ │Protein  │ │Weight   │          │
│ │  73%    │ │  63%    │ │  −0.4   │          │
│ │1320 of  │ │38 of 60g│ │kg in 12 │          │
│ │1800 a day│ │a day    │ │days     │         │
│ └─────────┘ └─────────┘ └─────────┘          │
│ Calories each day · Dashed line: target 1800 │
│ ▁ ▅ ▃ ▇ ─ ▅ ▃ … - - - - - - - - (target)     │  ← red < 60 %, amber < 85 %, green otherwise
│ Weight · 62.8 → 62.4 kg  (line)              │
│ Day by day · 11 of 14 days logged            │
│ Mon, 28 Sep                    1480 kcal     │
│   52 g protein · 5 glasses water · idli …    │
│ Missed feeds · Feeds and changes · Also noticed (only when present)
│ Targets · About 30 kcal and 1 g per kg [Change]
│ Calories a day   1800 kcal                   │
│ Protein a day    60 g                        │
└──────────────────────────────────────────────┘
```

**Worst case**

```
│ Weight steady: up 3.0 kg in 10 days          │  ← GREEN. A 5 % gain in 10 days (fluid, heart or kidney) is called
│                                              │    "steady" because only losses are checked (B23)
│ Nothing logged on 13 days                    │  ← first day of using MedLog: the 13 days before it count (B24)
│ 3 feeds missed                               │  ← RED. A feed still open 2 h after its time already counts as
│                                              │    missed, although "Given late" is still possible for another hour. 🔵
```

---

### 4.14 For the doctor (`DoctorPage.kt`, `DoctorNote.kt`, `Pdf.kt`)

```
┌──────────────────────────────────────────────┐
│ ⬅️                                   🔊 Read │
│ For the doctor                               │
│ [ 1W | 2W | 1M | 3M ]                        │
│ 14 September – 27 September 2026             │
│ ┌──────┐ ┌──────┐ ┌──────┐                   │
│ │  4   │ │  1   │ │ 86%  │                   │  ← Symptoms · Need care (red/amber) · Doses taken (amber < 80)
│ │Symptoms│Need care│Doses taken│              │
│ └──────┘ └──────┘ └──────┘                   │
│ Most important                               │
│ ▌ Chest pain                     [Watch]     │
│ ▌ Chest pain: must be checked by a doctor…   │
│ ▌ 22 September                               │
│ Where on the body  [Front figure with ①②]    │
│ Symptoms                                     │
│ ┌──────────────────────────────────────────┐ │
│ │ ① [pic] Chest pain              [Watch]   │ │
│ │ ┌ Times ─────┐ ┌ Worst pain ─┐            │ │
│ │ │ 3 in 2 days│ │ 6 of 10 Moderate│         │ │
│ │ Each bar is one day  ▁ ▁ ▅ ▁ ▃ …          │ │
│ │ Started   A few days before …             │ │
│ │ Over time Getting worse (red)             │ │
│ │ Where     Centre of chest                 │ │
│ │ Warning signs  Sweating (red)             │ │
│ │ In the patient's words "…"                │ │
│ └──────────────────────────────────────────┘ │
│ Medicines                                    │
│ Metformin              24 of 28 taken        │
│ 500 mg, twice a day    ▓▓▓▓▓▓▓▓░░            │
│ Readings   [BP 138/86 mmHg Latest…] [Sugar …]│
│ Nutrition  🍽️ Eating less than needed …    › │
│ Patterns noticed                             │
│ ─────────────────────────────────────────── │
│ My questions for the doctor                  │
│ Can I stop the evening tablet?               │
│ [ ➕ Add a question ]                         │
│ My doctors  Dr Rao   Heart           Call ›  │
│ Visits  📝 Write what the doctor said  ›     │
│         📅 Appointments                ›     │
├──────────────────────────────────────────────┤
│ [ 🖨️  Print ]           [ 📤  Share ]         │  ← builds "Symptom-summary.pdf" (A4, + nutrition page)
└──────────────────────────────────────────────┘
```

PDF page 1: heading, patient line, Allergies (red if any), Conditions, Medicines, Main concerns + body diagram, Symptoms table
(#, Symptom, When, Where, Character, Notes), Medicines table, Readings, Timing noticed, Patient's questions, footer.
PDF page 2 (when nutrition data exists): verdict, findings, Calories/Protein/Weight/Targets/Feeds, day-by-day table, missed feeds, changes.

**Worst case**

```
│ ① [pic] Hard to breathe                       │  ← this problem started a full SOS, but the note was saved GREEN:
│   (no Urgent tag)                             │    no tag, not in "Most important", "Need care" stays 0 (B08)
│ ┌ Times ────┐ ┌ Worst pain ─┐                 │
│ │ 3 in 1 day│ │ 8 of 10     │                 │  ← "3" includes a "Yes, better" tap (B19); "Worst pain" is shown
│                                               │    for itching or breathlessness strength too (B42)
Questions can't be edited or removed here (only in History → the note → Remove). 🔵
```

---

### 4.15 After the visit (`DoctorScreens.kt → VisitScreen`)

```
┌──────────────────────────────────────────────┐
│ ⬅️  After the visit                          │
│ [🎤 Speak · Say who you saw, any new medicine, when to go back]
│ Doctor                    [Dr Rao        ]   │  ← pre-filled from profile doctor name
│ Did anyone come with you? [              ]   │
│ Reason for visit          [              ]   │
│ What happened (tests…)    [              ]   │
│ Referred to a specialist or for tests  (  ●) │
│ New prescription          [Metformin 500 ]   │
│ What is it for?           [              ]   │
│ Next appointment          [in 2 weeks    ]   │
│   Understood as Monday, 12 October 10:00 AM  │
│ Notes or questions for next time [       ]   │
│ [ ✅ Save ]                                   │
│ After saving, you'll add the new medicine…   │
└──────────────────────────────────────────────┘
```

Save → VISIT note "Saw Dr Rao for sugar check. blood test", the notes box becomes a **question for the doctor**, next
appointment added (calendar too), then opens Add medicine if a new prescription was typed.
Speech understanding (examples): "doctor rao" → "Dr Rao"; "come back after 2 weeks" → +14 days 10:00; "on 5 october" → next 5 Oct 10:00.

---

### 4.16 Doctor appointments (`DoctorScreens.kt → AppointmentsScreen`)

```
┌──────────────────────────────────────────────┐
│ ⬅️  Doctor appointments                       │
│ ┌──────────────────────────────────────────┐ │
│ │ Monday, 12 October · 10:00 AM            │ │
│ │ Dr Rao · City Hospital · Follow-up       │ │
│ │ [          Remove          ]             │ │  ← one tap; the Google Calendar event stays (B28)
│ └──────────────────────────────────────────┘ │
│ Add an appointment                           │
│ [Tomorrow] [In 1 week] [In 1 month] [Pick a date]
│ Monday, 12 October at 10:00 AM               │
│ Doctor [   ]  Place [   ]  For what [   ]    │
│ [ ➕ Save ] (enabled once a date is chosen)   │
└──────────────────────────────────────────────┘
Reminders: 7 pm the evening before ("Doctor visit tomorrow"), and 2 hours before ("Doctor visit in 2 hours").
```

Worst case: "Pick a date" accepts a date in the past; the appointment is saved and no reminder ever fires. 🔵

---

### 4.17 History (`NotesScreens.kt → NotesScreen`)

```
By day                                               By problem
┌──────────────────────────────────────────┐          ┌──────────────────────────────────────────┐
│                                  🔊 Read │          │ History                            ( 🔍 ) │
│ History                            ( 🔍 ) │          │ [  By day  |  By problem  ]              │
│ [  By day  |  By problem  ]              │          │ ┌──────────────────────────────────────┐ │
│ (‹)        Today             (›)         │          │ │ [pic] Headache            [Watch]  › │ │
│      Monday, 28 September 2026           │          │ │       5 times. Last noted today      │ │
│ [22][23][24][25][26][27][28]             │          │ └──────────────────────────────────────┘ │
│   •       •           •   ▲chosen (teal) │          │ ┌──────────────────────────────────────┐ │
│ How I felt                               │          │ │ [pic] Cough                        › │ │
│ ┌──────────────────────────────────────┐ │          │ │       2 times. Last noted Thursday,… │ │
│ │ [pic] Headache            [Watch]  › │ │          │ └──────────────────────────────────────┘ │
│ │ 10:06 AM · Bad · started earlier…    │ │          │ (last 3 months, newest first)           │
│ └──────────────────────────────────────┘ │          └──────────────────────────────────────────┘
│ Medicines, food and more                 │
│ [💊] Medicines · 2 of 3 taken   (amber)  │
│ [🥛] Water · 5 glasses                   │
│ [🍽️] Food · idli 2 medium, sambar 100 g  │
│ [🩺] BP 138/86 · 9:12 AM                 │
│ [🆘] SOS: Emergency: Hard to breathe  ›  │
│ [📝] Saw Dr Rao for sugar check       ›  │
└──────────────────────────────────────────┘
```

| Element | Source | Tap |
|---|---|---|
| Day arrows | chosen day (can't go past today) | previous / next day |
| Week strip | 7 days ending up to 3 days after the chosen day, dots = days with symptom notes (90 days) | pick a day |
| How I felt | SYMPTOM notes that day, with time and a short detail line, triage pill "Urgent"/"Watch" | Note page |
| Medicines line | doses that day: taken of total | – |
| Water / Food / Readings lines | notes that day | – |
| Other lines | SOS, VISIT, MED_TAKEN, QUESTION, IMPORTED notes | Note page |
| 🔍 | – | Find a note |
| By problem | symptom notes in 90 days grouped by problem, Σ count, worst triage | Problem history |

**Worst case**

```
│ How I felt                               │
│ [pic] Headache · 10:06 AM · Bad          │
│ [pic] Headache · 4:30 PM · Better now    │  ← the "Yes, better" tap is listed as a second headache and is
│                                          │    counted in "By problem: 2 times" (B19)
│ Medicines, food and more                 │
│  (nothing about the check-in at 10 am, the "Told helpers: …" messages, the replies from helpers,
│   "Missed Warfarin twice in a row", or "Possible fall: no answer, SOS started")   ← hidden kinds (B30)
│ [pic] Hard to breathe · 11:02 AM         │  ← started SOS, but no "Urgent" pill (B08)
```

There is no Back button in the History header (the page is reached from Home; the phone's Back key and 🏠 work). 🔵

---

### 4.18 Problem history (`ProblemHistoryScreen`)

```
┌──────────────────────────────────────────────┐
│ ⬅️  Headache                                  │
│ ┌──────────────────────────────────────────┐ │
│ │ [pic] 5 times                             │ │  ← Σ count in 90 days
│ │       in the last 3 months                │ │
│ │ ▁ ▁ ▃ ▁ ▁ ▇ ▁ ▁ ▁ ▃ ▁ ▁ ▁ ▅   (14 days)    │ │  ← amber bars, grey = none
│ │ 15 September                   Today      │ │
│ └──────────────────────────────────────────┘ │
│ [        Tell how it is now         ]        │  ← Tell with this problem
│ Timeline                                     │
│ ● Today                                      │
│ │  10:06 AM                     [Watch]  ›   │
│ │  How bad: bad, 6 out of 10                 │
│ │  Where: forehead                           │
│ │  Started: Earlier today, before 10:06 AM   │
│ ● Thursday, 24 September                     │
│ │  8:15 PM                               ›   │
└──────────────────────────────────────────────┘
```
Worst case: "better" notes show as timeline entries "Getting better" and add 1 to "5 times" (B19).

---

### 4.19 One note (`NoteDetailScreen`)

```
┌──────────────────────────────────────────────┐
│ ⬅️  Headache                                  │
│     Today at 10:06 AM                        │
│ ┌──────────────────────────────────────────┐ │
│ │ [pic] Headache                            │ │
│ │       ▲ Call doctor today   (if not GREEN)│ │
│ │ ───────────────────────────────────────── │ │
│ │ Started   Earlier today, before 10:06 AM  │ │
│ │ How bad   Bad                             │ │
│ │ Warning signs  None                       │ │
│ └──────────────────────────────────────────┘ │
│ ┌ In your words ─ "my head is paining"  ────┐ │  ← only if a transcript exists
│ [ ▶️ Play my voice ]  (only if audio exists)  │
│ [     Add more about this     ]              │  ← Tell with this note (extended questions)
│ [ 🗑️   Remove this note       ]              │  ← soft delete, Undo 3 s, kept 30 days in Removed notes
└──────────────────────────────────────────────┘
```
Non-symptom notes (readings, SOS, visit…) show their text line only. The SOS note's log and location (in `details`) are
**never shown** anywhere in the app. 🟡 (part of B17)

---

### 4.20 Find a note and Removed notes (`SearchScreen`, `RemovedScreen`)

```
Find a note                                     Removed notes
┌──────────────────────────────────────┐        ┌──────────────────────────────────────┐
│ ⬅️  Find a note                       │        │ ⬅️  Removed notes                     │
│ [Word to find: dizzy          ]      │        │ ┌──────────────────────────────────┐ │
│ [note rows, max 50]                  │        │ │ Today · Water: 1 glass           │ │
│ From your old reports                │        │ │ [ ♻️ Bring back ]                 │ │
│ [report lines, max 40]               │        │ └──────────────────────────────────┘ │
│ [ ♻️ Removed notes ]                  │        │ (deleted for good after 30 days)     │
└──────────────────────────────────────┘        └──────────────────────────────────────┘
```
Search runs on every letter typed (from 2 letters), in notes' text and transcript, and imported report lines. 🔵 A slow
earlier search can finish after a newer one and replace its results.

---

### 4.21 Family (`HelpScreens.kt → HelpScreen`)

```
┌──────────────────────────────────────────────┐
│                                      🔊 Read │
│ Family                                       │
│ Tell your family what you need               │
│ ┌── amber, only when no helpers ───────────┐ │
│ │ Add at least one helper so MedLog knows… │ │
│ │ [ Add a helper ]                          │ │
│ └──────────────────────────────────────────┘ │
│ ┌── amber, only when SMS not allowed ──────┐ │
│ │ Allow MedLog to send text messages…      │ │
│ │ [ Allow ]  (asks SMS + calls)             │ │
│ └──────────────────────────────────────────┘ │
│ ┌── status of the last message ────────────┐ │
│ │ (✅) Ravi answered                        │ │  ← or 🕒 Sending… / 🕒 Waiting for an answer /
│ │      Sent at 10:12 AM                     │ │    ⚠️ Nobody has answered yet / ⚠️ Couldn't send it
│ │ [ I need water ]                          │ │
│ │ ✓ Ravi · I'm coming                       │ │
│ │ ✓ Latha · Got it on their phone           │ │
│ │ ✓ Suresh · Text message sent              │ │
│ │ [📞 Call Ravi]  (only when no answer/failed)│ │
│ └──────────────────────────────────────────┘ │
│ Send a message                    [Change]   │
│ 4 messages · tap one to send                 │
│ ┌──────────────┐ ┌──────────────┐            │
│ │     🚶        │ │     🥛        │            │
│ │ Please come  │ │ I need water │            │
│ └──────────────┘ └──────────────┘            │
│ ┌──────────────┐ ┌──────────────┐            │
│ │     🚻        │ │     💊        │            │
│ │Help with     │ │Bring my      │            │
│ │bathroom      │ │medicine      │            │
│ └──────────────┘ └──────────────┘            │
│ Something else                               │
│ [💬 Type, or tap Speak          🎤 Speak]     │
│   Keep it for next time  (●)  [📨 Send]       │  ← appear once text is typed
│ Call your family                  [Manage]   │
│ (R) Ravi   Son                        (📞)   │
│ (L) Latha  Daughter                   (📞)   │
│   🏠 Home      [ SOS ]      👪 Family         │
└──────────────────────────────────────────────┘
No messages chosen yet → a teal card "Choose your messages ➡️" instead of the tiles.
```

Message icons/colours: Please come/Help me walk 🚶 purple · I need water 🥛 blue · Bathroom 🚻 teal · Medicine 💊 orange ·
I don't feel well 🤒 pink · I'm hungry 🍽️ green · Please call me 📞 green · everything else 💬 blue.

**Worst case**

```
│ (⚠️) Couldn't send it                     │  ← no helper paired AND SMS permission missing
│ [ I need water ]                          │    (the page still recorded a MESSAGE note "Sent: I need water")
Helper "Suresh" has "Tell them about missed medicines" OFF but "Call and message in an SOS" ON:
  he still gets every missed-medicine, check-in, refill and help-message text (B11).
"Manage" → My helpers → edit/remove any helper, even when a helper PIN locks Settings (B32).
```

---

### 4.22 My messages (`MessagesScreen`)

```
┌──────────────────────────────────────────────┐
│ ⬅️  My messages                               │
│ Your messages                                │
│ ┌──────────────────────────────────────────┐ │
│ │ [🚶] Please come                          │ │
│ │      On the widget                        │ │  ← first 3
│ │ [ Move up ] [ 🗑️ Remove ]                 │ │
│ └──────────────────────────────────────────┘ │
│ Add a message                                │
│ [ +  Please call me ] [ +  I'm hungry ] …    │  ← 13 ready-made, minus those chosen
│ Write your own                               │
│ [Your message: Please bring the newspaper]   │
│ [ ➕ Add my message ]                          │
└──────────────────────────────────────────────┘
```
Ready-made list: Please come · Please call me · I need water · Help with bathroom · Bring my medicine · I don't feel well ·
I'm hungry · Help me walk · Help me to bed · I feel cold · Come and sit with me · Help with my phone or TV · I'm OK, don't worry.

---

### 4.23 My helpers and Add/Change helper (`HelpersScreen`, `HelperEditScreen`)

```
My helpers                                      Change helper
┌──────────────────────────────────────┐        ┌──────────────────────────────────────┐
│ ⬅️  My helpers                        │        │ ⬅️  Change helper                     │
│ ┌──────────────────────────────────┐ │        │ [ 📇 Choose from contacts ]           │
│ │ 1. Ravi                          │ │        │ Name            [Ravi            ]   │
│ │ 98xxxxxx12 · Son · SOS ·         │ │        │ Phone number    [98xxxxxx12      ]   │
│ │ Phone paired ✓                   │ │        │ Relation        [Son             ]   │
│ └──────────────────────────────────┘ │        │ Call and message in an SOS     (  ●) │
│ ┌──────────────────────────────────┐ │        │ Tell them about missed medicines(  ●)│  ← see B11
│ │ 2. Latha                         │ │        │ Let them see my notes          (●  ) │  ← ⚪ stored, never used
│ └──────────────────────────────────┘ │        │ [ ✅ Save ]  (name + ≥ 6 digits)      │
│ [ ➕ Add a helper ]                   │        │ [ 🗑️ Remove this helper ] → confirm   │
│ [ 🔵 Pair a helper's phone ]          │        └──────────────────────────────────────┘
│ In an SOS, helpers are called in     │
│ this order.                          │  ← but there is no way to change the order here (B33)
└──────────────────────────────────────┘
```

---

### 4.24 Connect phones (`PairScreen`)

```
Person's phone                                  Helper's phone
┌──────────────────────────────────────┐        ┌──────────────────────────────────────┐
│ Connect phones                       │        │ Connect phones                       │
│ [My helper's phone | Someone I help] │        │ Your name  [Ravi            ]        │
│ [ Look for my helper's phone ]       │        │ [ Start pairing ]                    │
│ Looking… On your helper's phone:     │        │ Waiting for the other phone…         │
│ open MedLog → I'm a helper → Start…  │        │                                      │
│ [ Ravi ]  ← found                    │        │                                      │
│ Which of your helpers is Ravi?       │        │                                      │
│ ( ) Ravi · Son   ( ) Someone not in… │        │                                      │
│ [ Connect Ravi's phone ]             │        │                                      │
│ Check both phones show the same      │        │ Check both phones show the same      │
│ number:      4 7 2 9                 │        │ number:      4 7 2 9                 │
│ [ They match ]  [ They don't match ] │        │ [ They match ]  [ They don't match ] │
│ Ravi's phone is paired.  [Done]      │        │ Paired with Amma. You'll be alerted… │
└──────────────────────────────────────┘        └──────────────────────────────────────┘
```

**Worst case (security)**: both phones accept the Bluetooth connection **before** anyone compares the digits, and the
person's phone sends the secret key as soon as the link is up. "They match" does nothing (an empty function), so a
stranger's phone nearby that is also in pairing mode can be paired by one wrong tap, and will then receive the
person's SOS texts' content, help messages and location links through the relay (B04).

---

### 4.25 Emergency (`Emergency.kt`), opened by 🆘 in the bottom bar and the widget

```
┌──────────────────────────────────────────────┐
│ ⬅️                                   🔊 Read │
│ Emergency                                    │
│ ┌──────────── red gradient, pulsing ───────┐ │
│ │               ( 📞 )                      │ │
│ │            Call 108                       │ │  ← 1 tap: direct call when "Calls" is allowed
│ │     Ambulance · free · any time           │ │
│ └──────────────────────────────────────────┘ │
│ ┌──── dark ────────────────────────────────┐ │
│ │ (👪) Hold to alert my family              │ │  ← hold 2 s: SOS with countdown; a short tap
│ │     Texts your location to 3, then calls…│ │    only says "Keep holding for 2 seconds…"
│ └──────────────────────────────────────────┘ │
│ ┌──────────────────────────────────────────┐ │
│ │ Call one person                           │ │
│ │  (R)📞   (L)📞   (D)📞    (➕)            │ │  ← first 3 helpers + profile doctor; ➕ Add
│ │  Ravi    Latha   Dr Rao   Add            │ │
│ │  Son     Daughter Doctor                 │ │
│ └──────────────────────────────────────────┘ │
│      Feeling low? Free helpline 14416        │
│   🏠 Home      [ SOS ]      👪 Family         │
└──────────────────────────────────────────────┘
```

**Worst case**

```
│     Texts your location to 3, then calls…│  ← 3 counts every helper; SOS texts only those with "SOS" on (B38)
│            Call                            │  ← emergency number erased in Settings: the card says "Call",
│                                            │    tapping does nothing, and SOS's last step dials nothing (B14)
The doctor face uses the old single "doctor phone" from the profile, not the doctors list from setup. 🔵
```

---

### 4.26 SOS panel (`AlertActivity.kt → SosPanel`, full screen)

```
┌──────────────────────────────────────────────┐
│ Calling for help in 7                🔊 Read │
│ ┌──────────── red ─────────────────────────┐ │
│ │                  7                        │ │
│ │       Calling for help in 7               │ │
│ │ Then your family is messaged and called   │ │
│ └──────────────────────────────────────────┘ │
│ [ ✖️        Cancel – I'm OK           ]       │
│ [        Don't wait – start now       ] (red)│
│ ┌──────────────────────────────────────────┐ │
│ │ ① Message family · Your location by text │ │
│ │ ② Call family, one by one · On speaker   │ │
│ │ ③ Call 108 · If nobody answers           │ │
│ └──────────────────────────────────────────┘ │
│ ┌ What happened ───────────────────────────┐ │
│ │ 10:12:03 SOS started: SOS                 │ │
│ │ 10:12:09 Location found                   │ │
│ │ 10:12:10 SMS sent to Ravi, Latha          │ │
│ └──────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
Phases and buttons:
  Calling Ravi            → [ Help is coming – stop calling ]  [Cancel SOS]
  Did Ravi answer? (15)   → [ ✅ Yes, help is coming ] [ 📞 No, call the next person ]
  WhatsApp call started   → [ Help is coming ] [ Call helpers one by one ]
  Calling 108 in 20       → [ 📞 Call 108 now ] [ Help is already coming ]
  Help is coming / SOS finished (green) → [ Close ]
```

**Worst case**: "Yes, help is coming" saves the SOS note as just "SOS" with no reason and no location; cancelling during the
WhatsApp call saves no SOS note at all (B17). The saved log is never shown in the app (4.19).

---

### 4.27 Did you fall? (`AlertActivity.kt → FallPanel`, needs "Fall detection" on)

```
┌──────────────────────────────────────────────┐  (light red page)
│ Did you fall?                                │
│                  60                          │  ← counts down, spoken every 15 s
│ [ ✅              I'm OK                ]     │  → note "Possible fall: said I'm OK"
│ [        I fell – I need help        ] (red) │  → SOS, no countdown
│ [    I fell but I'm OK – note it     ]       │  → Tell with "Fall"
└──────────────────────────────────────────────┘
0 → note "Possible fall: no answer, SOS started" and SOS.
```
**Worst case**: the countdown lives inside the screen. The phone's Back key, a screen rotation, or anything that closes the
screen stops the countdown for good: no SOS, no note (B18). Whether this screen can open at all from the background
service on Android 10+ needs a device check (Part 6).

---

### 4.28 Morning check-in and "X asks: How are you?" (`AlertActivity.kt`)

```
Check-in                                        Asked by a helper
┌──────────────────────────────────────┐        ┌──────────────────────────────────────┐
│ How are you today?                   │        │ Ravi asks: How are you?              │
│ Good morning, Kamala! How are you…   │        │ [ 😊  I'm good      ] (green)         │
│ [ 😊  Good      ] (green)             │        │ [ 😐  I'm OK        ]                 │
│ [ 😐  OK        ]                     │        │ [ 😟  Not so well   ] (amber) → Tell  │
│ [ 😟  Not well  ] (amber) → Tell      │        │ [ 📞 Please call me ]                 │
│ [ Tell how I feel ]                  │        └──────────────────────────────────────┘
└──────────────────────────────────────┘
```
Check-in answers save a CHECKIN note (hidden in History, B30) and stop the "hasn't answered" text.
**Worst case**: if the full-screen page does not appear, the only way in is the notification "How are you today? Tap to
answer", which opens **Home**. Home has no check-in buttons, so the person cannot answer and 2 hours later their helpers
are texted "…hasn't answered the morning check-in yet." (B12).

---

### 4.29 The helper's phone (`HelperHomeScreen`, `HelperAlertPanel`, `HelperSettings`)

```
Helper home                                     Incoming alert (over the lock screen)
┌──────────────────────────────────────┐        ┌──────────────────────────────────────┐
│ Amma                            (⚙️)  │        │ (dimmed)                             │
│ [   Me   |  I help someone  ]        │        │ ┌── dark card, red when urgent ────┐ │
│ (amber: permissions / battery cards) │        │ │ ● Answer within                   │ │
│ ┌── teal border until answered ────┐ │        │ │ I need water                      │ │
│ │ Today 10:12 AM                    │ │        │ │ Amma · 10:12 AM                   │ │
│ │ I need water                      │ │        │ │  [0] : [2] [7]                    │ │  ← 30 s, beeps at 10/5/3,
│ │ [ 🚶 I'm coming ]                  │ │        │ │ ▬▬▬▬▬▬▬▬▬▬▬░░░░                    │ │    then continuous alarm
│ │ [ In 5 min ] [ I'll call ]        │ │        │ │ [        I'm coming         ]     │ │    up to 10 min
│ └──────────────────────────────────┘ │        │ │ [In 5 min][I'll call][Can't now]  │ │
│ [ 💬 Ask Amma how they are ]          │        │ │         Open MedLog               │ │
│ You asked at 9:00 AM    I'm good     │        │ └───────────────────────────────────┘ │
│ Other helpers                        │        └──────────────────────────────────────┘
│ [I'm going there now] [Can someone…] │
│ Earlier                              │
│ Please come        Yesterday 7:10 PM │
│ Help one more person                 │
└──────────────────────────────────────┘
Settings (⚙️): Nearby · Bluetooth · Far away · Connected / No internet / Text messages only · Connect again.
```
Messages older than 30 minutes arrive as a quiet notification, not an alarm.

---

### 4.30 Settings (`SettingsScreens.kt`)

```
┌──────────────────────────────────────────────┐
│ ⬅️  Settings                                  │
│ You                                          │
│  My details              Kamala Devi       › │
│  My doctors              2                 › │
│  My helpers              3                 › │
│  My messages             4                 › │
│ Seeing and hearing                           │
│  Text size               Regular           › │
│  Read aloud              When I tap        › │
│  Languages               Tamil, English    › │
│ Care                                         │
│  Medicine reminders      Again in 10 min   › │
│  Daily check-in          10:00             › │
│  SOS                     Calls 108 last    › │
│ This phone                                   │
│  Home-screen widget      Add               › │
│  Permissions                               › │
│  Backup and new phone                      › │
│  Updates                 2.9.0             › │  ← hidden for Play Store installs
│ More                                         │
│  Add my old reports   Photos or PDFs       › │
│  Bluetooth machines   BP, oxygen, …        › │
│  Helper controls      Hide features, lock… › │
│  Privacy                                   › │
│ MedLog 2.9.0 · clinical content 0.1.0 (not yet doctor-reviewed)
└──────────────────────────────────────────────┘
```

Sections behind the rows:

| Section | Contents | Notes |
|---|---|---|
| My details | Name, Date of birth (free text YYYY-MM-DD, not checked), Woman/Man/Not said, Blood group, Hospital ID, Illnesses (free text), Allergies, "I take a blood thinner", Save | An invalid date silently gives "no age" everywhere. 🔵 |
| My doctors | list; add/change: name, speciality chips (Family doctor, Heart, Diabetes, Kidney, Lungs, Brain and nerves, Bones and joints, Stomach, Cancer, Eyes, Skin, Women's health, Mind, Other), contacts or phone; remove | used by "Call <doctor>" on Summary/Readings |
| Medicine reminders | Remind again after 5/10/15 min · Tell my helpers after 20/30/45/60 min · "Important medicines: after 15 minutes" (fixed) · Google Calendar (Off / calendars, Plain titles) · Meeting Timer | |
| Looking after you | Morning check-in + 08/09/10/11 · Fall detection · Sunday summary (B29) · Always-there buttons · "I have diabetes" (⚪) · Glasses of water 6/8/10 (B48) | Setup offers 1 pm and 6 pm; picking them there shows none selected here 🔵 |
| SOS | explanation · Emergency number (digits, max 4, can be empty: B14) · Seconds before SOS 5/10/15 · Reach helper phones over the internet · Relay address (https only) · WhatsApp group link · Use WhatsApp group call (needs Accessibility) | |
| Languages and voice | 12 language switches, main language chips, voice ready per language, "Add a reading voice" | |
| Updates | update card, version | |
| Helper controls | Show Medicines / Food & water / BP, sugar & more / How am I doing / For doctor / **Help** (⚪ does nothing) · Helper PIN (4–6 digits) Set/Remove | names here differ from Home ("My health", "Doctor page") 🔵 |
| Locked (PIN set) | "Easy mode and reading aloud" button + PIN box; unlock lasts 10 minutes | the lock covers only this page (B32) |

---

### 4.31 Seeing and hearing (`EasySettingsScreen`)

```
┌──────────────────────────────────────────────┐
│ ⬅️  Seeing and hearing                        │
│ Text size        (●) Regular   ( ) Large     │
│ Read aloud       ( ) Read every page         │
│                  (●) Only when I tap Read aloud
│                  ( ) Never                   │
│ Reading speed  [ Very slow | Slow | Normal ] │  ← none highlighted by default (B35)
│ High contrast  · Bold text · Steady touch · Touch to hear · Left-hand mode · Less movement
│ Flash the screen for alerts   (⚪ does nothing)
└──────────────────────────────────────────────┘
```

---

### 4.32 Permissions (`PermissionsScreen`)

Cards, green with ✓ when allowed, else an "Allow" button:
Let MedLog hear you (mic) · Let MedLog remind you (notifications) · Send help messages (SMS) · Call your helpers (calls + phone state)
· Share where you are in an SOS (location, optional) · Reach phones in your home (nearby, optional) · Remind at the exact time ·
Show reminders on the lock screen · Keep working when the phone sleeps · maker tips (Xiaomi, Oppo/Realme/OnePlus, Vivo, Huawei, Samsung).

The microphone permission is asked for, but the app's own voice input now uses the phone's speech typing, which needs no
microphone permission in MedLog. 🔵

---

### 4.33 Backup and new phone (`BackupScreen`, `Backup.kt`, `SetupFile.kt`)

```
┌──────────────────────────────────────────────┐
│ ⬅️  Backup and new phone                      │
│ Setup file                                   │
│ Your details, helpers, languages, messages … │
│ [ Save setup file ]   [ Load setup file ]    │
│ Keep the file private: it has your name…     │
│ Full backup                                  │
│ The backup is locked with a password …       │
│ [Backup password ••••••]                     │
│ [ Save a backup ]  [ Restore a backup ]      │  ← both need ≥ 6 characters
│ ┌ Restored. Everything is back. ───────────┐ │
└──────────────────────────────────────────────┘
```

**Worst case**

```
Restore a backup made by MedLog 2.7 (database version 1, no `plan` column, no pill shape/colour):
  "INSERT INTO main.profile SELECT * FROM bk.profile" fails on the column count
  ─▶ "Could not restore. Check the password."   ← the password was right (B16)
Restore works (same version) ─▶ notes and medicines are back, but feed calories, custom foods and nutrition targets
  are not (prefs), and calendar event ids point at events that don't exist on the new phone (B16).
Load a setup file on a new phone ─▶ details, helpers, settings are back; doctors, "what I feel these days",
  treatments, risks and emergencies are not (B15).
```

---

### 4.34 Privacy (`PrivacyScreen`)

```
┌──────────────────────────────────────────────┐
│ ⬅️  Privacy                                   │
│ ┌──────────────────────────────────────────┐ │
│ │ Your notes stay on this phone.            │ │
│ │ MedLog uses the internet for one thing    │ │  ← not the only thing: it also checks GitHub for
│ │ only: passing help alerts …               │ │    updates once a day on sideloaded installs (B40)
│ └──────────────────────────────────────────┘ │
│ Things leave the phone only when you choose: │
│  • SOS and help messages … • Helper phones … │
│  • Your doctor page … • Google Calendar …    │
│ Your notes are locked (encrypted)…           │
│ [ Open App info ]                            │
│ [ Delete everything ]                        │
│ ┌── red ──────────────────────────────────┐  │
│ │ This deletes all notes, medicines and   │  │
│ │ helpers from this phone. It cannot be … │  │
│ │ [ Yes, delete everything ] (red)        │  │  ← CRASH (B01)
│ │ [ No, keep it ] (green)                 │  │
│ └─────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

**Worst case**: "Yes, delete everything" runs `clearAllTables()` on the screen's main thread. Room refuses that and throws
`IllegalStateException: Cannot access database on the main thread`, so the app closes. Nothing after it runs (B01).
If that is fixed, the wipe is still incomplete (B36).

---

### 4.35 Add my old reports (`ImportScreen`, `Ocr.kt`, `ReportAnalyzer.kt`)

```
┌──────────────────────────────────────────────┐
│ ⬅️  Add my old reports                        │
│ [ 📷 Take a photo ]                           │
│ [ Choose a PDF or picture ]                  │
│ Reading… this can take a minute …            │
│ Nothing is uploaded. The reading happens …   │
│ ── after reading ──                          │
│ Name on report: K DEVI                       │
│ Findings (3 of 3 chosen)                     │
│  HbA1c 8.2 %                (●)  2025-11-02  │
│ Medicines on the report                      │
│  • Metformin 500 mg                          │
│  Keep these for the doctor page  (●)         │
│ [ ✅ Save to my history ] [ Start again ]     │
└──────────────────────────────────────────────┘
```
Save → every line into `doc_lines` (search), chosen findings as IMPORTED notes on the report date, medicines as IMPORTED notes today.
**Worst case**: when nothing is found, the code sets the message "I couldn't find findings or medicines in this. The text is
still saved for search.", but that message is only drawn on the *before reading* layout, so it never appears. The person
sees an empty result page with "Save to my history" and "Start again", and the text is saved only if they tap Save (B39).
Importing the same report twice duplicates every line. 🔵

---

### 4.36 Bluetooth machines (`DevicesScreen`, `Ble.kt`)

```
┌──────────────────────────────────────────────┐
│ ⬅️  BP and sugar machines                     │
│ Works with machines that use the standard …  │
│ [ 🔵 Find my machine ]                        │
│ ┌ Found 1 machine. Tap it to connect. ─────┐ │
│ [ Blood pressure: OMRON HEM-7156T ]          │
│ Sugar meters can't be read automatically yet │
└──────────────────────────────────────────────┘
```
A reading from the machine is saved, spoken, and checked. A RED reading texts helpers but, unlike the typed reading,
**does not show the Danger page**. 🟡 (listed with B21 fixes)

---

### 4.37 Widget, always-there notification, quick-settings tile, notifications (`Widget.kt`)

```
Widget (size decides what fits: SOS row, status, symptom row, messages, next medicine)
┌──────────────────────────────────────────────┐
│ ✓ Headache (2nd today) 10:06 AM              │  ← status line for 30 min (or "✓ Ravi: I'm coming")
│ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐  │
│ │ [pic]  │ │ [pic]  │ │ [pic]  │ │ [pic]  │  │  ← your problems first, then suggestions; ONE TAP SAVES
│ │Headache│ │ Dizzy  │ │ Tired  │ │ Cough  │  │    (an emergency of yours opens MedLog instead)
│ └────────┘ └────────┘ └────────┘ └────────┘  │
│ [ Please come                      Send ]    │  ← first 3 messages, one tap sends to family
│ [ I need water                     Send ]    │
│ 1:00 PM · Amlodipine        [ I took it ]    │  ← button only within 15 min of the time
│ [   Something else…   ] [  SOS  ] (red)      │  ← SOS opens the Emergency page
└──────────────────────────────────────────────┘
Not set up / helper phone: "Open MedLog to finish setting up".
Always-there notification: "Tap to tell how you feel" + [Tell how I feel] [Medicines] [Help].
Quick-settings tile: "Tell MedLog".
```

**Worst case**: a widget tap saves the symptom with triage **GREEN** and no rule check at all. "Fit / seizure" (if not ticked
as an emergency) or a 6th "Vomiting" today is saved silently: no Danger page, no helper text, no "Watch" (B13).
"(2nd today)" counts "better" taps (B19).

**Every notification the app can post**

| Channel | Title → tap opens | When |
|---|---|---|
| Medicine reminders | "Time for Metformin" + [I took it] [In 10 min] → alarm page | dose time and repeats |
| Help alerts | "MedLog SOS" → SOS panel | SOS running |
| Help alerts | "<Helper> asks: How are you?" → answer page | helper asked |
| Help alerts (helper phone) | "Amma asked for help" / "Message from Amma" → alert page | message arrives |
| Help alerts (helper phone) | "Family: Latha" → Helper home | family chat |
| Check-in and gentle reminders | "How are you today?" → **Home** (B12) | check-in time |
| Check-in and gentle reminders | "Your week" → My health (nothing spoken, B29) | Sunday 6 pm |
| Check-in and gentle reminders | "Can you tell me a bit more?" → Tell (extended questions) | 30 min after a short note |
| Check-in and gentle reminders | "Doctor visit tomorrow" / "Doctor visit in 2 hours" → Doctor page | appointments |
| Check-in and gentle reminders | "Time to buy more medicine" → Medicines | 5, 2, 0 days left (B45) |
| Running in background | "Listening for Amma" / "Connected to your family" / "Watching for falls" | services |
| Quick buttons | "Tap to tell how you feel" | setting on |

---

## Part 5. Master bug list (ranked)

Paths are relative to `app/src/main/java/com/suryaprakash/medlog/` unless they start with `app/`.

### 🔴 Critical

| ID | What breaks | Where | Worst-case example | Suggested fix |
|---|---|---|---|---|
| **B01** | "Delete everything" crashes the app | `ui/screens/SettingsScreens.kt:470` | Tap Privacy → Delete everything → Yes: the app closes with "Cannot access database on the main thread"; nothing is deleted, settings not reset | Run `clearAllTables()` inside `withContext(Dispatchers.IO)` (it is a blocking call) |
| **B02** | Everyone gets Chest pain, Hard to breathe and Fainted as "call my helpers now" emergencies, and an emptied list is refilled | `data/CarePlan.kt:91`, `ui/screens/OnboardingScreen.kt:481`, `ui/screens/TellScreen.kt:209` | A person with COPD taps "Hard to breathe" (it is even suggested to them) → SOS with no countdown → helpers texted and called → 108 dialled after 20 s | Start with an empty list and only pre-tick from risks the person chose; remember "none" (store a flag instead of testing `isEmpty()`); for emergencies still ask one danger question or give a 10-s countdown with Cancel |
| **B03** | Any web page or app can make MedLog call, text or start SOS | `MainActivity.kt:74-85`, `app/src/main/AndroidManifest.xml:101` (`BROWSABLE` on `medlog://`) | A link `medlog://call` directly phones the first helper; `medlog://help?send=please_come` texts all helpers "Please come"; `medlog://sos` starts the SOS countdown | Remove `BROWSABLE`, or ignore `call`, `sos` and `send` unless the intent came from the widget/shortcut (use `PendingIntent`s and an internal action, or a random token) |
| **B04** | Pairing does not wait for "They match" | `help/Nearby.kt:132, 307, 403, 475` | Two phones in pairing mode nearby; the person taps the wrong name → the stranger's phone gets the key and later every SOS text with location | Call `acceptConnection` only from the "They match" button on both phones; send the key only after both accepted |
| **B05** | A stopped medicine keeps alerting | `meds/Scheduler.kt:123-150` (tick does not check `m.active`), `ui/screens/MedAddFlow.kt:404` (`dropFuture` only drops future doses) | Warfarin stopped at 8:05; its 8:00 dose stays "Due now" on Home, helpers are texted at 8:30, it is marked missed at 11:00 and counted in "Doses taken" | When stopping, also mark that medicine's open past doses as SKIPPED with reason "Stopped"; in `tick()` skip inactive medicines; filter inactive medicines on Home and in counts |

### 🟠 High

| ID | What breaks | Where | Worst-case example | Suggested fix |
|---|---|---|---|---|
| **B06** | Vomiting / loose motions counted twice | `clinical/Triage.kt:170` | Told "3 times" once → "Vomiting 6 times in 24 hours", AMBER | The note being saved is already in `recent`: exclude its id (or don't add `count` again) |
| **B07** | Dizziness counted one too many | `clinical/Triage.kt:218` | 2 dizzy notes → "Dizzy 3 times in 2 days", AMBER | Same: exclude the current note, or drop the `+ 1` |
| **B08** | An emergency that started SOS is saved as GREEN | `ui/screens/TellScreen.kt:209` | History, doctor page and PDF show no "Urgent" for an event that called 108 | Call `app.repo.updateTriage(noteId, triage)` right after setting the RED triage |
| **B09** | Saving a RED note after "This is wrong – change it" texts helpers again | `ui/screens/TellScreen.kt:152, 307-318` | Helpers get "…suggested getting medical help straight away" twice | Remember that helpers were told for this note (a flag in state or in the note) and don't send again |
| **B10** | Pill counting wrong for "1½" and for liquids | `meds/Scheduler.kt:192, 218` | 30 tablets, "1½" each → 19.5 left after one dose; refill alarms and "has run out" texts to helpers days early. Syrup "10 ml" counts 1 per dose | Parse "1½" as 1.5 (`replace("½", ".5")` after a digit, or a small parser); for ml forms, count ml or hide "Tablets left" |
| **B11** | "Tell them about missed medicines" OFF does nothing when SOS is ON | `help/Alerts.kt:19`, `ui/screens/HelpScreens.kt:129` | A helper who only wants emergencies gets every missed-dose, check-in, refill and help-message text | Use `it.alerts` for non-SOS types; decide separately whether help messages go to `alerts` or `sos` helpers |
| **B12** | Check-in can't be answered from its notification | `care/Care.kt:76`, `MainActivity.kt:99` | Full-screen page doesn't open → notification → Home (no buttons) → helpers texted 2 h later | Make `medlog://checkin` open the check-in page (AlertActivity CHECKIN) and put answer buttons on the notification; add a full-screen intent like the medicine alarm |
| **B13** | Widget taps skip triage | `widget/Widget.kt:216` | 6th vomiting today or "Fit / seizure" saved GREEN, no helper text | Run `DangerRules.evaluate` after saving; if RED/AMBER open MedLog on the Danger/Summary page |
| **B14** | Emergency number can be empty | `ui/screens/SettingsScreens.kt:141` | Card says "Call" and does nothing; SOS last step dials nothing | Refuse empty (keep the old value), or fall back to 112/108 |
| **B15** | Setup file loses the care plan | `data/SetupFile.kt:25` | New phone: doctors, "what I feel these days", treatments, risks, emergencies all gone | Add `plan` to the exported profile and restore it |
| **B16** | Backup gaps | `data/Backup.kt:16, 40` | Old backup → "Check the password" although the password is right; after restore: no feed calories, custom foods, nutrition targets | List columns explicitly (`INSERT INTO main.t(cols) SELECT cols`) or migrate the attached copy first; include the prefs keys `feed_info`, `custom_foods`, `kcal_target`, `protein_target` (or move them into the database); clear calendar ids after restore |
| **B17** | SOS record loses its details | `help/Sos.kt:179-180, 218` | "Help is coming" → note "SOS" without reason/location; cancel during WhatsApp → no note; the log is never shown | Keep reason and location in the service and pass them to `saveLog` everywhere; save on WhatsApp cancel; show the log on the note page |
| **B18** | "Did you fall?" stops if the screen is closed | `help/AlertActivity.kt:195-199` | Back key or rotation → countdown gone → no SOS | Run the 60-s countdown in a service (like SosService), the screen only shows it; Back should not cancel |

### 🟡 Medium

| ID | What breaks | Where | Worst-case example | Suggested fix |
|---|---|---|---|---|
| **B19** | "Yes, better" is saved as another symptom note and counted as an occurrence | `data/Repo.kt:111` | Headache once + "better" → "2 today", "2 times" in History, doctor page "Times 2", widget "(2nd today)", My health "Problems noted" | Save it with `count = 0`, or a separate kind, and exclude `better == true` notes from all counts and "last noted" |
| **B20** | My health chart numbers | `ui/screens/HealthScreen.kt:133, 146, 229` | 6M "Problems noted: In total 4" when there were 8; W/M count notes not times; water "average a day" ignores days with no water | Keep sums for totals and averages separately; use Σ `count` for symptoms; average water over all days in the period (or say "on days logged") |
| **B21** | Low BP reading always says "with dizziness"; Bluetooth RED readings show no Danger page | `ui/screens/FoodReadings.kt:474`, `ui/screens/DevicesScreen.kt` (connect callback) | 85/60 typed → "Low BP (85/60) with dizziness" | Pass `null` as problem for a plain reading (the reading rules don't need it) or use "high_bp" only; show the Danger page for RED from devices too |
| **B22** | Temperature must be in °F | `ui/screens/FoodReadings.kt:550`, `ui/screens/TellScreen.kt:455` | 38.5 → "That number looks wrong" | Accept 34–43 as °C and convert (the speech path already does), or add a °C/°F switch |
| **B23** | Weight gain called "steady" and green | `nutrition/Nutrition.kt:132` | Up 3 kg in 10 days → "Weight steady", GREEN | Flag gains too (e.g. ≥ 2 kg in a week AMBER for heart/kidney conditions); say "up" not "steady" |
| **B24** | "Nothing logged on N days" counts days before the person started | `nutrition/Nutrition.kt:136` | Day 1 of use → "Nothing logged on 13 days" | Start counting from the first note / install date |
| **B25** | "null" in "Did I take my medicines?" | `ui/screens/MedsScreens.kt:326, 328` | "Today you took: null at 8:04 AM" | Look names up in all medicines (not only active) or skip unknown ones |
| **B26** | Medicines "Today" header counts hidden doses | `ui/screens/MedsScreens.kt:103, 137` | "1 of 4 taken" with 2 cards | Count only the doses that get a card |
| **B27** | "How long" can't go back to "Always", and shows "Always" wrongly | `ui/screens/MedAddFlow.kt:337` (and `daysCount` starting empty) | 7-day course can never be made permanent; review page lies | Initialise `daysCount` from `endDate`; when 0 is chosen set `endDate = null` |
| **B28** | One-tap destructive actions | `ui/screens/MedAddFlow.kt:397`, `ui/screens/FoodReadings.kt:179`, `ui/screens/DoctorScreens.kt:189` | Shaky tap stops a heart medicine / a tube feed / removes an appointment (calendar event stays) | Confirm first (like "Remove helper"); delete the calendar event with the appointment |
| **B29** | Sunday "Your week" promises speech that never comes | `care/Care.kt:88`, `ui/screens/ReportsScreen.kt:96` | Tap → My health opens silently; `weeklySummaryText` is never called | Handle `reports?speak=1` in MainActivity and speak `weeklySummaryText` (fix its "better" filter too: it drops notes whose "What makes it better" list was answered, `ReportsScreen.kt:63`) |
| **B30** | History hides some saved events | `ui/screens/NotesScreens.kt:252` | Check-ins, "Told helpers …", helper replies, "Missed X twice", "Possible fall" never shown | Add CHECKIN, MESSAGE, FALL_ALERT to the "other" list (maybe folded under one "Family and alerts" line) |
| **B31** | "Your helpers have been sent a message" even when nothing was sent | `help/Alerts.kt:23`, `ui/screens/Common.kt:80-81, 102` | No SMS permission → page still says helpers were told; History note "Told helpers" | Return the number sent; show "Couldn't text your helpers — call them" when 0 |
| **B32** | Helper PIN protects only the Settings page | `ui/screens/SettingsScreens.kt:88` | Family → Manage → edit/remove helpers; deep links open Backup/Privacy/Helpers | Check the PIN in Helpers, HelperEdit, Messages, Backup, Privacy, Permissions (one shared guard) |
| **B33** | Helper call order can't be changed after setup | `ui/screens/HelpScreens.kt:357` | Page says order matters, no way to reorder | Add "Move up" (as in My messages) |
| **B34** | Home doesn't refresh by itself | `ui/screens/HomeScreen.kt:109` | Dose stays "Log taken" past its time; taken from the notification still shows open | Use the doses Flow (`betweenFlow`) and a 1-minute ticker |
| **B35** | Reading speed shows nothing selected | `ui/screens/SettingsScreens.kt:315` | Default 1.0 and setup's 0.8 are not in 0.55/0.7/0.85 | Use one list everywhere, include the default, pick the nearest |
| **B36** | "Delete everything" is incomplete (after B01 is fixed) | `ui/screens/SettingsScreens.kt:470-474` | Custom foods, feed info, targets, follow-ups, pairing list, family key, "fired" markers stay; fall service, quick notification and calendar events stay | Clear the whole prefs file(s), stop services, remove calendar events, cancel alarms |
| **B37** | Undo after Save undoes only part | `ui/screens/TellScreen.kt:315` | Readings/medicines/other problems told in the same sentence remain | Undo all ids returned by `saveTold` (they share a `groupId`) |
| **B38** | Emergency card counts all helpers | `ui/screens/Emergency.kt:137` | "Texts your location to 3" when only 1 has SOS on | Count `sos == true` helpers |
| **B39** | Import "nothing found" message never shows | `ui/screens/ImportScreen.kt:64` | Empty result page, no explanation | Show the message in the result layout |
| **B40** | Privacy text says the internet is used for one thing only | `ui/screens/SettingsScreens.kt:459`, `Updater.kt:84` | Daily GitHub update check on sideloaded installs | Mention the update check (or ask before checking) |
| **B41** | Birth year becomes a fake date | `ui/screens/OnboardingScreen.kt:341` | Settings shows DOB "1952-07-01" as if real | Store the year only (or mark the date as approximate) and show "Born 1952" |
| **B42** | "Worst pain" label for every kind of severity | `ui/screens/DoctorPage.kt:369` | "Worst pain 8 of 10" for itching or breathlessness | Use "Worst" + the scale's word (pain / itch / strength) |
| **B43** | Search finds short synonyms inside other words | `ui/screens/Common.kt:176` | "fitness" → Fit / seizure; "computing" → Burning urine | Match whole words (`\b`), or only when the typed word starts with the synonym |

### 🔵 Low

| ID | What breaks | Where | Example | Suggested fix |
|---|---|---|---|---|
| **B44** | Greeting at night | `ui/screens/HomeScreen.kt:121` | 2 AM → "Good morning" | Use the same day parts as `dayPart()` (night after 8 pm, before 4 am) |
| **B45** | Refill warning repeats | `meds/Scheduler.kt:223` | Twice-daily medicine: two "About 5 days left" notices the same day | Remember the last warned level per medicine |
| **B46** | Editing drops half tablets | `ui/screens/MedAddFlow.kt:147` | 9.5 → 9 | Keep one decimal |
| **B47** | One missed-dose text per medicine | `meds/Scheduler.kt:133-139` | 3 medicines at 8 am missed → 3 texts to each helper | Group doses due at the same time into one text |
| **B48** | Water goal choices differ | Settings "6/8/10" vs Food & water 1–16 | Goal 12 → Settings shows nothing selected | Use the counter in both places |

Other small things noted on the pages above (not numbered): the History header has no Back button; "Skip setup completely"
doesn't arm the alarm; as-needed "I took one" doesn't lower the pill count; "I took them all" skips the double-dose
question; appointments can be set in the past; feeds can't be edited; the microphone permission is asked but not needed;
importing a report twice duplicates it; Settings "Helper controls" uses different page names than Home; a slow search can
overwrite a newer one; a feed still open after 2 h is counted as missed before it is marked missed.

---

## Part 6. Dead settings, unused code, and things to check on a phone

### 6.1 Settings the person can change that do nothing ⚪

| Setting | Where it is offered | Proof |
|---|---|---|
| `flashAlerts` "Flash the screen for alerts" / "Missing alarms: Flash and vibrate" | Setup, Seeing and hearing | written in 3 places, read nowhere |
| `diabetic` "I have diabetes: Shows sugar readings next to meals" | Settings → Looking after you | read nowhere |
| `hidden` = "help" "Show Help" | Helper controls | nothing checks `"help" in hidden` |
| `canSeeNotes` "Let them see my notes" | Change helper | stored and exported only |
| `appLock`, `voiceEngine`, `voiceOnline`, `nearbyListening`, `bilingual` | not shown | stored in prefs, read nowhere |

### 6.2 Code that is never used ⚪

`doctor/Summary.kt` (the whole `SummaryBuilder`, the older doctor summary) · `OldMedEditScreen` in `MedsScreens.kt` ·
`CustomFoodSheet` in `FoodReadings.kt` · the onboarding `READ` step (skipped by `next()`) · `weeklySummaryText` and
`buildStats` in `ReportsScreen.kt` (B29) · `Nearby.confirmDigits` (empty, B04) · `Repo.DEFAULT_PROBLEMS` (kept only by a
`@Suppress("unused")` line).

### 6.3 ⚙ Needs a device check (Android rules, not provable from code alone)

| What | Why it might fail | Where |
|---|---|---|
| "Did you fall?" screen | A foreground service starting an activity from the background is blocked on Android 10+ unless an exemption applies; there is no full-screen notification fallback | `help/FallService.kt:58` |
| Morning check-in screen | Started with `startActivity` from the alarm receiver; same rule; the fallback notification opens Home (B12) | `care/Care.kt:75` |
| "X asks: How are you?" | Has a full-screen notification, so it should work; the direct `startActivity` may be ignored | `help/Nearby.kt:188-194` |
| SOS "Did Ravi answer?" after a call | `Sos.showScreen` from the service after the call; the SOS notification has a full-screen intent | `help/Sos.kt:192` |
| Speakerphone during SOS calls | `setCommunicationDevice` during a call placed by the dialer | `help/Calls.kt` |
| Alarm tone "rings on silent" | Uses the alarm stream; some makers mute it in Do Not Disturb | `meds/DoseAlert.kt` |

Test plan for each: lock the phone, wait for the trigger, and note whether the screen appears; then repeat with the app
swiped away from recents.

---

## Part 7. Space for your corrections and new features

Use the IDs above. For each item, write what you want instead. Example:

```
B02  → Emergencies: start empty. Only "Fainted" pre-ticked. Show a 10-second countdown before calling helpers.
B22  → Temperature: show °C by default for Tamil/Hindi users, with a °F switch.
NEW-1 → (your new feature, which page it lives on, what it shows, where its data comes from)
```

When you send your corrections, I will map each one to the exact files in Part 5 and change them one by one, with a note
of what else each change touches (Part 1.3 shows which pages share the same data).
