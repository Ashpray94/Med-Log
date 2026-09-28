# MedLog: read this first

MedLog is an Android app (Kotlin, Jetpack Compose, Material 3, Room with SQLCipher, WorkManager, Glance widgets) for an
older person to note how they feel, take medicines and feeds, and reach family; and for their helpers (family) to
follow along on their own phones. Hindi and Tamil throughout. The owner installs every release on real phones
through the in-app updater, so **every change ships to a person who depends on it**.

Work on branch `main-mxp0m4`. PR #1 is the open draft for it.

## Before you change anything

1. Read the file in `docs/context/` for the area you're touching:
   - `UI_RULES.md`: every design rule the owner has set. Breaking one is a bug.
   - `DATA_RULES.md`: how doses, occurrences, ids and sync work. Getting this wrong corrupts real health records.
   - `WORKFLOW.md`: build, tests, screenshots, line endings, commits, releases.
   - `CODE_MAP.md`: where things live.
   - `STATUS.md`: current version, what just changed, what's open.
2. Look at the screen before and after with the screenshot harness (see `WORKFLOW.md`). Don't claim a UI change
   works without having looked at it.
3. Check both modes: **my health (teal)** and **helping (blue)**, and at least one of Hindi or Tamil for anything with words.

## Non-negotiables (the owner has asked for each of these; see UI_RULES.md for the full list)

- No horizontal scrolling anywhere. No thick borders, no coloured side stripes on cards.
- Colour: green only means *done*. Actions use the mode's accent (teal / blue), never green. Mode colours come from
  `ui/Theme.kt` only; `ColourRulesTest` fails the build otherwise.
- Nothing blends into the background: white cards with a soft lift on a light grey page.
- Histories are timelines. Key-value rows only for short summaries.
- Sheets open at 75% and grow to 90%.
- Plain, kind words. No "MedLog" inside the UI. No dashes or "not recorded" filler on the doctor page or PDF.
- No regressions: re-check the screens around the one you change.

## Data non-negotiables (see DATA_RULES.md)

- Count symptoms only through `data/Occurrences.kt`, and dose outcomes only through `data/DoseOutcome.kt`.
- Never write back a row object read before it was saved (it has no shared `uid`). Read it again from the database first.
- One user action = one saved row. Guard against double taps, re-delivered intents and races.
- Every data change gets a test in `DataIntegrityTest` (or a new test class).

## Hard rules

- Never commit signing keys or keystores. The key lives only in the `MEDLOG_SIGNING` GitHub secret.
- No model names or identifiers in commits, PRs, code or docs.
- Tests must pass before every push. Run `python3 scripts/keep_eol.py` before committing (CRLF files).
- Say plainly what was and wasn't verified (the cloud machine has no phone: alarms, SMS, Bluetooth, widgets, speech
  and installs can't be tested here).

## Ending a chat

Before you finish, update `docs/context/STATUS.md` (version, what changed, what's open, what wasn't verified) so
the next chat starts from the truth, not from memory.
