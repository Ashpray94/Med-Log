# Status

Update this at the end of every chat: what changed, what's open, what wasn't verified.

## Now
- Released: **2.14.1** (versionCode 2141): 2.14.0 plus the feedback token (MEDLOG_FEEDBACK_TOKEN secret added)., branch `main-mxp0m4`, draft PR #1.
- 2.14.0: SMS only for SOS (app first, SMS after 90 s with no answer: `SosPlan`); helper alerts use Meeting Timer's
  reminder page (`assets/alert/`) with exactly 3 replies per alert (`help/AlertReplies.kt`); personal limits and
  cancer-care rules; trusted links; "They match" pairing; stopped medicines close their doses; shake to report
  (token secret added for 2.14.1; reports go to private repo Ashpray94/Med-Log-feedback); 3-second send on family messages; one Settings page in both modes;
  update sheet once per phone lifetime (`UpdateSheetOnce`).
- Not in 2.14.0 on purpose: the other branch's sync engine (2.13.1's `data/Sync.kt` stays).
- 2.14.0 phone checks: the WebView alert over the lock screen with sound; notification's 3 buttons; "In 5 min" rings
  again (lost if the phone restarts); "Ask another helper" rings the other phone; SOS app-first then SMS; pairing
  with They match on both phones; shake to report; the update sheet shows once.
- Best version before the 2.11 review: 2.10.9 (commit `36b7755`).

## Recently changed (2.11 – 2.13)
- 2.13.1: copies that came back from helper phones (each entry 2–3 times, from notes saved without a shared id before
  2.13) are recognised by content in sync (`sameEntry`), merged (`Repo.mergeCopies`, at every start and after every sync),
  and every phone settles on the smallest id. Tests: `copiesUnderDifferentIdsBecomeOneOnEveryPhone`, `copiesAlreadyOnAPhoneAreMerged…`.
- 2.13.0: duplicates fixed at the root (links acted on once, one note per start, widget saves once); counts as running
  totals everywhere; shared ids kept (they were being blanked) and repaired; group links by id; copies moved to
  Removed once; "same one or again?" within 10 minutes; pending details on Home; My health → one page per measure;
  doctor page items open their pages; PDF waits for its nutrition page. `DataIntegrityTest` added.
- 2.12.x: helper reminder actions (Given, Snooze 5 min, More: give later, ask another helper, not given); helpers can
  see and message each other; two-way nearby pairing that replaces old links; Dismiss on every alert; white text on
  blue; cough and long-running problems ask how often and since when; normal temperature in one tap; extra feeds;
  "Food taken instead of feed" everywhere; colour rule (green only for done) with `ColourRulesTest`; doctor PDF
  without clutter (original layout kept).
- 2.11.0: alert titles; food-instead outcome; Material 3 components; persona review (`docs/REVIEW.md`).

## Ported from the 2.9.0 side branch (not yet released, version not bumped)
- Safety: links that call, send SOS or send a message act only when the app made them (`integration/TrustedLinks.kt`);
  pairing accepts only after "They match" on each phone; stopping a medicine skips its open doses (`Scheduler.stopMedicine`);
  the privacy wipe runs off the main thread.
- Clinical: helper-set limits per measure (`clinical/Limits.kt`, `LimitsForm.kt`, Limits page, Helper controls row, setup step),
  over-55 numbers never assumed, cancer-care rules, absolute temperature rules and degrees C, cancer doctor first on the red page,
  helpers told once per level (`clinical/Told.kt`), 10-second cancel before helpers are called or told. 2.13's running-total counting
  (`Occurrences`) was kept for vomiting and loose stools; cough duration stays in days.
- Bug reporting (`feedback/`): shake to report, page picture, GitHub issues. Needs the `MEDLOG_FEEDBACK_TOKEN` secret in the
  release workflow (or gradle property `medlog.feedbackToken`); without it reports stay on the phone.
- Family messages wait 3 seconds and can be cancelled (`SendCountdown`, `PendingSend`).
- SMS only in an SOS (`SosPlan`): every other alert and help message goes only to the helpers' app.
- Not ported on purpose: the branch's sync engine and settings-in-profile; the "whose phone" switch stays on Home (2.13's `PersonaSwitch`).
- Waiting on a phone: pairing with "They match" on both phones; SOS app-first flow and the 90 s / 20 s SMS fallback; shake to report;
  the Limits page with a real helper; the countdown before a plan emergency.

## Waiting on the owner's phone (can't be checked in the cloud)
1. After 2.13.1 on the person's phone AND every helper phone: History shows each entry once (the 3× cough and vomiting
   of 28 Sept become one each); vomiting reads 3, not 6; the extras are in Removed. Update all phones: a phone still
   on an older version keeps sending its copies back.
2. Doctor page → Share/Print: the PDF has its second page (nutrition).
3. Helper reminders: Given and Snooze from the notification; More opens the choices page; "Ask Meena" rings her phone.
4. Pairing: both phones list each other; connecting again doesn't list the person twice.
5. Widget: tapping the same problem within 10 minutes asks "again?"; Add details opens that note.
6. Helper phone after update: records come back complete after the one-time resend.
7. Alarms over the lock screen; SMS permission steps; Bluetooth scale; read-aloud.

## Open issues (from docs/REVIEW.md, not yet done)
- O1: a helper's "medicine not taken" alert stays after it's marked taken later.
- O3: setup is in English before a language is chosen.
- O6: dish pictures are grey placeholders.
- O7: History has two ways to browse; "by problem" is rarely needed.
- O8: "Medicines" and "Today's medicines" overlap.
- O10: helpers' chat wording until both phones are updated.
- O11: the "Read" button's muted-speaker icon reads as "sound off".
- O13: doctor page and Nutrition take a few seconds to build.
- O20: the dark "Hold to alert my family" card is very heavy.
- O22: long Tamil titles wrap to 2–3 lines in large text.
