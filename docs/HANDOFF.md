# MedLog: handoff (any AI or developer can pick this up)

Branch: `claude/determined-noether-sc7tr7` (draft PR against the default branch). Android, Kotlin, Jetpack Compose,
package `com.suryaprakash.medlog`. Read `docs/DESIGN.md` and `PLAN.md` first.

**Read this before anything else: NOTHING IN THIS BRANCH HAS BEEN COMPILED OR RUN.** The authoring sandbox had no Android
SDK. All code was written and re-read by hand. First action for the next person: run `gradle :app:testProdDebugUnitTest`
(or `testDebugUnitTest` if the flavor work was not merged) and `gradle :app:installDevDebug` on an emulator, then fix the compile
errors that will almost certainly exist. Do the UI checks below on a real screen.

## 1. What the owner asked for (verbatim intent)
1. **Major revamp, 4 tabs:** Home (all features, no summaries, simple) · Timeline (today, 7 accordions: Early morning, Morning,
   Noon, Afternoon, Evening, Night, Late night; food, medicines, history; filterable, not messy) · Helpers + Messages (helper tools and
   separate chat channels: helpers-only, patient↔helpers) · Settings (mode-specific: self mode vs helper mode).
2. **Notifications/reminders/alerts re-examined:** each characterised, each with proper responses, all permutations.
3. **Ailment questions re-examined:** cough must ask "did phlegm come up?" before colour, colours need pale/dark shade, "coffee grounds" must not
   appear on every cough, every question needs a 2-line description (what it means, why asked).
4. **Sync on every change** among helpers and patient; the other side is notified ("X added an entry").
5. **Shake-to-feedback redesigned:** text is primary; screenshot secondary; annotation optional.
6. **One toast per action**, and other parties told who did what.
7. **Information hierarchy:** messages look like messages, alerts like alerts, medicines like medicines; fewer, meaningful cards.
8. **Full CRUD** on everything; visible Edit button; small Details; Apple HIG + hospital-sign design language (high contrast, minimal words).
9. (Later) **dev and prod environments**; **audit every ailment's questions against medical standards** (straight, branched, conditional flows);
   **alert screen must have real replies, not only "Open app"**; **alert sound = loud sustained middle C, Steinway-like piano**; this handoff.
Process requests: use the strongest model only to plan, hand execution to smaller models, give agents complete plans so they only execute, keep
usage low. (Lesson learned: the first wave used Sonnet for six agents and explored too much; the rest used Haiku with file-by-file plans, except the
clinical-content audit, which used Sonnet on purpose.)

## 2. What was built (by area, with files)
| Area | State | Files |
|---|---|---|
| 4-tab shell, flat Home grid | Done (uncompiled) | `ui/Nav.kt` (Route.Timeline/HelpTab/HelperSettings, `isRootTab`), `ui/Components.kt` (BottomBar), `MainActivity.kt`, `ui/screens/HomeScreen.kt` |
| Design tokens + rules | Done | `ui/Tokens.kt` (`object Hs`), `docs/DESIGN.md` section "Hospital-sign + Apple HIG rules" |
| Toast/announce hook | Done | `ui/Toasts.kt` (`Announce.done/remote/show`, `Announce.onLocal`, `Event`) |
| Timeline | Done | `ui/screens/TimelineScreen.kt`, `TimelineParts.kt` (pure logic), `TimelineTest.kt` |
| Helpers tab + chat | Done | `ui/screens/HelpTabScreen.kt`, `ChatParts.kt` (`ChatAdapter` tags channel with `[h]`/`[p]` prefix in the text) |
| Mode-specific settings | Done | `SettingsScreens.kt` (self), `HelperSettingsScreen.kt` (helper), `data/Settings.kt` (`notifyEntryAdded`) |
| Ailment questions: help text, gates | Done for cough; generic engine + audit: see section 4 | `clinical/Help.kt`, `clinical/Interview.kt`, `TellScreen.kt` |
| Notification table | Done | `notify/NotifySpec.kt` (types × tier × audience × responses × state resolver), `notify/EntryNotice.kt`, `NotifySpecTest.kt`, wired into `meds/DoseAlert.kt`, `help/AlertActivity.kt` |
| Sync | Engine done; hook calls being added | `data/Sync.kt`, `data/Db.kt` (`SyncMeta`, `SyncDao`), `data/Repo.kt`, `help/FamilyChat.kt` |
| CRUD and row actions | Done except reading edit (being fixed) | `ui/Forms.kt` (`RowActions`, `rememberLeaveGuard`), Meds/Food/Notes/Doctor screens |
| Shake feedback | Done | `ui/Feedback.kt`, `ui/ShakeDetector.kt`, `FeedbackTest.kt`, hook in `MainActivity.kt` |
| Dev/prod flavors | See section 5 | `app/build.gradle.kts`, `docs/ENVIRONMENTS.md` |
| Alert replies | See section 5 | `help/Nearby.kt`, `help/AlertActionReceiver.kt`, `help/AlertActivity.kt` |
| Piano alert tone | See section 5 | `help/PianoTone.kt`, `help/AlertSound.kt`, `PianoToneTest.kt` |

## 3. Decisions and honest limits
- Patient never sees a disease name (PLAN.md section 0). Danger rules are deterministic. **All clinical content is AI-assisted and NOT clinician-reviewed.**
  `catalogue.json` keeps `"reviewed": false`. A licensed clinician must approve `docs/clinical-audit/*.md` before release.
- The piano sound is a **synthesised approximation** (additive synthesis, inharmonic partials, two detuned strings, hammer noise). No Steinway recording was
  used: no sample or licence was available. If a licensed sample is wanted, drop it in `res/raw` and play it from `AlertSound.play()`.
- Sync = sealed events plus entry payloads over the existing Nearby/relay transport; last-writer-wins by timestamp; deletes are tombstones.
  The dev flavor disables the relay and the updater.
- The Meeting Timer bridge/Calendar integration is tied to the package name, so the dev flavor does not talk to Meeting Timer (known limitation).
- "Shake to feedback": there was no existing implementation in the repo (grep found none); it was created new.
- Reported coffee-ground bug: in the data, `coffeeGround` was only attached to `vomiting` and `vomit_blood`, not `cough`. A test now asserts cough never asks it.
  If the owner still sees it on a cough, find which screen path injects it (likely the parser inferring facts from speech).

## 4. Ailment audit (how it works)
- Engine: catalogue fields/questions accept optional `gate`, `minAge`, `maxAge`, `help`; top-level `redFlags` are evaluated by `DangerRules`
  (`clinical/Triage.kt`, rules version `rules-0.2.0-unreviewed`).
- Content: one patch per body area in `app/src/main/assets/clinical/audit/*.json`, one report per area in `docs/clinical-audit/*.md`
  (problem → standard used → flow type → changes → open questions for a clinician).
- Merge: `python3 tools/merge_audit.py` applies the patches to `catalogue.json` and validates references and gate ordering;
  `--check` validates only.

## 5. Status of the last wave (update this table when you finish each item)
Items launched last, in parallel: dev/prod flavors; alert notification buttons + generic alert panel; piano tone; Sync hook calls + reading edit/delete;
clinical engine; four clinical-audit content patches. Check `git log` and the tables above; anything not committed is not done.

## 6. Do next (in order)
1. Build and run unit tests; fix compile errors. Install the dev flavor on an emulator and click through all 4 tabs.
2. Run `python3 tools/merge_audit.py` (merge audit patches), fix validation errors, rebuild, run `InterviewTest`/`TriageTest`.
3. Clinician review of `docs/clinical-audit/*.md`; then set `reviewed` true and bump `DangerRules.VERSION`.
4. Verify on two real phones: add an entry on one, see the toast + notification + synced row on the other (including offline then online).
5. Design pass: confirm cards only wrap actual items (a medicine, a message, an alert); contrast ≥7:1; body ≥18sp; touch ≥48dp.
6. Leftovers: old `HelpScreen` composable in `HelpScreens.kt` is unreachable (delete); `Route.Messages` still opens the old messages editor (Settings links to it).

## 7. Working agreement that worked
Give each sub-agent: exact files it may edit, numbered steps with signatures, a stop condition, "no exploring", and "commit only your files".
Use Haiku for mechanical execution, Sonnet only where judgement matters (clinical content, foundations). Do not run two agents on one file.
