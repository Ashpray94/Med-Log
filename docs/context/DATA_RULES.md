# Data rules

Real health records for a real person. A counting or sync mistake here reaches a doctor.

## Records
- Room database, SQLCipher on the phone, in-memory in tests (`data/Db.kt`). Tables: notes, medicines, doses, profile,
  helpers, inbox, appointments.
- Every note, medicine and dose has a **`uid`** shared with paired phones and an **`updatedAt`**. Triggers stamp
  them on insert and bump `updatedAt` on update; the `_keep` trigger refuses to blank them.
- **Never write back an object read before it was saved.** After `insert`, read the row again (`get(id)`) before
  `update`. Writing back the unsaved copy is how notes lost their uid in 2.12 and earlier.
- Deletion is soft (`deletedAt`), so it syncs and can be undone (Removed page); purged after 30 days.

## Counting
- Symptoms: **only through `data/Occurrences.kt`.** "How many times today?" is a running total for that day: a
  day's count is the highest total given, or the number of notes if more. "Better now" notes are not occurrences.
- Doses: **only through `data/DoseOutcome.kt`**: GIVEN, FOOD_INSTEAD (counts as done), NOT_GIVEN, MISSED, DUE,
  LATER. Counts and percentages use `planned()`, which leaves out extra feeds.
- Feeds are counted apart from medicines ("medicines taken %" never includes feeds).
- Water glasses add up (each note is glasses drunk).

## One action, one row
- A link (deep link, widget, notification) is acted on **once**: `MainActivity` ignores it when the activity is
  rebuilt or reopened from Recents, and clears it after use.
- The Tell flow starts one note per start (`starting` guard). The widget takes its pending question once (`askLock`).
- The same problem within 10 minutes asks "the same one, or again?" (app and widget).
- `Duplicates.find` / `Repo.removeDuplicates` cleaned copies made before 2.13 (runs once, flag `dedupe_v1`).

## Sharing between phones (`data/Sync.kt`)
- The person's phone and each helper's copy (`mirror_<pairId>.db`) send what changed since last time, sealed, through
  the relay (or Bluetooth nearby). Rows match by `uid`; newer `updatedAt` wins.
- If no row has the incoming uid, the **same entry** (kind, problem, occurredAt, createdAt, text: `sameEntry`) is
  matched instead, and both phones settle on the smaller uid. Removals match by uid only. `Repo.mergeCopies` merges
  copies already on a phone (at start and after each sync).
- Grouped notes are linked by the **uid** of the group's first note (`gu`), never by row number.
- A helper's actions on the person's records write to the mirror and sync back (`Doses`, `HelperDose`).
- Tests: `DataIntegrityTest` covers round trips, sending twice, deletions and ids. Add to it for any change here.
