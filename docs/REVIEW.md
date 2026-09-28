# MedLog review: personas, use cases and issues (27–28 September 2026)

Reviewed: the app as of 2.11.0 (branch `main-mxp0m4`). **2.10.9 is marked as the best version before this review**
(release `v2.10.9`, commit `36b7755`); everything below is measured against it.

## How this was tested, and what it could not test

- **Screens drawn from the real code** (the screenshot harness in `app/src/test/.../Shots.kt`), under each
  persona's conditions: English, Hindi and Tamil; normal and large text; the person's phone and the helper's
  phone; a brand-new install; and "worst case" data (long names, 11 medicines and feeds, up to 6 doses a day,
  every dose state at once, long messages, 4 helpers, 3 doctors). Flows were walked by tapping.
- **Code reading** of the logic behind each screen: dose states, counts, alerts, sync, updates, sheets.
- **Unit tests** (9 test classes) pass after every change.
- **Not tested here** (no emulator can run in this cloud machine): alarms and full-screen alerts, notifications,
  SMS and calls, Bluetooth, speech recognition and read-aloud, the home-screen widgets, the in-app update install,
  and real timing. These need a pass on a phone; the checklist is at the end.

## What the app is for: use cases

| # | Who | Use case |
|---|---|---|
| U1 | The person | Say how they feel (pictures or speaking) and have it noted with the time, in 2–3 taps |
| U2 | The person | Be reminded of medicines, and mark them taken, late, or skipped |
| U3 | The person | Ask family for something (water, "please come") in one tap, from the app or the widget |
| U4 | The person | Get help in an emergency: call 108, alert the family with location, say what's happening |
| U5 | Helper | See at once whether the person needs them, answer ("I'm coming"), or pass it to another helper |
| U6 | Helper | See today's medicines and feeds for the person, and mark them for them |
| U7 | Carer | Log feeds (given, not given, food instead), food, water, toilet, readings, day after day |
| U8 | Doctor | Read one page: what happened, how often, how bad, medicines kept to, feeds, readings |
| U9 | Family member | Set the app up for a parent: helpers, doctors, medicines, permissions |

## Personas

### 1. Lakshmi, 78: the person (Tamil, low vision, large text, her own phone)

Lives with her son's family; arthritis in the hands; reads Tamil; uses WhatsApp calls and nothing else.

| Task | Expected | Found (before this pass) | Now |
|---|---|---|---|
| Tell how she feels | One big question, two big ways in | Good: white card, Choose / Speak, Tamil throughout | Same |
| Mark a missed medicine | Clear what was missed, one obvious button | "Details" split mid-word ("விவரங்க / ள்") beside the answer; the schedule line cut the time off ("ஒருமுறை · 1") | Buttons stack in large text and Tamil/Hindi; the time comes first ("தினமும் 1 முற்பகல்") |
| Ask family for help | One tap | Good | Same |
| SOS | Big, unmistakable | Good: red Call 108, dark hold-to-alert card | Same |
| Go back from a question | Back and SOS reachable, nothing else | The bottom bar showed on question pages | No bottom bar there; SOS sits at the top |

- **Learning curve:** low for Home, Tell and SOS. Medicine Details and History are harder (two ways to browse History).
- **Frustrations:** long Tamil titles wrap to 2–3 lines and push content down; the dark SOS card is visually very
  heavy.
- **Verdict:** usable on her own for U1–U4.

### 2. Ravi, 45: helper far away (Hindi/English, his own phone)

Works in another city, checks the app a few times a day, needs to know in two seconds whether something is wrong.

| Task | Expected | Found | Now |
|---|---|---|---|
| See if mother needs him | A clear title, then the message | The whole message was the title, all bold; no "who and why" | A real title for each kind ("Lakshmi may need urgent help", "A medicine isn't marked as taken", "Message from Lakshmi"); the message is a sentence under it |
| Answer | 1–2 obvious choices, and a way to say "done" | Four equal buttons, no "already handled", no dismiss | Personal alerts: I'm coming · I'll call · More (5 min, ask someone else, already handled, dismiss). App notices: I'll call · Already handled · Dismiss |
| Know it's up to date | Only told when it isn't | "Updated at…" strip even when fine | Strip only when nothing for 2 hours or never |
| Medicines and feeds today | Separate, one card each | Mixed; "0 of 6 given" even with food instead | Separate sections; food instead counts as done |
| Hindi dates | In Hindi | "Today 7:21 pm" in English | "आज 7:28 pm" |

- **Verdict:** good for U5–U6.
- **Still open:** a "medicine not taken" alert stays up after the medicine is marked taken later (see O1).

### 3. Meena, 52: full-time carer (English, logs everything)

Looks after her mother, who is fed a mix 6 times a day and sometimes eats ordinary food instead.

| Task | Expected | Found | Now |
|---|---|---|---|
| Note "ate food instead" | Its own status, neither missed nor given | Treated as missed in Details (red) and not counted as done; card said "Missed" | One outcome rule for the whole app (`data/DoseOutcome.kt`): given, food instead, not given, missed, due, later. Food instead shows as done ("Done for today · 1 with food instead"), grey in Details, its own line in history |
| See the day's feeds | One line of times | Times wrapped onto 2 lines | First three times, then "+3 more", on one line |
| Feed history | A timeline, name once | Done in 2.10.9 | Same |
| "What did you eat?" filters | All visible | Sideways-scrolling chips, last one cut off | Chips wrap |
| Nutrition page | Content, or "loading" | Blank for a few seconds | "Getting it ready…" with a spinner |

- **Verdict:** good for U7.
- **Open:** dish pictures are grey placeholders (O6).

### 4. Dr Rao, 55: GP reading the doctor page

Has 10 minutes per patient and reads the printout or the phone.

| Task | Expected | Found | Now |
|---|---|---|---|
| See the most important thing first | Urgent first | An urgent chest-pain note ranked below "Tired" because it had no written reason | Urgent and watch notes always rank first ("noted as urgent" when no reason was written) |
| Medicines kept to | Medicines only | Feeds counted as medicines (fixed in 2.10.7) | Same |
| Visual | Plain | A dark bar down the side of each concern | Removed; the level tag says it |
| Page opening | Content or a message | Blank for a few seconds | "Getting it ready…" |

- **Verdict:** the page reads well.
- **Open:** the page takes a few seconds to build (O13).

### 5. Arjun, 24: grandson setting it up (English)

| Task | Expected | Found | Now |
|---|---|---|---|
| Understand the app | A short welcome | An auto-sliding carousel (moves by itself, swipes sideways) | Four plain points on one page |
| Choose "my health" or "I help" | Clear | Good | Same |
| Permissions | Each one opens | SMS blocked silently on Android 13+ for side-loaded apps | The app shows the exact steps (fixed in 2.10.4) |

- **Verdict:** setup is long (about 16 steps) but each step is clear.

## Visual and interaction audit

- **Components:** buttons, the bottom bar, segmented choices and chips are now Android's own Material 3
  components (ripple, states, accessibility). They are wrapped so the app's tap rules still apply: the
  steady-hands pause, and touch-to-hear (the first tap reads the label, the second acts).
- **Hierarchy:** one accent colour per page for the main action; red only for SOS, urgent and missed; amber for due
  and to-watch; green for done. Cards are white on a light grey page with a soft shadow and a faint edge. No side
  stripes or heavy frames.
- **Type:** one scale. Titles wrap and shrink up to 30% rather than split a word or be cut off. The one deliberate
  exception is the medicine card's times line, which ends in "…" (Details has everything).
- **Nothing scrolls sideways:** the last two sideways areas (food filters and the welcome carousel) are gone.
- **Sheets** open at most 75% high and grow to 90%, never under the status bar.

## Logic audit

| Area | Rule now | Checked |
|---|---|---|
| Dose outcome | `Dose.outcome()` in `data/DoseOutcome.kt`; food instead is covered, never missed | Card, banner, Details, History, feed history, counts |
| Counts | Medicines: taken of due. Feeds: done (given or food instead) of due | Home, helper home, Today lists, Food page |
| Medicines taken % | Feeds excluded | My health, doctor page |
| Alerts to helpers | A missed feed says "feed" and "given"; titles per kind | Wording, helper card |
| Updates | Checked each time the app comes to the front (at most every 30 min) and each time the Updates page opens; shown on both home pages | Updater, Settings, Home, helper home |

## Open issues (not fixed in this pass)

| # | Severity | Issue | Suggestion |
|---|---|---|---|
| O1 | Medium | A helper's "medicine isn't marked as taken" alert stays after the dose is marked taken later | When a dose is taken after helpers were alerted, send a quiet "now taken" note that clears the alert |
| O2 | High (untested) | Alarms, notifications, SMS, Bluetooth, speech and widgets are untested here | Run the phone checklist below |
| O3 | Low | Setup is shown in English before a language is chosen | Ask the language first |
| O6 | Low | Dish pictures on "What did you eat?" are grey placeholders | Use food pictograms like the symptom ones |
| O7 | Low | History has two ways to browse (by day, by problem); by problem is rarely needed | Keep by day; show problems as a filter |
| O8 | Medium | "Medicines" and "Today's medicines" overlap | Make Medicines the list to manage (add, change, stop), and Today's the list to mark |
| O10 | Low | Helpers' chat says only "starts once … has the new version" until both phones are updated | Say what to do: "Update Lakshmi's phone" |
| O11 | Low | The "Read" button's muted-speaker icon reads as "sound off" | Label it "Read aloud" |
| O13 | Low | The doctor page and Nutrition take a few seconds to build | Build them in the background when the app opens |
| O20 | Low | The dark "Hold to alert my family" card is the heaviest thing in the app | A red-tinted card, like Call 108 but lighter |
| O22 | Low | Large text in Tamil wraps page titles to 2–3 lines | Shorter Tamil titles |

## Phone checklist (for the pass this machine can't do)

1. A medicine alarm rings over the lock screen; swiping it stops the sound; a quiet reminder stays.
2. "Ate food instead" from a feed notification or card shows as done, grey, in Details and history.
3. A helper phone gets "SOS from…" with I'm coming / I'll call / More, and "Already handled" clears it.
4. SMS: allow via Settings → ⋮ → Allow restricted settings; "Allow text messages" disappears.
5. Widget: the question sits at the top, the buttons at the bottom; the status line goes after 10 seconds;
   Messages opens Family.
6. Updates: open the app → a new version appears on Home within a moment; Settings → Updates checks again.

## Colour audit (28 September, after 2.12.0)

Missed in the first review: it compared layouts and wording, but never checked each mode's colours against its own accent.
Found and fixed in 2.12.1:

| Leak | Where | Now |
|---|---|---|
| Green action buttons (about 30) | Given, Took it, Done, Yes, Update now, alert screens | The mode's accent (teal for my health, blue for helping) |
| Black text on blue | Selected tabs, chips, segmented choices | White, from the button's own content colour |
| Teal in helper mode | History and Toilet icons, Undo bar, date and time pickers, widget | Slate in helper mode; pickers and widget follow the mode |
| Green that wasn't "done" | Read aloud toggle, Food & water icon, call icons, "I have no allergies" | Accent, or a warm brown for food |
| Blue for waiting | "Sending…", "Waiting for an answer" | Grey |
| Android default purple | Unset Material colour slots | Mapped to the palette |

Rule, now checked by `ColourRulesTest` on every build: green only means done; actions take the mode's accent; mode
colours are never typed in outside `ui/Theme.kt`.
