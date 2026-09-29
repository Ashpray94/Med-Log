# Persona test report (after Phase A / W2, before W3)

Branch base: `claude/busy-lamport-3qr5wl` at `fb4f0da`. Tests: `app/src/test/java/com/suryaprakash/medlog/PersonaKamalaTest.kt` and `PersonaRaviTest.kt`.
No app code was changed. Walk-throughs are from reading the code (no emulator); file:line refer to this branch.

**Result of `testDebugUnitTest`: 142 tests, 0 failures, 8 skipped.** The baseline of 103 still passes. The 39 new tests are 31 PASS,
6 IGNORED-FAILS (real findings, expectation kept) and 2 PENDING-W3.

Legend: PASS = runs and passes. IGNORED-FAILS = the test is written as the plan says, fails when un-ignored (I checked each one), and carries `@Ignore("FAILS: ...")` only to keep the build green.
PENDING-W3 = the Personal limits page is not on this branch.

## 1. Scenario table

### Persona 1: Kamala (67, oesophageal cancer, chemotherapy, no blood thinner)

| ID | Test name (PersonaKamalaTest) | Result | Evidence |
|---|---|---|---|
| K1 | `k1_vomitingThreeIsGreenFiveIsAmberAndNoteIsNotDoubleCounted` | PASS | 3 and 4 vomits GREEN; 5 and 6 (the "5 or more" tap) AMBER "Vomiting 5 times in 24 hours during cancer treatment: call your doctor"; earlier 1 + this 3 = GREEN (B06 not double-counted); earlier 2 + this 3 = AMBER; a note 30 h old is not counted. |
| K2 | `k2_vomitingBloodIsRedAndNoWaterNoUrineIsRed` | PASS | blood, `vomit_blood`, coffee grounds all RED; no water + no urine at 67 RED "dehydration risk"; only one of the two is AMBER. (But see K11: the two questions are not asked from taps.) |
| K3a | `k3_chillsAfterVomitingHundredPointTwoIsRedUntilHelperRaisesTheLimit` | PASS | 100.2 F RED "Fever during cancer treatment"; with helper `temp` amber 102 / red 102: 100.2 GREEN, 102.4 RED "above the limit set for you"; 104 always RED. |
| K3b | `k3_celsiusTypedIsTurnedIntoFahrenheit` | PASS | 38.9 typed becomes 102.0; 38.0 becomes 100.4; the interview accepts "38.9" and rejects "60". |
| K3c | `k3_helperSetsOnlyTheAmberFeverLine_cancerRedLineStillApplies` | PASS (documents behaviour) | helper sets only amberHigh 102: 100.2 is still RED because the cancer red line (100.0) is not replaced. See B71. |
| K4a | `k4_severeCoughOneWeekIsNotRed_coughBloodProblemIsRed_threeWeeksIsAmber` | PASS | cough 8/10, 1 week GREEN; problem `cough_blood` RED; cough 3 weeks AMBER. |
| K4b | `k4_coughWithBloodAnsweredYesIsRed` | IGNORED-FAILS | expected RED, actual GREEN. Cough + "Any blood when you cough?" = Yes is not a rule. Cause `Triage.kt:125` (blood is RED only for vomiting/nausea). B52. |
| K5a | `k5_mildConfusionDuringFeverIsRedConfusion` | PASS | fever + confusion at 100.6 RED "Fever with confusion"; confusion alone RED "Confusion". |
| K5b | `k5_helpersAreToldOnceNotTwice` | IGNORED-FAILS (code read) | B09 is still open: `TellScreen.kt:153` persist() texts helpers on every RED persist while phase != DANGER; "This is wrong – change it" (`TellScreen.kt:355`) sets SUMMARY; Save (`TellScreen.kt:326`) persists again, RED again, second text. Not runnable on the JVM (it lives in a composable). |
| K6 | `k6_hardToBreatheIsNotAnAutomaticEmergencyUnlessBreathingRiskChosen` | PASS | `emergenciesFor(["falls","alone"], ["Cancer"])` = fall, fainted; no `breathless`. With risk `breathing` it is added (B02). |
| K7a | `k7_spo2At67` | PASS | 88 with no limit AMBER "Low oxygen (88%): please call your doctor" + `needsLimit = "spo2"`; 92 GREEN + needsLimit; 89 AMBER. |
| K7b | `k7_spo2HelpersRedLineAt88IsRedAt88` | IGNORED-FAILS | expected RED, actual AMBER: with red line 88 a reading of 88 is not red. Cause `Triage.kt:189` uses `<`. B55. |
| K7c | `k7_spo2HelpersRedLineAt88_87IsRedAndNoNeedsLimitLine` | PASS | 87 RED, no "no limit" line; 89 AMBER. |
| K8 | `k8_bp168over96` | PASS | 168/96 no limit GREEN + `needsLimit = "bp"`; helper 160/100 amber, 180/110 red: AMBER, no needs-limit line; 182 systolic RED; 112 diastolic RED; 150/90 GREEN. |
| K9 | (walk-through, section 2) | DONE | see section 2 |
| K10a | `k10_shiveringAfterVomitingAsksForTemperatureInTheCoreQuestions` | PASS | chills core = `when`, `q_temp` (TEMP, danger), `strength`: 3 questions, temperature is asked. |
| K10b | `k10_vomitingThenShiveringWithTemperatureIsRed` | PASS | vomiting 3 GREEN; chills 100.2 RED. |
| K10c | `k10_shiveringOnChemoWithNoTemperatureIsNotGreen` | IGNORED-FAILS | shivering, no temperature (Skip) is GREEN "Saved." Cause `Triage.kt:151-160`. B56. |
| K11a | `k11_vomitingInterviewListAndCount` | PASS | exact questions below; 4 (<= 6). |
| K11b | `k11_vomitingCoreAsksWaterAndUrine` | IGNORED-FAILS | "Can you keep water down?" and "Have you passed urine in the last 8 hours?" are not in the core. Cause `Interview.kt:158` (`ALWAYS_CORE`) and the priority >= 85 filter. B53. |
| K12a | `k12_coughInterviewListAndCount` | PASS | exact questions below; 3 (<= 6). |
| K12b | `k12_coughCoreAsksHowManyWeeks` | IGNORED-FAILS | "For how many weeks?" is only in "tell more", so "cough 3 weeks" cannot fire from the core taps. `Interview.kt:158`. B54. |
| K13a | `k13_countTapsMatchTheRules` | PASS | tiles are Once/2/3/4/"5 or more"(=6); 4 GREEN, "5 or more" AMBER. |
| K13b | `k13_severityAloneNeverMakesCoughVomitingOrChillsRed` | PASS | severity 10 alone never RED. |
| - | `kamalaIsInCancerCare` | PASS | `cancerCareOf("Cancer", ["Chemotherapy"])` true; false without treatment, false for other conditions. |

**Kamala's exact core questions (the count is 3 to 4, all <= 6):**

- Vomiting (4): 1 "When did it start?"; 2 "Was there any blood?"; 3 "How many times today?"; 4 "How bad is it?". Then "Can you tell me a little more? It helps your doctor." (not counted). In "more": keep water down, urine in 8 hours, and more.
- Cough (3): 1 "When did it start?"; 2 "Any blood when you cough?"; 3 "How bad is it?". Weeks is only in "more".
- Shivering (3): 1 "When did it start?"; 2 "Did you check your temperature? Say the number."; 3 "How strong is it? From 0 to 10."

### Persona 2: Ravi (34, helper)

| ID | Test name (PersonaRaviTest) | Result | Evidence |
|---|---|---|---|
| R1 | `r1_shakeOpensReportOnHelperPhoneButNotOnThePersonsPhone` | PASS | `Settings(role="helper").shakeOn` true; `role="self"` false; a fresh install false; an explicit setting wins on both. |
| R1 | `r1_twoHardJoltsInASecondIsTheShakeAndAWalkingBumpIsNot` | PASS | two peaks over 2.7 g in 1 s detected; one bump and a walk (1.5 g) never. |
| R2 | `r2_reportWithNoTokenIsQueuedSurvivesRestartAndIsSentOnceATokenExists` | PASS | save, new `FeedbackStore` over the same folder: pending = 1 (QUEUED, issue 0, picture kept); with a fake GitHub the send sets SENT #12 and pending becomes empty. |
| R2 | `r2_noInternetKeepsTheReportQueuedAndASecondSendDoesNotCreateTwoIssues` | PASS | offline throws `IOException` (the worker retries); a second send does not create a second issue. |
| R2 | `r2_newestReportIsFirstInMyReports` | PASS | ordering. |
| R3 | `r3_publicRepoGetsTextOnlyNeverTheScreenshot` | PASS | no PUT, no picture bytes in any request, body says "Screenshot not sent". |
| R3 | `r3_repoThatCannotBeCheckedCountsAsPublic` | PASS | a 404 on the repo check means no upload. |
| R3 | `r3_privateRepoGetsThePictureLinkInTheIssue` | PASS | image link in the issue body. |
| R4 | `r4_closedIssueShowsFixedAndStillBrokenReopensWithTheNewNote` | PASS | "Sent · #12 Open" > closed "Fixed · #12 closed" > "Still broken" PATCHes state open and comments "Still broken on the phone.\n\n<note>" > SENT > closed again > "It works now" > "Fixed and checked · #12", stays VERIFIED. |
| R4 | `r4_stillBrokenWithNoWordsStillReopens`, `r4_everyStatusHasPlainWords` | PASS | |
| R5 (rules) | `r5_limitsSavedByTheHelperAreUsedByTheRulesAtOnce` | PASS | spo2 91 at 70: GREEN + needs-limit; after a helper saves amber 93 in the care plan (JSON round trip = what `Repo.person()` reads) it is AMBER and the line is gone. |
| R5 (rules) | `r5_aPlanWithoutLimitsStillLoadsAndAHelperWhoSavesNothingChangesNothing` | PASS | old plans and garbage load as no limits. |
| R5 | `r5_setupLimitsStepCanBeSkippedAndFoundAgainInHelperControls` | PENDING-W3 | page not on this branch |
| R5 | `r5_limitsPageWalkThrough` | PENDING-W3 | page not on this branch |
| R6 | (walk-through, section 3) | DONE | |

## 2. Kamala's walk-through (from the code)

Kamala's set-up is assumed done: conditions "Cancer", treatment "Chemotherapy", one helper (Ravi) with SOS on, SMS and location allowed. Screen names are the on-screen titles.

### 2.1 "Vomiting 3 times" (8 taps, 7 screens)

| # | Page | She sees | Tap |
|---|---|---|---|
| 0 | Home | Greeting "Good morning, Kamala"; a "Me / I help someone" switch at the top (`HomeScreen.kt:129`); the big teal card "How are you feeling? / Tap to choose" (`HomeScreen.kt:242-244`); Recent tiles; "More" tiles; bottom bar Home / SOS / Family | |
| 1 | Home | | tap the big card (`HeroTell`) |
| 2 | "How are you feeling?" (`PickProblem`, `Common.kt:127`) | search box, "You told me before" tiles (from her notes), "Common for you", then the body-area lists. If "Vomiting" is not in the first tiles she must scroll or type: +2 to +8 taps | tap **Vomiting** |
| 3 | Vomiting, "Question 1 of 4": **"When did it start?"** | 5 big buttons: Just now / Earlier today / Yesterday / A few days ago / A week or more | tap "Earlier today" (moves on by itself after 0.45 s, `TellScreen.kt:195`) |
| 4 | "Question 2 of 4": **"Was there any blood?"** | Yes / No | tap No |
| 5 | "Question 3 of 4": **"How many times today?"** | Once / 2 times / 3 times / 4 times / 5 or more | tap "3 times" |
| 6 | "Question 4 of 4": **"How bad is it?"** | 6 face tiles (A little ... Worst ever) with "Or tap the exact number" and a 0-10 pad | tap a face |
| 7 | **"Can you tell me a little more? It helps your doctor."** | Yes / No | tap No (this schedules a follow-up reminder, `TellScreen.kt:181`) |
| 8 | **"Here's what I noted"** | Vomiting, Started: Earlier today, Times: 3, How bad: ..., buttons Save, Tell a little more, Change an answer | tap **Save** |
| | Home | a green "Saved." bar with Undo, spoken "Saved. Get well soon." | |

Rules result: 3 in 24 h, GREEN. Nothing is sent to anyone.

### 2.2 "Shivering afterwards" (9 taps and 2 screens, then the danger page)

Home > big card (1) > **Shivering** (2) > "When did it start?" (3) > **"Did you check your temperature? Say the number."** with a number pad "°F or °C": `1`,`0`,`0`,`.`,`2` (5 taps) and **Done** (1) = 9 taps. The moment the temperature is answered the app saves, judges RED (100.2 F, cancer default 100.0), **texts the helpers and alerts paired helper phones at once** (`TellScreen.kt:153-156`) and shows **"Get help now"**: "This could be serious. Get help now." A huge red card **"Call 108 - Ambulance · free · any time"**, the dark card "Alert my family" (hold 2 s), her helpers' faces, "WHY: Fever during cancer treatment (100.2 °F)", and a quiet "This is wrong – change it". The strength question is never asked (RED cuts the interview). To leave she uses the bottom bar Home. No Save is needed (the note is already saved, and marked RED).
Total 9 taps for a note that ends in RED. If she types 38 for Celsius she gets "38 °F" replaced by 100.4 (`DangerRules.toFahrenheit`) and is RED as well.

If she has no thermometer she must find **Skip** (bottom of the screen); the note is then GREEN "Saved." (B56).

### 2.3 A cough note (7 taps)

Home > big card (1) > **Cough** (2) > When (3) > **"Any blood when you cough?"** No (4) > "How bad is it?" face (5) > "tell more?" No (6) > **Save** (7): 7 taps. Rule result GREEN. "Cough 3 weeks or more" AMBER never fires because "For how many weeks?" is only in "tell more" (B54). **If she taps Yes to blood: GREEN "Saved."** (B52).

### 2.4 Her helper being told

- Only a **RED** note tells the helper: an SMS (to every helper with "alerts" or "sos", `Alerts.kt:17`) and a Bluetooth/relay alert that rings on a paired helper phone. The SMS text is `Wording.seeDoctorNow`: *MedLog: Kamala noted "Shivering". MedLog suggested getting medical help straight away. Please call or go to them.* It says nothing about the temperature or the vomiting (B60).
- A **GREEN** note (vomiting 3, cough) and an **AMBER** note ("call your doctor today") tell nobody. `Alerts.amberToHelpers` has no caller (B59).
- The other way is the Family tab: tap a message tile ("I don't feel well", `HelpScreens.kt:209`), which sends it to everyone with **one tap and no confirmation**.

### 2.5 Words, buttons and behaviours that could confuse a mildly confused 67-year-old

| Where | Text or behaviour | Why it can confuse |
|---|---|---|
| Home `HomeScreen.kt:129` (`PersonaSwitch`, `HelpScreens.kt:600`) | "Me" / "I help someone" switch at the top of her own Home | A wrong tap opens "Switch to helping? This phone will ring when the person you help needs you." with "Switch to helping" / "Keep it as it is". If confirmed, the phone becomes a helper phone: Home changes and **the bottom bar with SOS disappears** (`Components.kt:244`, `hasNav = ... role != "helper"`). B61. |
| Home Recent tiles `HomeScreen.kt:175-183` | "Vomiting, 2 today" tile | One tap starts a new note at once (`TellScreen.kt:243`, `begin`), and the note is saved before any question. Backing out leaves it (B62). |
| Pick page `Common.kt:154,158,161` | "You told me before", "Common for you", "Others you may have", then "Head, face and mind", "Chest, stomach, back and arms", "Toilet, legs and feet" | Many tiles; "Shivering" (chills) is under "Common" only if it was chosen in setup. |
| Question header `TellScreen.kt:285` | "Question 2 of 4" | Then an extra un-numbered "Can you tell me a little more?" appears, so 4 becomes 5. |
| Bottom of every question `TellScreen.kt:442-443` | **"Skip"** and **"Finish"** | "Finish" ends the questions early (goes to "Here's what I noted"); then there is another "Save". Two finish-like buttons. |
| Chills `Interview.kt:72` | "How strong is it? From 0 to 10." with tiles "None / A little / Some / Strong / Very strong / Worst ever" | An odd question for shivering. |
| Temperature `q_temp` | "Did you check your temperature? **Say** the number." | The screen is a tap pad, not a microphone; the unit shows "°F or °C" (`TellScreen.kt:512`); "That number looks wrong. Please check." (`TellScreen.kt:623`) if she types e.g. 98 without the point, 9.8, or 1002. |
| Count `Interview.kt:39` | tile "5 or more" | Stored as **6** and shown as "Times: 6" in "Here's what I noted" and on the doctor page (`TellScreen.kt:574`). B74. |
| Summary | "Tell a little more", "Change an answer" (`TellScreen.kt:336,340`) | "Change an answer" starts the questions again from question 1. |
| Danger page `Common.kt:104` | "This is wrong – change it" | Unclear who is wrong (the app or her answer). After it, Save texts the helpers again (B09). |
| Danger page `Common.kt:82-92` | "Call 108 - Ambulance · free · any time" is the largest thing; spoken "Call 108 now." | For a shiver at 100.2 the oncology team's number is the right first call; it is not shown (B58). |
| Emergency page `Emergency.kt:137` | "Texts your location to 1, then calls each one" and "hold for 2 seconds" | The hold is good; it needs the person to keep holding, which a tremor or a confused person may not. |
| Family tab `HelpScreens.kt:209` | tiles "Send: I don't feel well" / "Send: Please come" | One tap sends an SMS to all helpers. |
| Family tab `HelpScreens.kt:235` | rows "Call <name>" | One tap dials (ACTION_CALL). |
| Setup words | "Which of these are emergencies for you?", "Do any of these apply to you?" | Assumes she understands that ticking a box means her helpers get called. |

### 2.6 Dangers (mistaken calls or texts, and missed real dangers)

Wrongly texting or calling:

1. A tile tap on a problem marked RED (Vomiting blood, Fainted, Coughing blood, ...) or on one of her own emergencies is enough to alert helpers before any question. The plan emergencies do have a 10 s "Cancel - I'm OK" countdown (`TellScreen.kt:223-226,348`); the RED problems do **not**: `begin()` > `persist()` > `Alerts.dangerToHelpers` (`TellScreen.kt:153-156`). B62.
2. A single "Yes" to "Was there any blood?" (or any RED answer) texts every helper before she sees the result. The text cannot be recalled (B09 doubles it).
3. The RED page's 108 card dials on one tap with no confirm (`Emergency.kt:110`, `Calls.call` uses `ACTION_CALL` when the permission is held). Intended, but combined with over-triage (chemo fever) it makes false calls likely. B58.
4. The "I help someone" switch (B61).
5. Family tiles and "Call <name>" rows: one tap (`HelpScreens.kt:209`).

Real danger that can be missed:

1. Vomiting: "keep water down" and "urine" are only in "tell more" (B53), so the RED dehydration rule can only be reached if she says yes to "tell more".
2. Cough with blood answered Yes stays GREEN (B52); "cough 3 weeks" never asked (B54).
3. Shivering with no temperature is GREEN (B56).
4. A phantom note (opened and abandoned) counts as one vomit in her 24 h total (`Triage.kt` count `?: 1`, `Repo.kt:58`), so a mis-tap can push her to AMBER "more than 4"; the reverse is not possible.
5. GREEN and AMBER notes do not reach the helper, so Ravi cannot see a pattern (5 vomits, AMBER, "call your doctor") that Kamala herself may ignore (B59).
6. The AMBER page's "Call <doctor>" picks the Stomach or Family doctor, not her cancer doctor (B58).

## 3. Ravi's walk-through (helper, 34, new to the app)

### 3.1 First 10 minutes on his own (helper) phone

1. Install and open. Welcome > **Start** (or "Skip", which opens "Skip setup? ... Skip setup completely / Use a setup file").
2. **"How will you use MedLog?" "Tap one or both."** with "My health" and "I help someone" (`OnboardingScreen.kt:210`). The hint "Setting this up for a parent? Choose My health." is the opposite of what he wants for his own phone and confusing for a helper who will hold her data. He picks "I help someone".
3. That sets role helper, lands on **"MedLog Helper"** with "Connect to their phone. Hold both phones close. It takes a minute, once." and a **Connect** button, then the **"Connect phones"** page (`HelpScreens.kt:411`, "Hold this phone next to the other person's phone"). He needs Bluetooth, nearby, notification and battery permissions; a card "To hear alerts, MedLog needs: ..." with **Allow** and "Let MedLog run in the background..." with **Allow** appears at the top of the helper home. Nothing explains why or that Kamala's phone must do its part.
4. After pairing, the home shows the person's name as the title, "The latest from Kamala, and your reply.", "No messages from Kamala yet. You'll hear an alarm when one comes." (`HelpScreens.kt:494`), **Ask Kamala how they are**, "Other helpers" (needs "Kamala's phone has the new MedLog and is online"), "Help one more person".
5. A **round gear icon** at the top right opens "Settings" > "Connection" (Nearby: Bluetooth, Far away: Connected), "Problems with MedLog" > **Report a problem** / **My reports**, and the toggle **"Shake to report a problem"** (already on for a helper). There is **no Personal limits page yet (W3)**: the "limits" scenarios in R5 are PENDING-W3. When it lands the walk-through needs: where it is in Helper controls (PIN), the words used for amber / red, and whether a fever limit sets both lines (B71).
6. What he would not understand: "Nearby / Far away", "Text messages only", "Through the internet, encrypted", "Other helpers", the top switch "Me / I help someone" (which he could tap by mistake; it shows a confirmation), and that "I'm coming / In 5 min / I'll call" replies go to Kamala and other helpers.

### 3.2 On Kamala's phone (set-up with real data)

Set-up is 17 counted steps (`OnboardingScreen.kt:139`) plus doctors, helpers, emergencies, check-in, permissions and the widget. He picks "My health" as the hint says, then "Setting this up for someone else? Write their name." He must know her conditions, treatments, doctors and which "emergencies" to tick. A helper-set "Personal limits" step will come here (W3).
On her phone the shake is **off** by default (R1 PASS), because her hands may tremble. He finds **Report a problem** and **My reports** in Settings > "More" (`SettingsScreens.kt:228,230`), with a toggle whose hint says "Can start by accident if your hands shake."

### 3.3 "Report a problem" and "My reports"

- Shake twice (or Settings > Report a problem). The app takes a picture of the current page and opens **"Report a problem"**: "Draw with your finger to circle the problem", Undo/Clear, "What kind of problem?" (Bug / Wrong information / Hard to use / Idea), "What happened?" with **Speak**, the hint "The picture can show health details. It goes only to the MedLog team's private tracker." (`FeedbackScreen.kt:100`), **Send**.
- After Send: "Saved on this phone. It is sent when there is internet." or, if the build has no token, "Saved on this phone. It will be sent when the app is set up to send reports." (`FeedbackScreen.kt:57-58`). Two buttons: **See my reports**, **Done**.
- **My reports** lists each report with its words, date, and one status line: "Waiting to send", "Sent · #12 Open", "Fixed · #12 closed", "Fixed and checked · #12"; a closed one has **It works now** / **Still broken** (asks "What is still wrong? (You can leave this empty.)"). A queued one has **Send now** only when the build is configured.
- Not understood: "#12 Open / closed" is GitHub jargon; "Fixed" is shown for any closed issue, including "won't fix" (B67); with a build that has no token nothing can ever send and no button explains it (B65); the hint says "private tracker" even when the tracker is public and the picture was not sent (B66); the screenshot on a helper phone can show Kamala's messages and there is no way to send only the words (B66).

## 4. New findings, ranked (numbering continues from B52)

Severity marks: 🔴 critical, 🟠 high, 🟡 medium, 🔵 low.

| ID | Sev | Finding | Where | Suggested fix |
|---|---|---|---|---|
| **B52** | 🟠 | "Any blood when you cough?" answered **Yes** on Cough is GREEN. Plan scenario 4 says cough with blood is RED. Test `k4_coughWithBloodAnsweredYesIsRed`. | `clinical/Triage.kt:125` (`problemId in setOf("vomiting","nausea")`) | Add `"cough"` (and `"sputum"`-type problems): `if (yes(facts,"blood") && problemId == "cough") red += "Coughing blood"`. |
| **B53** | 🟠 | For vomiting the dehydration questions are not core ("Can you keep water down?" priority 80, "urine in 8 hours" 70). The RED rule "no water + no urine, age >= 65" and the AMBER "cannot keep water down" are reached only if she says yes to "tell more". Test `k11_vomitingCoreAsksWaterAndUrine`. | `clinical/Interview.kt:158` (`ALWAYS_CORE`), `:163` (`room`) | `ALWAYS_CORE += "vomiting" to listOf("q_blood_vomit","q_keepwater","q_urine")`. Core then = when, blood, keepwater, urine, count, severity = 6 (the limit). Keep blood in the list, because `room = 2 - must.size` drops the "rest" questions once `must` has two. Loose motions the same (`q_keepwater`, `q_urine`). |
| **B54** | 🟠 | "For how many weeks?" is only in "tell more", so "cough 3 weeks or more" (AMBER) can never fire from the core taps; "A week or more" in "When did it start?" sets no `weeks`. Test `k12_coughCoreAsksHowManyWeeks`. | `clinical/Interview.kt:158`; rule `clinical/Triage.kt:265` | `ALWAYS_CORE += "cough" to listOf("q_weeks")` (core becomes 4), or derive `weeks = 1` from `started == "a week or more"` and ask weeks only then. |
| **B56** | 🟠 | Shivering or fever on chemotherapy with **no temperature** (she tapped Skip, or has no thermometer) is GREEN "Saved." Test `k10_shiveringOnChemoWithNoTemperatureIsNotGreen`. | `clinical/Triage.kt:151-160` | When `person.cancerCare` and problem in (`chills`, `fever`) and no temperature: AMBER "Check your temperature. If you cannot, call your cancer doctor today." |
| **B57** | 🟠 | **B09 still open** (helpers texted twice after "This is wrong – change it" + Save). Test `k5_helpersAreToldOnceNotTwice` (ignored, code read). | `ui/screens/TellScreen.kt:153,326,355` | Keep `toldHelpers` in `remember` (or in the note) and send only once per note; or only send when the level rises. |
| **B58** | 🟠 | The pages for a chemo patient point to the wrong help. (a) The AMBER summary button `DoctorCallButton(pr.dept)` finds the doctor by department: vomiting = Gastroenterology = "Stomach", else "Family doctor", never her "Cancer" doctor. (b) The RED page has no doctor button at all, and for chemo fever "Call 108" is the biggest and one-tap. | `data/CarePlan.kt:37` (`doctorFor`); `ui/screens/Common.kt:82-92,110-118` | In `doctorFor`/`DoctorCallButton`, when `cancerCare` prefer speciality "Cancer"; on the RED page for cancer-care fever, show "Call <cancer doctor>" above 108 with words like "Call your cancer team now" (or ask the oncologist's number in setup). |
| **B59** | 🟠 | Helpers are never told about AMBER notes ("Please call your doctor today", vomiting > 4 in cancer care, no motion > 2 days) or about any GREEN note. `Alerts.amberToHelpers` and `Wording.seeDoctorToday` exist but have **no caller**. | `help/Alerts.kt:42`; the AMBER path in `TellScreen.kt:persist()` | Call `Alerts.amberToHelpers` once per note when the level first becomes AMBER (same "told once" flag as B57); add a per-helper switch. Phase B "sharing both ways" will also carry notes. |
| **B61** | 🟠 | The "Me / I help someone" switch is at the top of a person's own Home. Two taps turn her phone into a helper phone, and **the bottom bar with the red SOS button disappears** for helper role. | `ui/screens/HomeScreen.kt:129`, `ui/screens/HelpScreens.kt:600`, `ui/Components.kt:239` | Remove the switch from Home; keep it in Settings behind the helper PIN (Ravi's helper home keeps it). |
| **B62** | 🟠 | Opening a problem creates the note **immediately** and backing out keeps it. (a) A RED problem tile (or one RED answer) texts helpers at once with no cancel window (the plan emergencies have a 10 s countdown, RED problems do not). (b) An abandoned "Vomiting" note counts as 1 vomit in the 24 h total (`count ?: 1`), inflating the "more than 4" rule. | `TellScreen.kt:206-230` (`begin`), `:153` (`persist`), `clinical/Triage.kt` fluids block, `data/Repo.kt:58` | (a) Give RED-by-tile the same 10 s "Cancel – I'm OK" countdown before texting. (b) On Back from the first question, delete a note with no answers; and do not count a vomiting note with no `count` and no answers. |
| **B55** | 🟡 | A helper's low red line does not include the reading equal to it (SpO2 red line 88, reading 88 = AMBER). Test `k7_spo2HelpersRedLineAt88IsRedAt88`. Same shape for pulse and sugar low lines. | `clinical/Triage.kt:189,216,176` | Use `<=` for low red lines (and for amber low lines if the page says "88 or below"), and word the Personal limits page as "at or below". |
| **B60** | 🟡 | The SMS to a helper for RED says only *noted "Shivering". MedLog suggested getting medical help straight away.* No reason, no number, no time. Ravi cannot judge how urgent it is (chemo fever vs a fall). | `help/Wording.kt:20`, `help/Alerts.kt:33-37` | Add the first reason: *"...noted "Shivering" (fever 100.2 °F during cancer treatment)"*. Keep the tone check in `Wording.check`. |
| **B63** | 🟡 | Family tiles ("Send: I don't feel well") and "Call <name>" rows act on **one tap** with no confirm or undo. | `ui/screens/HelpScreens.kt:209,235` | Require a second tap ("Send to family?") or a short "Sending in 3... Cancel". |
| **B65** | 🟡 | Feedback is only sent if the build has a compile-time token. Without it a report says "It will be sent when the app is set up to send reports." and stays in "Waiting to send" forever; no button. | `feedback/FeedbackScreen.kt:57-58`, `feedback/FeedbackSender.kt` (`configured`) | Show "This build cannot send reports" on My reports; offer "Share the report" (Android share sheet with the text and picture) as a fallback; add a CI check that release builds have the token. |
| **B66** | 🟡 | The hint "It goes only to the MedLog team's private tracker" is printed even if the repo is public (words only are sent, picture withheld), and there is no way to tick "send without the picture" on a helper phone that shows Kamala's messages. | `feedback/FeedbackScreen.kt:100,200` | Add a "Send the picture" toggle (default on only if the repo check says private) and reword the hint after the check. |
| **B71** | 🟡 | A helper who sets only the amber fever line (102) leaves the cancer red line (100.0) in force: 100.2 stays RED (test `k3_helperSetsOnlyTheAmberFeverLine_cancerRedLineStillApplies`). The plan's "fever limit 102" must set both lines. | `clinical/Triage.kt:150-159` (`effective` per line) | On the W3 page ask one number for fever ("The doctor's limit for this person") and save it as both `amberHigh` and `redHigh`; show the rule that replaces it. |
| **B74** | 🟡 | The tile "5 or more" is stored as **6** and displayed as "Times: 6" in the summary and on the doctor page. | `clinical/Interview.kt:39`, `ui/screens/TellScreen.kt:574` | Store 5 (the rule is `> 4`, so 5 and "5 or more" behave the same) and show "5 or more" for 5; or keep the label in the fact. |
| **B67** | 🔵 | My reports shows "Sent · #12 Open" and "Fixed · #12 closed" (GitHub jargon), and any closed issue reads "Fixed" even when closed as not planned. | `feedback/FeedbackScreen.kt:153-154`, `feedback/FeedbackSender.kt:64` | "Sent, waiting", "Marked fixed by the team"; read `state_reason` and show "Closed without a fix" for `not_planned`/`duplicate`. |
| **B68** | 🔵 | The helper's report and reports entry is a round gear icon with no visible word, two levels deep (gear > "Problems with MedLog"). | `ui/screens/HelpScreens.kt:483` | Show a labelled "Report a problem" row on the helper home while the app is in testing. |
| **B72** | 🔵 | With fever + confusion the "Why" list shows both "Fever with confusion" and "Confusion". | `clinical/Triage.kt:158,167` | Drop the plain "Confusion" line when the fever line is present. |
| **B73** | 🔵 | "When did it start?" = "Yesterday" or "A week or more" does not move the note's time, so an old vomit counts in today's 24 h total. | `TellScreen.kt:187` | Ask the count "today" only and keep older starts out of the 24 h sum. |

Not bugs, but decisions to confirm: (1) the strength question for shivering ("How strong is it? From 0 to 10.") is odd; (2) the "Finish" and "Save" buttons; (3) a RED for chemo fever at 100.2 F does give the 108 card first (see B58).

## 5. How to re-run

```
export ANDROID_HOME=/opt/android-sdk; echo "sdk.dir=/opt/android-sdk" > local.properties
/opt/gradle-8.14.3/bin/gradle --console=plain -q testDebugUnitTest
```
To see a finding fail, delete its `@Ignore("FAILS: ...")` line. After a fix, delete the line and the test should pass. The 6 ignored Kamala tests and 2 pending Ravi tests are: B52, B53, B54, B55, B56, B57 (and W3).

## Round 2: sharing (Kamala's phone, Ravi's replica, the daughter's phone)

Tests: `app/src/test/java/com/suryaprakash/medlog/PersonaSharingTest.kt` (33 tests: 28 pass, 5 `@Ignore` = 4 bugs; real SQLite with the real triggers, `SyncRunner`, `FakeRelay`). Full suite after this round: 255 tests, 0 failed, 5 skipped (all in this file), `assembleDebug` OK.
The rule tested: "Helper and Self must be a bi-directional CRUD. Nothing should miss on any device." Every scenario checks that the phones end **identical** (`same()` compares every shared row with its winning version), then the specific expectation.

### Scenario results

| # | Scenario | Result | Test (line) |
|---|---|---|---|
| 1 | Ravi pairs a day later (relay has forgotten the first message): replica gets 3 medicines, 3 doses, 2 notes, appointment, 2 helpers, lab line, profile with her plan and cancer doctor; pair keys never leave her phone | PASS | `s1_*` (187, 207) |
| 2 | Ravi sets temp 102, BP 160/100 and 180/110, SpO2 red 88 on his phone: on Kamala's DB `DangerRules` gives 100.2 F GREEN, 102.4 F RED, BP 168/96 AMBER, SpO2 87 RED; her doctor edit reaches Ravi with his limits intact | PASS | `s2_limitsSetOnRavis*` (227), `s2_andBackAgain*` (247) |
| 2b | Kamala has "My details" open, Ravi sets limits, she taps Save: **limits erased on every phone** | **BUG B75** | `s2_limitsRaviSetWhileKamalaHasMyDetailsOpen*` (261, ignored) |
| 3 | Ravi adds Ondansetron: her phone gets it, `medicineChanged` + `reschedule` run, her new dose comes back to him | PASS | 289 |
| 3 | Ravi marks the 8 am dose taken: her phone calls `doseClosed` (alarm cancelled), `reschedule`, widget | PASS | 306 |
| 3 | Both tap the same dose TAKEN at different times: one dose row, later tap kept on both, pill count 13 (not 12) | PASS | 320 |
| 3 | Two phones both create the 14:00 dose of one medicine: one row (uid = medicine uid + time) | PASS | 340 |
| 3 | Ravi changes times and drops future doses; her phone plans again; both end identical | PASS | 352 |
| 3 | Two different doses taken on two phones at once: 13 pills left instead of 12 | **BUG B76** | 371 (ignored) |
| 3 | Ravi stops a medicine while her medicine page is open, she taps Save: it is active again | **BUG B76** | 387 (ignored) |
| 4 | 5 vomits on Kamala's phone while Ravi is offline 48 h (relay keeps 12 h): nothing arrives until he opens the app, then HELLO brings all 5; also 50 % message loss + HELLO | PASS | 406, 420 |
| 5 | Same field edited on both: identical, later edit wins, either delivery order | PASS | 433 |
| 5 | Different fields of one medicine edited at once: identical, but only ONE edit survives | identical PASS / both-survive **BUG B76** | 451 / 461 (ignored) |
| 6 | Ravi removes his wrong note (soft delete `deletedAt`): gone on her phone | PASS | 481 |
| 6 | She corrected it first, he removed later: the removal wins, her correction is lost, "Bring back" would restore the wrong "5 times" | documented, see "Delete vs edit" | 490 |
| 6 | He removed first, she edited later without having heard it: her edit brings the note back on both | documented | 501 |
| 6 | Hard delete of an appointment travels as a tombstone; a later insert brings one back, same on both | PASS | 510 |
| 7 | Daughter joins after 7 days: all rows and both tombstones (`sync_rows del = 1` = 2), then her edit reaches both phones | PASS | 525 |
| 7 | Same when Kamala's phone is off: Ravi's replica answers for her origin; when hers comes back it gets what the daughter added | PASS | 545 |
| 8 | Kamala's wipe: helpers keep every row, no delete is sent, no tombstones on helpers | PASS | 565 |
| 8 | Restore backup: new device id, catch-up, no duplicate uids, the appointment Ravi removed after the backup stays removed, her own dose change made after the backup comes back, new edits travel under the new id | PASS | 580 |
| 9 | Ravi's clock +1 h: after he has received from her, her later edit wins (hybrid clock) | PASS | 631 |
| 9 | Ravi's clock +1 h, concurrent edits: his wins although hers is later in real time | documented limit R1 | 644, 655 |
| 10 | Gate (code reading below) | no leak found | `s10_*` (745) |
| - | Fuzz: 3 phones x 6 seeds x 70 random edits (medicine, note soft delete, appointment insert/delete, dose slot, dose taken), message loss, phones going offline, then HELLO: all identical | PASS | 706 |

### New findings (numbering continues; B75-B77 have an ignored test, B78 is code reading only)

| ID | Sev | Finding | Evidence | Suggested fix |
|---|---|---|---|---|
| **B75** | 🟠 high | **A helper's limits (and any plan change) are erased by the person's Save in "My details".** The page keeps the profile it loaded (`profile = app.repo.profile()`, refreshed only `if (section == null)`), and Save does `profile().put(profile)` = `INSERT OR REPLACE` of the whole row with the OLD `plan` (limits, doctors, emergencies). It is stamped newest, so it wins and the limits vanish on Ravi's phone as well. Reproduction: Kamala opens My details (Settings > Me); Ravi saves 102 F on his phone; it reaches her phone (100.2 F becomes GREEN); she changes "Illnesses" and taps Save. Expected: conditions saved, limits kept. Actual: conditions saved, `limits.band("temp")` null on both phones, 100.2 F is RED again. | test `s2_limitsRaviSetWhileKamalaHasMyDetailsOpenSurviveHerSave` (261); `ui/screens/SettingsScreens.kt:86-88,114`; `data/Db.kt:214` (`@Insert(REPLACE)`) | On Save write only the fields the page edits (`UPDATE profile SET name=?, ...` without `plan`), or re-read the row inside the save and copy only changed fields. |
| **B76** | 🟡 medium | **A medicine is one row, last write wins for all columns; screens write every column of a copy loaded earlier.** (a) `pillsLeft` is a counter: two doses taken on two phones together leave 13, not 12 (test 371). (b) Different fields edited together: one edit is lost, e.g. her `purpose` vs his `times` (test 461). (c) The medicine page saves `m.copy(...)` of the copy loaded on open, so a medicine Ravi stopped (`active = 0`) is started again by her Save and alarms again (test 387). Both phones stay identical in all three cases, so nothing looks wrong. | tests at 371, 387, 461; `ui/screens/MedsScreens.kt:196-199,269-285`; `sync/SqlSyncStore.kt` `write` replaces all columns | Save only changed columns (compare with `original`); derive pills from taken doses instead of a stored counter, or merge per column. |
| **B77** | 🟡 medium | **A feed's contents (parts, kcal, protein, tube flag) are not shared.** They live in phone-wide settings `feed_info`, keyed by the LOCAL medicine id. The feed medicine syncs (name, ml, times, doses) but the other phone has no parts, so the calories/protein of a tube feed are missing on Ravi's phone; on a helper phone the id comes from the replica while the settings are the helper's own (ids start at 1 in every database), so Ravi's own feeds can be read as Kamala's. Reproduction: Kamala saves a feed; open Nutrition on Ravi's replica. Expected: same kcal. Actual: `Feeds.infoFrom(ravisSettings, replicaId)` is null (or Ravi's own feed). | test `s11_aFeedKamalaMadeShowsItsKcalOnRavisPhone_andNotSomeoneElsesFeed` (687, ignored); `ui/screens/FoodReadings.kt:319-321`, `nutrition/Foods.kt:499-509`, `nutrition/Nutrition.kt:70-76` | Store the parts in the medicine row (a `feedInfo` column added to `SyncSql.MEDICINES`), or as a shared doc. |
| **B78** | 🟠 high | **After the person's phone gets a NEW family key (wipe then restore, or a restore on a new phone), a helper's already-open replica channel stays on the OLD key** until the app process restarts, so Ravi silently stops receiving and sending. Chain: `FamilyChat.familyKey` makes a new key when `own_family_key` was forgotten (`SettingsStore.forgetFamily`, `data/Settings.kt:207-210`); `shareKey` sends it; the helper's `gotKey` stores it (`help/FamilyChat.kt:70-76`) and reconnects; `refreshReplicas` compares channel NAMES only (`SyncHub.kt:105-108`, `ReplicaPlan.diff` `data/Replicas.kt:40`), and the channel `p-<pairId>` is already attached with the old key (`attach` is called only for new names). Expected: helper re-attaches with the new key. Not covered by a JVM test (`SyncHub` needs Android); the sync itself after a restore is tested and passes (test 580). | code reading | Make `diff` take the key (attach again when `Chan.key` differs from `familyBytes`). |

### Delete vs edit (scenario 6) and clocks (scenario 9): the rule and whether it is safe

- The winner is the version with the higher (time, device id). A delete is a version like any other: soft delete (`notes.deletedAt`) is an ordinary edit of the whole row, hard delete (appointments, helpers, planned doses) is a tombstone. Both phones always end identical (tests 490, 501, 510, fuzz).
- **It never eats a newer edit if the clocks are right**: the later action wins and the earlier is dropped (test 490 shows her earlier correction lost, 501 shows his earlier remove undone). What is lost is invisible: nobody is told, and the loser's data is gone (the "removed" note keeps the old wrong text and count 5, which the rules count as a vomit, `Repo.recentForRules`).
- **R1 (risk, not counted as a bug, tests 644, 655):** a phone whose clock is ahead wins concurrent edits against the other phone's real-time later edit; a delete from such a phone eats a newer edit (1 h ahead: her correction lost). The plan's hybrid clock (`SyncSql.STAMP`, `SqlSyncStore.raiseClock`) fixes it only AFTER she has received his edit (test 631). The only sign is `Log.w` for ops more than 10 minutes ahead (`sync/SyncRunner.kt:135`); no screen tells Ravi that his phone clock is wrong. Suggest a one-line warning on Helper home when an op is more than 10 minutes ahead or behind.

### Gate check (scenario 10): no helper-side action rings, texts or calls

Read every caller of `Alerts.*`, `Sos.start`, `Calls.*`, `Scheduler.*`, `DoseAlert`, `CalendarSync` in `ui/`: the ones reachable with a replica open are gated.
- `Alerts.dangerToHelpers` gated: `FoodReadings.kt:478`, `DevicesScreen.kt:60`. `Alerts.tellOnce` for AMBER gated `TellScreen.kt:161`; the RED path goes to `Phase.DANGER` before any countdown when `viewing.active` (`TellScreen.kt:168`), so `Alerts.emergency/tellOnce` at `:382-387` (COUNTDOWN only) are unreachable on a replica. The mental-health `tellOnce` (`:169`) comes after the `viewing.active` branch, so it is unreachable too.
- `Sos.start` from the danger and emergency pages sits inside `AlertFamilyOrNotice` (`Common.kt:100`, `Emergency.kt:88,134`), which shows "This runs on Kamala's phone." instead.
- `Calls.ui` only dials on a replica (`help/Calls.kt:34`). `CalendarSync` gated (`integration/CalendarSync.kt:54,88,103`). `Scheduler.take/untake/skip/stopMedicine` take the replica `db` and do not cancel alarms, count down, or send "taken twice" unless `db === ownDb` (`meds/Scheduler.kt:159-165,187-212,223-227`). The tick, missed-dose and check-in alerts run on the own database only (`Scheduler.kt:117-141`, `care/Care.kt:82,112`).
- Replicas attach with `SyncEffects.NONE` (`SyncHub.kt:108`), so incoming rows ring nothing (test 745).
- Residual, by design and not a leak: a RED note Ravi enters on the replica syncs to her phone but nobody (not her, not the daughter) is texted, because no code reacts to notes arriving (`SyncRunner.react` handles only medicines and doses). He is with her when he enters it, but the daughter learns only by opening the app. Also `Scheduler.reschedule(ctx)` is called from replica screens (`MedsScreens.kt:107`, `MedAddFlow.kt:344`); it works on the helper's OWN database, so it only re-arms his own alarms.

### Data a user would expect to be shared but is NOT

Shared today (`SyncSql.TABLES`): profile (with the care plan and limits), helpers, medicines, doses, notes (incl. readings, meals, water as notes), appointments, doc_lines. Not shared:
1. **Feed contents** (B77), `settings feed_info` (`FoodReadings.kt:320`).
2. **Nutrition targets** `kcal_target`, `protein_target` (`NutritionScreen.kt:132`, read in `Nutrition.kt:43`) and **custom foods** `custom_foods` (`FoodPick.kt:70,167`): phone-wide settings. Ravi sets the doctor's kcal target while viewing Kamala's replica: it is saved on HIS phone, not hers, and a replica reads the helper's own value.
3. **Photos and voice**: medicine strip photo `photoPath`, note `photoPath`, note `audioPath` (voice); the transcript text does travel (test 669). The replica shows no "Play my voice".
4. **Calendar links**: `calendarEventId` of medicines and appointments (correctly per phone, the calendar entry is not created on the helper's phone).
5. **Messages / inbox** (`inbox` table: "I'm coming", replies, helper chat) and the person's **family tiles** (`Settings.messages`).
6. **Settings** that describe the person, not the phone: `waterGoal`, `diabetic`, `emergencyNumber`, `checkInEnabled/Time`, `snoozeMinutes`, `escalate*`, `sosCountdown`, `keepAudioDays`, `languages`, `helperPin`, easy/big mode (`data/Settings.kt:9-77`).
7. **Per-note follow-ups and "is it better?" markers** are local: `followups` (`care/Care.kt:129-137`, keyed by local note id) and `asked_better_<id>` (`HomeScreen.kt:121,155`): after "Yes, better" on her phone, Ravi's phone asks again (the note itself syncs, the question marker does not).
8. **Helper pairing** (`pairId`, `pairKey`) is per phone on purpose (test 207).

### Counts

Round 2 adds 33 tests (28 pass, 5 ignored for B75, B76 x3, B77). New bug ids: B75 to B78 (2 high, 2 medium), risk R1 (low), design notes above. Full suite: 255 run, 0 failed, 5 skipped.
