# MedLog handoff (detailed). Read fully before touching anything.

Repo `Ashpray94/Med-Log`, branch `claude/determined-noether-sc7tr7`, base `main`. Android, Kotlin, Compose, package `com.suryaprakash.medlog`.
Also read, in this order: `CLAUDE.md` (binding rules), `docs/DESIGN.md`, `docs/ENVIRONMENTS.md`, `docs/clinical-audit/*.md`, `PLAN.md`.
**Do not change the approach in this file. Continue it.** If you disagree, write why in this file and ask the owner; do not silently redo.

## 0. Owner and working style
- Owner: Surya (suryaprakash2094@gmail.com). Wants SHORT replies (broken English is fine), low token use, small models for execution, strong model only to plan.
- Owner's hard rules: (1) user = 90-year-old with cognitive impairment who recognises pictures (CLAUDE.md rule 0 overrides all). (2) Clinical quality = professional
  standard; patient never sees a disease name. (3) Reports in known formats (SOAP, SBAR, FHIR R4, LOINC). (4) Hospital-sign minimalism + Apple HIG.
- Agent method that worked: give each agent exact files, numbered steps, stop condition, "no exploring", "commit only your files". Haiku for execution,
  Sonnet only for clinical content/foundations. Never two agents on one file. Do not spawn big waves without asking (owner's limit is tight).

## 1. CRITICAL STATE
**Nothing here was ever compiled or run locally (no Android SDK in the authoring sandbox).** A GitHub Actions run (`release.yml`, workflow_dispatch on this branch) was
queued to be the first real compile. Check its result first: `Actions → Release`. Expect compile errors. Fix them before anything else.
The artifact `MedLog-<version>` (arm64/armv7 APKs) is uploaded by the run. If the repo secret `MEDLOG_SIGNING` is missing the APK is signed with a one-off key:
it installs fresh but CANNOT upgrade an existing prod install (signature mismatch); uninstall first or use the `dev` flavor.

## 2. What the owner asked (all in scope)
1. 4 tabs: Home (all features, no summaries) · Timeline (today, 7 accordions Early morning/Morning/Noon/Afternoon/Evening/Night/Late night; food, meds, history;
   filter) · Helpers + Messages (tools + separate chat channels helpers-only and patient↔helpers) · Settings (self mode vs helper mode).
2. Notifications characterised with responses for all permutations. 3. Ailment questions clinical-standard, conditional, each with 2-line help.
4. Sync on every change, both directions, notify the other side. 5. Shake feedback: text primary, screenshot secondary, annotation optional.
6. One toast per action. 7. Messages look like messages, alerts like alerts, fewer cards. 8. Full CRUD, visible Edit, small Details.
9. dev + prod environments. 10. Alert screen with real replies. 11. Alert sound = loud sustained middle-C piano. 12. Main info first, granular detail under "Tell more", for EVERY ailment.
13. Release build to test. 14. Sync must work between devices, any device can push, devices on the network sync on change.

## 3. Built (file map). All uncompiled unless CI says otherwise.
| Area | Files |
|---|---|
| Nav, 4 tabs, Home hero tiles (4 big + "More") | `ui/Nav.kt`, `ui/Components.kt` (BottomBar), `MainActivity.kt`, `ui/screens/HomeScreen.kt` |
| Tokens (7:1 colours, Senior sizes) | `ui/Tokens.kt` (`Hs`, `Senior`), `ContrastTest.kt` |
| Toast/announce | `ui/Toasts.kt` (`Announce.done/remote/show`, `Event`) |
| Timeline | `ui/screens/TimelineScreen.kt`, `TimelineParts.kt`, `TimelineTest.kt` (filters/day selector only in helper role) |
| Helpers tab + chat | `HelpTabScreen.kt`, `ChatParts.kt` (`ChatAdapter`: `[h]`/`[p]` text prefix = channel) |
| Settings split | `SettingsScreens.kt` (self), `HelperSettingsScreen.kt` (helper), `data/Settings.kt` (`notifyEntryAdded`) |
| Notifications table | `notify/NotifySpec.kt`, `notify/EntryNotice.kt`, `NotifySpecTest.kt`; wired in `meds/DoseAlert.kt`, `help/AlertActivity.kt`, `help/Nearby.kt`, `help/AlertActionReceiver.kt` |
| Alert sound | `help/PianoTone.kt` (synthesised C4, NOT a recording), `help/AlertSound.kt`, `PianoToneTest.kt` |
| CRUD | `ui/Forms.kt` (`RowActions`), Meds/Food/Notes/Doctor screens; reading edit in `FoodReadings.kt` |
| Shake feedback | `ui/Feedback.kt`, `ui/ShakeDetector.kt`, hook in `MainActivity.kt` |
| Flavors | `app/build.gradle.kts` (`dev` = `.dev` id, relay+updater off; `prod`), `docs/ENVIRONMENTS.md`, `.github/workflows/release.yml` (prod build) |
| Reports | `doctor/Pdf.kt` (SOAP order), `doctor/Sbar.kt`, `doctor/Fhir.kt`, buttons in `DoctorPage.kt`, `DoctorReportTest.kt` |
| Sync | `data/Sync.kt`, `data/Db.kt` (`SyncMeta`,`SyncDao`), `data/Repo.kt`, `help/FamilyChat.kt` (`received` routes `"sync"` payloads), `SyncTest.kt` |
| Clinical engine | `clinical/Catalogue.kt` (gate, minAge/maxAge, help, redFlags), `Interview.kt`, `Triage.kt` (`rules-0.3.0-unreviewed`), `Help.kt`, `CoreSizeTest.kt` |
| Clinical content | `assets/clinical/catalogue.json` (merged; v0.1.2), patches in `assets/clinical/audit/*.json`, reports `docs/clinical-audit/*.md`, tools `tools/merge_audit.py`, `tools/lint_completeness.py`, standard `assets/clinical/{standard,archetypes,dims}.json` |

## 4. SYNC (owner's top pain). Audit result and TODO
Design: each change → `Sync.local(type, op, id)` → sealed JSON over `Relay.post` (ntfy relay) with uid, at, op, payload. Receiver: `FamilyChat.received` → `Sync.receive`
→ LWW by `at` via `Sync.shouldApply`, tombstones, `applyingRemote` flag stops echo. Types: note, medicine, dose, appointment.
**Bug found and FIXED:** callers send ops "add"/"edit"/"delete" but `Sync.local` only matched "insert"/"update" → adds/edits never synced (only deletes). Now mapped.
**Still broken or missing (do these, in order; all are real sync failures):**
1. **Medicine ids differ per phone.** Dose payload carries the sender's local `medicineId`; the receiver applies it to the wrong/no medicine. Fix: send the medicine's
   sync `uid` in dose payloads; on receive map uid → local medicine id and match the dose by (medicineId, scheduledAt) (unique index) instead of inserting. Same for any
   entity referencing another (note.problemId is a catalogue id, fine).
2. **No catch-up.** A phone that was off/offline never gets missed events. Add a snapshot/handshake: on app start, on reconnect and after pairing, each phone sends
   `{"sync":"hello","since":<last applied at per peer>}`; the peer replies with all `sync_meta` rows with `updatedAt > since` (payload + tombstones). Persist `since` in settings.
3. **Retry queue is memory-only** (lost on restart) and dropped after 100. Persist queue in a Room table or file; retry on connectivity change
   (`ConnectivityManager.NetworkCallback`), app start, and every 30 s.
4. **"Devices on the network sync whenever anything changes":** today only the relay path is used. Also push over Nearby (`help/Nearby.kt`) when a peer is connected, and
   trigger a sync on network-available callback. Keep relay as fallback. Dedupe by uid+at already exists.
5. **Startup wiring:** verify `Sync.init(this)` runs in `MedLogApp.onCreate` and `Announce.onLocal` is not a second publisher (it is a deliberate no-op now).
6. **Auto-generated doses** (Scheduler) are created locally on each phone: do NOT sync inserts; sync only take/skip/snooze state (already so). Medicines must sync first (ordering).
7. **Pairing/key:** `FamilyChat.familyKey(ctx)` must be the same on all paired phones; a phone without it silently drops sync. Show a visible "Not connected" state in Helpers tab.
8. Add an instrumented or unit test: two in-memory DB instances exchanging payloads both ways, including offline queue and out-of-order delivery.
Dev flavor has relay OFF by design: to test sync between emulators, temporarily enable `Relay.enabled` for dev or use two prod installs.

## 5. CLINICAL CONTENT: state and what is left
- Engine: field/question `gate`, `minAge`, `maxAge`, `help`; `redFlags` (only `is` and `gte`). Core questions ≤5 (`core()`: when, ≤2 danger, where, severity always kept);
  everything else in `extended()` behind "Tell more"; `course` and `impact` are fixed Tell-more questions for every problem.
- Catalogue: 145 problems, ~490 fields, ~490 questions, ~1080 red flags (half RED). **AI-assisted, `reviewed:false`. A licensed clinician must sign off.**
- Completeness check: `python3 tools/lint_completeness.py` → currently **144 of 145 problems with gaps, 861 missing dims** (waivers 36, need clinician review).
  Top gaps: timing, site, relieving, aggravating, character, trigger, radiation (pain), plus substance chains on problems other than cough/vomit/stool/urine/discharge/bleeding.
  30 new substance fields/questions from `substances.json` are NOT yet tagged in `dims.json` (inflates gaps): tag them first.
  **Next step:** per body-area Sonnet agents (head; general+mind+daily; chest+tummy+toilet+women; pain+injury+skin), each writes `audit/gap_<area>.json`
  to close that area's gaps using the standard, then `python3 tools/merge_audit.py`, then lint. CI only WARNS now; make it blocking at 0 gaps.
- Known engine limits to fix: red flags lack `lt`, `isNot`, age/sex tests, no sex gate (pregnancy questions), newborn jaundice and ectopic risk inexpressible.
- Relabel problems that name diseases (e.g. "Gout attack") to symptom wording. Smell-loss "smoke/gas alarm" advice needs UI support.
- Over-triage risk: measure RED rate on normal elderly inputs; trim with clinician. Open values for clinician: sugar bands (mg/dL), fever cut-offs (°F), 5 kg weight loss,
  helplines (Tele-MANAS 14416, 112, ambulance 108), self-harm "yes"=RED, amount thresholds (teaspoon/cupful), Bristol mapping.
- UI gap vs rule 0: colour choices need colour swatches, Bristol stool types need pictures; engine cannot express `swatch`/`image` on choices yet.
- `DangerRules.VERSION` must be bumped with every rule change (currently `rules-0.3.0-unreviewed`).
- `projectile` (age limit) and `pillows` (gate) shared fields were modified by the audit: re-check users of them.

## 6. Reports (done, with gaps)
PDF = header → allergies → S (OPQRST/SOCRATES) → medications table → history → O (measured, with units) → blank A/P → ◇ patterns → footer (version, rules version).
Gaps: allergies/blood thinner print "Not recorded" when blank (cannot tell none vs not asked); `Profile.sex` is F/M only; readings have no source field (prints "patient-entered").
FHIR: Bundle with LOINC vitals (BP panel 85354-9), MedicationStatement, Condition text only (no SNOMED/ICD by rule). Never add codes without a cited certain mapping.

## 7. Senior-UX (rule 0) status and TODO
Done: Home = 4 hero tiles + More; tab bar 72dp with pill+underline; Timeline segment icons; dose screen main button 88dp + auto read-aloud + speaker button.
TODO: verify every patient screen at largest text size; Tell flow one question per screen with pictures; alert panels (SOS/fall/dose) ≥88dp main button and spoken;
no patient-side countdowns except spoken SOS cancel; replace "Why ask?" text with a speaker icon for the patient (text only in helper mode).

## 8. Other leftovers
Old `HelpScreen` composable (unreachable) in `HelpScreens.kt`; `Route.Messages` still opens the old editor; `Announce.done` is not yet called from every action (grep for gaps);
dev flavor cannot talk to Meeting Timer (package-bound bridge); `CRLF` files (`Db.kt`, `Pdf.kt`) keep CRLF, do not normalise.

## 9. First 10 actions for the next AI
1. Read the Actions run for this branch; fix compile errors (smallest edits; do not refactor).
2. Run `gradle :app:testProdDebugUnitTest`; fix failing tests (do not delete tests).
3. Sync items 1–4 in section 4 (owner's top pain). 4. Tag new ids in `dims.json`; run lint; fill gaps per section 5.
5. Install dev flavor on an emulator; walk Home → Timeline → Helpers → Settings; check rule 0 at largest text.
6. Two-device sync test (section 4.8). 7. Clinician review of `docs/clinical-audit/*.md`. 8. Make lint blocking in CI. 9. Bump versions. 10. Update this file.
