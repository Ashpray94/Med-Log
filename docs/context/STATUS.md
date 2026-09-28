# Status

Update this at the end of every chat: what changed, what's open, what wasn't verified.

## Now
- Released: **2.13.0** (versionCode 2130), branch `main-mxp0m4`, draft PR #1.
- Best version before the 2.11 review: 2.10.9 (commit `36b7755`).

## Recently changed (2.11 – 2.13)
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

## Waiting on the owner's phone (can't be checked in the cloud)
1. After 2.13.0: History shows each entry once; vomiting count is right; copies are in Removed.
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
