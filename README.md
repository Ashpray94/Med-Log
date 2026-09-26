# MedLog

*Say how you feel. MedLog remembers it, looks after you, and tells your doctor clearly.*

MedLog is an Android app for older people and anyone in supportive care. It records how the person feels,
reminds them about medicines, sends help to family, and turns it all into a one-page summary a doctor can read in a minute.
Your notes stay on the phone. The app uses the internet for one thing only: sealed help alerts to your
helpers' phones when they are out of Bluetooth range.

The full product plan is in [PLAN.md](PLAN.md).

## Install

Download the APK for your phone from the latest [release](../../releases):

| File | Phones |
|---|---|
| `MedLog-2.7.0-arm64.apk` | Almost every phone from 2017 on (use this one if unsure) |
| `MedLog-2.7.0-armv7.apk` | Older or low-cost phones |

Android 6.0 or newer. Allow "install unknown apps" for the app you open the file with.

## New in 2.7.0

See [RELEASE_NOTES.md](RELEASE_NOTES.md). In short: updates from inside the app (from the public
[Med-Log](https://github.com/Ashpray94/Med-Log) downloads, checked by SHA-256 and signing key),
quick messages between helpers instead of a chat, a 3-second Undo bar, and everything from 2.6: much better voice understanding (the phone's own speech), the
whole app in Hindi and Tamil, slower read-aloud, family one tap away (home card and a Family widget), your own
messages, answers from helpers in seconds, "How are you?" from helpers, a helpers' family chat, a clear
"Whose phone is this?" switch, a setup file to skip setup, and calmer messages. The Play Store review is in
[docs/PLAY_POLICY_REVIEW.md](docs/PLAY_POLICY_REVIEW.md).

## What was in 2.5.0

- **Helpers are reached wherever they are.** Help messages and SOS now also go over the internet to paired helper
  phones that are out of Bluetooth range. Nearby phones still get them by Bluetooth with no internet, and SMS still goes too.
- **Only their phone can read it.** Each alert is sealed with the key the two phones agreed when they were paired.
  It passes through a relay (ntfy.sh, or your own) that sees only scrambled bytes. No accounts, no MedLog server.
  Phones paired before 2.5 work straight away.
- **You see it arrive:** "Ravi's phone got it" appears as soon as the alert is on their screen, then their answer.
- **On the helper's phone:** the home screen shows whether the internet link is connected. Alerts missed while the
  phone was off come in when it's back; old ones show as a normal notification with the time sent, not an alarm.
- Settings → SOS → "Reach helper phones over the internet" turns it off. The relay address can be changed there.

## What was in 2.4.0

- **A new first-time setup**, one question per page, with a progress bar, a Read aloud button and Back on every page.
  Welcome → who it's for → how it should look → languages → name → year of birth → long-term illnesses →
  allergies → blood thinner → doctor → helpers → permissions → home-screen widget → "You're all set".
- **Helpers set up in full:** choose from contacts or type, who they are to you, and what they get (calls and texts
  in an emergency, missed-medicine texts). Then "Let them know" by text message and, if you want, pair their phone.
  They are called in the order shown. Skipping explains that only 108 can be called without one.
- Every answer is saved as you go. Leaving to pair a phone, or pressing Home, returns you to the same step.

## What was in 2.3.0

- **Doctor page, calmer.** One main button (Share with the doctor, which asks Send or Print) and one quiet card for
  how far back it covers. Numbers are cards side by side. Each symptom shows Times and Worst pain as cards,
  then plain facts: Started, Over time, Where, What it feels like, Warning signs. "My questions" is set apart at
  the bottom, and adding one opens its own window.
- **Real dates, never stale words.** "Just now" and "yesterday" are saved as the actual time or date. Dates are
  never shortened, and a day before today is shown by its date.
- **History by problem is a timeline**, grouped by date, one card per time it was noted.
- **Pain all over the body** can be recorded ("It's all over my body", or say "all over" or "everywhere").
  It shows as a shaded body on the doctor page and in the PDF.
- **Warning signs** list only what was there, or "None". There's no more "not the worst ever".

## What was in 2.2.0

- **SOS on every screen.** The bottom bar has a red SOS button in the middle, including during a conversation.
  It opens the Emergency page with three different choices: a large red "Call 108" card, a dark
  "Hold to alert my family" card, and faces to call one person. The widget's red button goes there too.
- **Doctor page redesigned as clean tables.** Patient, a three-number summary, main concerns with Urgent/Watch
  tags, a body diagram with numbered pins, and one table per symptom: how often (with a day-by-day strip),
  started, trend, how bad (0–10 bar), where, feels like, and danger signs. Medicines show doses taken as bars;
  readings flag values out of range. The period is a one-line control.
- **A new body chart** (front and back), used on screen and in the PDF. It zooms to the area of the problem,
  with "Show the whole body" below it.
- **Widget:** a true grid of equal squares, sized to the widget.
- **Readability:** no text below the minimum size, urgent items say "Urgent" in words, and single-choice
  settings are one-line controls.

## What was in 2.1.0

- **A conversation, one question at a time.** Picking a symptom (from the widget, Home or the picture list) opens a
  full-screen conversation. The question is spoken and shown in large captions, a live caption shows what you are saying,
  and big buttons are there if you'd rather tap. It covers when it started, danger signs, where, and how bad. Then it asks "tell a
  little more?", and a gentle reminder comes 30 minutes later if you said not now. "I'm done" is always on screen and ends
  on a clear summary.
- **Exact location, depth and feel.** A zoomed body map takes a precise pin. There are picture questions for how deep
  (skin, under the skin, muscle, deep inside, bone or joint) and what it feels like (sharp, burning, throbbing...).
- **Pain and burn scales.** The 0–10 Wong-Baker faces pain scale with exact numbers, plus itch and strength versions.
  Burns are assessed by look (red, blisters, white/brown/black) and by size compared with a palm. Deep or large burns are danger signs.
- **Indian languages.** Hindi, Tamil, Telugu, Kannada, Malayalam, Marathi, Bengali, Gujarati, Punjabi, Odia and Urdu,
  using the phone's own offline speech recogniser (Android 12+), with language detection on Android 14+. Answers are
  transliterated into English letters for the doctor. Questions are spoken and shown in Hindi and Tamil. Other phones use offline English.
- **Doctor page rebuilt as a plain clinical note:** main concerns, one symptom table, a body diagram with numbered pins,
  medicines, readings and the patient's questions. Nothing about the app.
- **History** has large day-by-day navigation and a "by problem" view. A **bottom bar** on every screen has Home,
  History, Help and Read aloud.
- **Widget** rebuilt with equal tiles and an "Add MedLog to home screen" button in Settings.

## What was in 2.0.0

- **Tell how you feel**: tap and speak (offline Indian-English speech recognition), or pick from 70 symptom pictures.
  MedLog reads back what it understood, asks at most two follow-up questions, and saves it.
- **Danger signs**: a deterministic rule set (not AI) flags emergencies such as stroke signs, chest pain with sweating,
  vomiting blood, or a fall on a blood thinner. It shows Call 108 first and messages helpers.
- **Medicines**: exact-time reminders that ring on silent, snooze, skip with a reason, a double-dose guard,
  helper alerts for missed doses, refill warnings, "Did I take my medicines?", and a photo of the strip with text reading.
- **Help**: message tiles sent to paired family phones over Bluetooth / Wi-Fi Direct (no internet) and, when
  they're far away, over the internet (sealed end to end), with SMS fallback,
  and SOS. SOS sends an SMS with your location, calls each helper in turn on speaker, then 108. A WhatsApp group call is optional.
- **Doctor page**: a one-page A4 PDF with needs-attention items ranked first, a timeline, details, medicine adherence,
  readings, patterns marked ◇, and the patient's questions. Share or print it.
- **Also**: food and water, BP/sugar/oxygen readings (typed, spoken or Bluetooth), trends, a morning check-in,
  optional fall detection, importing old reports, Google Calendar through Android's own sync, a home-screen widget,
  a Quick Settings tile, and encrypted backup.
- **Accessibility**: a Read Aloud button on every screen, Big mode, high contrast, steady touch for tremor,
  touch-to-hear, left-hand mode, undo everywhere, and no timeouts.

## Not yet

- The danger-sign rules and clinical content **have not been reviewed by a clinician**. Don't rely on them for care decisions yet.
- English only. No on-device AI model; understanding uses the tested rule parser.
- Meeting Timer's medicine card (the "I took it" button inside Meeting Timer) is not built yet. MedLog shows its own reminders.

## Build

```bash
./scripts/gradle.sh :app:testDebugUnitTest   # clinical parser and danger-sign tests
./scripts/gradle.sh :app:assembleRelease     # signed APKs in app/build/outputs/apk/release
```

The scripts use the JDK, Gradle and Android SDK from the Meeting Timer repo's `.toolchain`. Release signing uses
the Meeting Timer key (kept outside this repo), so the two apps can talk to each other.

Symptom icons are drawn in `design/pictograms/sprites.py`, which renders the three sprite sheets in `app/src/main/assets/sprites`.
The speech model is [Vosk](https://alphacephei.com/vosk/) `vosk-model-small-en-in-0.4` (Apache 2.0).

MedLog 1.0 (the Expo / React Native version) is kept on the `v1` branch.
