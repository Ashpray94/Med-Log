# MedLog: what we're building, organised

Every request so far, grouped by what it is for. Status: **Done**, **Partly done** (what's left is noted), or **Next**.

**Best version so far: 2.10.9** (release `v2.10.9`, commit `36b7755`), marked before the 2.11 persona review. The review, with every issue found and what is still open, is in [REVIEW.md](REVIEW.md).

## The model: three kinds of thing a person notes

The app felt complex because different kinds of entry were mixed together. Everything fits into three kinds, and each
kind has the same shape everywhere (Home, History, widget, helper's phone, doctor page):

| Kind | What's in it | How it's noted | Reminders |
|---|---|---|---|
| **How I feel** (ailments) | pain, cough, fever, tiredness… | picture → a few questions → saved at the first tap | none; one "tell me more?" 30 min later if left short |
| **What goes in** | medicines, feeds, food, water | one tap; time can be changed or set later | medicines: **loud**; feeds and food: **quiet** |
| **What comes out** (excrements) | stool, urine, vomit | picture of what it looked like, 2–3 taps | none |

Plus two things that aren't notes: **messages to family** (comms) and **SOS**.

Vomit is kept in "what comes out" *and* can still be told as an ailment ("Vomiting" with how many times). The toilet
note is the precise record (colour, blood); the ailment is how it feels. The doctor page shows both.

## Reminders: two classes, nothing in between

| Class | Used for | Behaviour |
|---|---|---|
| **Loud** | medicines, messages and SOS from the person, a medicine the person hasn't taken (on helpers' phones) | alarm sound, full screen over the lock screen; a swipe (even of the pop-up) silences it and leaves a quiet pinned reminder until answered; answering anywhere clears it |
| **Quiet** | feeds, food, water, check-in, finishing a half-done note, family chat | ordinary notification, no alarm |

## Status

### Safety and reminders
- **Done**: swiping a loud reminder always stops the sound, including the pop-up. Answering from the notification, the alert screen or the app, or another helper answering, clears it everywhere.
- **Done**: reminders are classed loud or quiet (table above).
- **Done**: helper messages have "I'm coming" / "I'll call" right on the notification.
- **Done**: MedLog writes nothing to shared storage. The `.crypt14` files are WhatsApp's backups.

### Helpers
- **Done**: helpers log for the person ("How are they feeling?", Speak it all for them, medicines, food, toilet, readings) and see the same history and doctor page.
- **Done**: the person's latest message is the first thing on the helper's page, in a solid card with the answers on it.
- **Done**: one helper can ask another to go ("Ask someone else to go"), and the other phone rings.
- **Done**: helpers get the person's medicine reminders (a loud one if a medicine isn't taken 15 minutes later) and quiet feed reminders.
- **Done**: records sync both ways over the internet (sealed; any network, not just the same Wi-Fi), within about a minute of a change, with a 15-minute catch-up in the background. Android doesn't allow background checks more often than every 15 minutes, so changes are *pushed* the moment they're made instead of polled every 5.
- **Done**: when the internet can't deliver, records go to helper phones in Bluetooth range (at most once every 15 minutes, only with something new).
- **Done**: "Updated 2 min ago" on the helper's page; "Not shared with your helpers yet" on the person's Home when something is waiting.

### Noting things
- **Done**: everything is saved as it's tapped; closing the app loses nothing; one reminder 30 minutes later for a half-done entry (symptoms, food, toilet).
- **Done**: back-dating: a "When" row on every kind of note (up to two weeks back).
- **Done**: medicines can be noted afterwards: "When did you take it?" (on time / just now / another time), "Change the time", and any dose on any past day from History.
- **Done**: water: tap once, it's saved; change a glass's time on the glass itself (no "earlier" button).
- **Done**: food: main dish first, sides as pills under it, tap a pill for the counter sheet; the main dish stays in view.
- **Done**: toilet and vomit have their own page, apart from how one feels.
- **Done**: related problems are suggested together (cough → breathless, vomiting → nausea…).
- **Done**: questions ask in days, not weeks.
- **Done**: logging from the lock screen (Speak it all; can't see existing records without unlocking).

### Medicines and history, kept short
- **Done**: a medicine taken many times a day is one card: its next times as one strip ("12 PM · 6 PM · +2 more"), tap for every dose.
- **Done**: feeds are never listed as medicines.
- **Done**: History: one group per kind; open a group for each entry, times in one column in line with the names.

### Widgets
- **Done**: nothing is noted from a widget without asking: a problem asks Done / Add details / Cancel (Done notes it and asks for more in 30 minutes; Add details opens its questions in the app); water asks Done / Cancel; a message asks Send / Cancel.
- **Done**: food and medicine open the app; medicine shows which medicine is being noted, with "I took it" on each.
- **Done**: main widget, top to bottom: usual problems with "More" in the last place, then Food / Water / Medicine, then messages, then Speak and SOS at the bottom.
- **Done**: two small widgets besides the main one: "How I feel" (your usual problems; asked first, then Add details / Done) and "Toilet & vomit" (Stool, Urine, Vomit, each opening its picture page).

### SOS
- **Done**: Call 108 as before; "What's happening?" pictures under it: one tap alerts the family with the kind of emergency.

### Look and feel (for older eyes)
- **Done**: no text is ever cut off with "…"; picture grids use fewer columns rather than split a word.
- **Done**: WCAG AA: text 4.5:1 or better everywhere; every button, field and option has a 3:1 edge.
- **Done**: plain words: Done, Remove, Note (no Save / Delete / Log / Sync).
- **Done**: one design for repeating things (tiles, cards, groups, sheets), the same on every page.
- **Done**: Hindi and Tamil for every on-screen line, with dates in the chosen language.

### Speak it all
- **Done**: one long spoken sentence is split into symptoms, toilet, food, water, medicines and readings, each put in its place; urgent things (chest pain with breathlessness, blood in vomit, the person's own emergencies) go straight to helpers with no questions. Covered by tests.
