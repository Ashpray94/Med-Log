# MedLog: product and build plan

**Status:** Draft for your approval · 25 Sep 2026
**One line:** *Say how you feel. MedLog remembers it, looks after you, and tells your doctor clearly.*

This plan covers what the app is, who it is for, every screen, how voice understanding works, the clinical
model, the doctor page, medicines and reminders (including the Meeting Timer app), nearby help and SOS,
privacy, reliability, testing and the build order. **Section 0 lists the places where your request can't be
built exactly as written, and what I propose instead.** Section 20 lists the decisions I need from you.

---

## 0. Read this first: honest limits and what I propose instead

Some of these limits come from the phone platform, some from the law, and one is about safety. A careful
Apple-quality product faces these limits openly instead of hiding them.

| You asked for | The limit | What I propose |
|---|---|---|
| AI that understands voice "with no mistakes" | No speech or language model is 100% accurate, especially with older voices, accents, dentures, background TV or mixed languages. | Three safety layers: (1) a checked medical word list for the main facts, (2) the AI only fills in extra detail, (3) the app **always reads back what it understood** and asks for a one-tap "Yes". Nothing is saved as fact without that yes. On the doctor page, anything the app worked out for itself is marked ◇, so it can't be taken for something the patient said. |
| "Make a guess" at the ailment from symptoms | An app that tells a patient what disease they have counts as a regulated medical device (FDA in the US, EU MDR, CDSCO in India). A wrong guess shown to a frightened older person can also cause real harm. | The app **does** reason about the symptoms. It uses that reasoning to (a) ask the one or two follow-up questions that matter, (b) run a **danger-sign check** that says "Call a doctor now" or "Call 108 now", and (c) put **"worth checking"** patterns on the doctor page. It **does not show the patient a disease name.** A licensed clinician must approve all rules before release. Decision D3. |
| SOS as a WhatsApp **group conference call** | WhatsApp has no public way for another app to start a call, let alone a group call. The only ways are to automate taps through Android Accessibility, which is fragile and breaks when WhatsApp updates, or to open the chat and let the user tap Call. | The main SOS uses channels that always work: **phone calls to each helper in turn + SMS with location to everyone + an alarm on nearby helper phones**. That works with no internet. A WhatsApp group call is an **optional extra**: MedLog opens the family SOS group and, if enabled, presses the call button through the Accessibility service. It is labelled "extra" and never relied on. Decision D4. |
| "Nothing goes outside the device" **and** Google Calendar reminders | Google Calendar is stored on Google's servers by definition. | **MedLog uses the internet for one thing only: sealed help alerts to helper phones (3.2).** Notes, medicines and the doctor page never go online. Calendar work goes through the **Meeting Timer** app you already have, which already talks to Google. Events show a neutral title ("Medicine time"). Medicine names and doses stay on the phone and are shown by asking MedLog locally. There is also a "No Google at all" mode. Section 12. |
| "Use an image generation model to depict all ailments" | Generating pictures on the phone is slow, gives a different style each time, and can be medically wrong. The reference image is stock art, so we can't copy it. | Generate the whole picture set **once, at design time**, in one consistent style that we define. A person cleans them up as vector art and a clinician checks them. They ship inside the app, so they are fast, sharp at any size and work offline. Section 9. |
| Helpers "may or may not be in Bluetooth range" | Bluetooth reaches about one house. SMS reaches anywhere but can't ring an alarm on the helper's phone or bring back "I'm coming". | **Internet link (13.2):** the same alert also goes through a relay (the open-source ntfy protocol: ntfy.sh or the family's own) as a sealed note that only the paired phone can open. No accounts, no MedLog server. Decision D11. |
| "Find devices nearby which can signal" | A phone can only alert another device that is running software to listen for it. | A free **Helper mode** in the same app goes on family members' or carers' phones and tablets. They are paired once, face to face. At home they are reached with no internet through Bluetooth and Wi-Fi Direct (Google Nearby Connections). Anywhere else they are reached over the internet through a relay that only ever sees sealed notes (13.2), and SMS always goes too. Bluetooth health devices (BP cuffs, oximeters, thermometers) come in Phase 3. |
| Apple-level quality, running only on the phone | Good on-phone AI needs a reasonably modern phone. Many seniors have cheap phones. | Three **device tiers** (Section 7.6). Every phone gets the safe core: checked word list, read-back and tap questions. Stronger phones also get the AI detail layer. The app never gets *less safe* on a weaker phone, only a little less automatic. |
| Platform | The Meeting Timer reminder app is Android-only. WhatsApp and SMS automation and exact alarms are not possible on iOS. | **Android first** (Kotlin, native). iOS gets looked at after v1. Decision D1. |

---

## 1. Product definition

### 1.1 What MedLog is
The simplest possible place to **record how you feel**, **what you ate and took**, and **ask for help**, for
older people and anyone in supportive care. It turns a few spoken words into a complete, accurate medical
record and a **one-page summary a doctor can read in 60 seconds**.

### 1.2 The five jobs (and nothing else)
1. **Tell:** "How do you feel?" Tap a picture or just talk.
2. **Medicines & food:** remind, confirm, never double-dose.
3. **Help:** message people nearby, or SOS.
4. **For the doctor:** one clear page, nothing softened.
5. **How am I doing:** simple trends over weeks and months.

### 1.3 Design principles (every screen is checked against these)
1. **Understood in 2 seconds.** One big question per screen, one main button, pictures *with* words.
2. **Never lose anything.** Autosave everything. There is no "Save" button to forget.
3. **Never guess silently.** The app says back what it heard. Unknown stays "not said".
4. **Always a way back.** A big Home button is on every screen. Undo is on every action.
5. **No hurry.** Nothing times out or vanishes on its own. The only exception is the SOS cancel countdown, which is spoken aloud.
6. **Calm, not scary.** Warm words, and alarms only when they matter. Danger is clear but never dramatic.
7. **Private by construction.** Notes never go online, encrypted storage, no accounts. The internet carries only sealed help alerts.
8. **The same place every time.** Buttons never move, and screens look the same every day.
9. **Short input, deep processing.** The person gives about 10 seconds of input. The app does the detailed work in the background.
10. **Works when things go wrong:** no signal, low battery, a phone restart, a shaking hand, a soft voice.

---

## 2. People we design for

| Person | Situation | What they need most |
|---|---|---|
| **Kamala, 78**: lives with son's family | Diabetes, BP, arthritis, mild memory lapses, reading glasses, uses WhatsApp video calls | "Did I take my tablet?", big text, voice, tell family without shouting |
| **Raghavan, 84**: lives alone | Heart failure, hand tremor, hard of hearing, basic phone | SOS that works, daily "I'm OK", reminders he can hear/feel |
| **Meera, 52**: daughter / carer, lives 20 km away | Manages parents' care remotely, busy job | Missed-medicine alerts, the doctor page before appointments, setup on parent's phone |
| **Arun, 34**: recovering from surgery | Normal user, temporary care needs | Fast logging, pain tracking, medicine schedule, report for follow-up |
| **Dr. Iyer**: OP doctor | 5–7 minutes per patient | One page: what's wrong, what's changed, what's dangerous, meds adherence |

**Empathy notes** (these drive the design):
- Many older users are **afraid of "breaking" the phone** or pressing the wrong thing, so nothing is destructive, and everything can be undone.
- They often **under-report** ("it's nothing") and feel like a burden. The app makes reporting feel normal and quick, and gently prompts: "Anything else bothering you?"
- They may **forget** they already logged something, or already took a pill. The app answers "Yes, you took it at 8:02 this morning".
- A carer often **sets up** the phone. Setup is split into a helper part (detailed) and a user part (almost nothing).
- **Dignity:** words like "burp", "vomit" and "bathroom" are shown plainly and respectfully, and never cutesy.

---

## 3. Platform and architecture

### 3.1 Stack
| Layer | Choice | Why |
|---|---|---|
| Language/UI | **Kotlin + Jetpack Compose**, fully custom design system on top of Material 3 foundations | Native speed, precise control of accessibility semantics and a premium feel. Matches Meeting Timer (Kotlin). |
| Min Android | **8.0 (API 26)**, target latest | Same as Meeting Timer; covers most senior phones in use |
| Widgets | **Jetpack Glance** app widgets + Quick Settings tile + optional persistent notification | Home-screen logging |
| Database | **Room + SQLCipher** (AES-256), key held in **Android Keystore** | Encrypted at rest |
| Scheduling | **AlarmManager exact alarms** (`USE_EXACT_ALARM`) + WorkManager for housekeeping | Reliable medicine times, the same approach Meeting Timer uses |
| Speech-to-text | **Whisper (whisper.cpp) multilingual, quantized**, fed a medical-vocabulary prompt; Android on-device recognizer as a fallback | Offline, handles accents and mixed languages |
| Language understanding | **Rules + medical word list (always)** + **small on-device LLM** (Gemma-class 1B, 4-bit, via llama.cpp / MediaPipe LLM Inference) forced to output strict JSON | Accurate core, rich detail on capable phones |
| Text-to-speech | Android TTS with offline voices; **bundled Piper voice** as a fallback if none installed | Read-aloud must always work offline |
| OCR (medicine strips, prescriptions) | **ML Kit Text Recognition (bundled model)** | Offline |
| Nearby | **Google Nearby Connections** (Bluetooth, BLE, Wi-Fi Direct) + BLE beacon fallback | Helper alerts with no internet |
| Far away | **ntfy-protocol relay** (ntfy.sh by default, or self-hosted), notes sealed with AES-256-GCM using the pairing key | Helper alerts anywhere, with no accounts and no MedLog server |
| Charts & PDF | Custom Compose Canvas charts; `PdfDocument` for the doctor page | Offline, pixel-exact |
| DI / structure | Hilt, multi-module | Testable and keeps the clinical logic isolated |

### 3.2 Internet for one thing only
MedLog declares `android.permission.INTERNET` for a single purpose: **help alerts to helper phones that are out
of Bluetooth range** (13.2). All network code is in one file (`help/Relay.kt`), and it talks only to the relay
address in Settings → SOS. What it sends is a note sealed with the key the two phones agreed face to face, so the
relay sees scrambled bytes going to a random-looking mailbox. The libraries' own usage reporting (ML Kit's
datatransport) is removed from the app, so the relay is the only traffic. The user can turn the internet link off,
and then only Bluetooth and SMS reach helpers.

Everything else that has to leave the phone does so **only through another app, only when the user acts, and
only as the user expects**:
- SMS: sent over the mobile network by the phone's SMS system, not the internet
- Phone calls: through the phone dialer
- WhatsApp: through the WhatsApp app
- Google Calendar: through Meeting Timer
- Sharing the doctor page: through the Android share sheet (print, WhatsApp to the doctor, Bluetooth, and so on)

AI models are delivered with the app through the Play Store (Play Asset Delivery), so MedLog never downloads anything itself.

### 3.3 Modules
```
:app                      shell, navigation, onboarding
:core:design              tokens, components, read-aloud engine, big mode, haptics
:core:data                Room/SQLCipher, repositories, export/import
:core:speech              recording, Whisper, VAD, TTS
:core:nlu                 lexicon, rule parser, negation/number/time parsing, LLM adapter, merger
:core:clinical            symptom catalogue, attribute schemas, follow-up question logic,
                          danger-sign rules, "worth checking" patterns   ← versioned, clinician-signed
:feature:tell             symptom capture (voice + tap + body map)
:feature:log              timeline, edit, undo
:feature:meds             medicines, schedules, dose confirm, refill
:feature:food             food & fluids
:feature:vitals           BP, sugar, temp, SpO2, weight, pulse
:feature:help             nearby messages, SOS, daily "I'm OK"
:feature:doctor           doctor page (screen + PDF), visit notes
:feature:reports          trends and weekly summary
:feature:helper           Helper mode (the same APK in a different role)
:widget                   Glance widgets, QS tile
:integration:meetingtimer local bridge to Meeting Timer
clinical/                 JSON content (catalogue, rules, words) + tests + sign-off records
```

---

## 4. Design system and accessibility

### 4.1 Look and feel
- **Calm, warm, high clarity.** Off-white paper background, deep ink text, one accent colour.
  Rounded 24dp cards, soft depth, and no clutter. Think Apple Health's clarity with larger type.
- **Type:** a humanist sans with open letter shapes (e.g., *Atkinson Hyperlegible*, designed for low vision).
  - Standard: body **20sp**, buttons **22sp**, titles **30sp**
  - Big mode: body **26sp**, buttons **28sp**, titles **38sp**
  - The app still follows the phone's own font size setting and never shrinks text below these sizes.
- **Touch targets:** at least **64dp** as standard and **80dp** in big mode, with at least 16dp between them.
- **Colour means something** and is **always doubled with a shape and a word**, never colour alone:
  - Green ● "OK", Amber ▲ "Call doctor today", Red ■ "Get help now"
- **Contrast:** at least 7:1 for text (WCAG AAA), and a high-contrast theme at 15:1+.
- **Motion:** gentle and short (≤250 ms), turned off with "Less movement" or the phone's own setting.
- **Sound & touch:** a soft confirm tone plus a light vibration on every save, so the user knows it worked without looking.

### 4.2 Words: plain English, reading age about 8
All words come from one controlled list. Examples:

| Never say | Say |
|---|---|
| Symptom / ailment | **How I feel** / **Problem** |
| Log entry | **Note** |
| Submit / Save | *(no button; saves itself)* / **Done** |
| Medication adherence | **Took my medicines** |
| Nauseous | **Feel like vomiting** |
| Syncope / faint | **Fainted / passed out** |
| Dyspnea | **Hard to breathe** |
| Severity 7/10 | **Very bad** (with face picture) |
| Emergency contacts | **My helpers** |
| Notifications permission | **Let MedLog remind you** |
| Error occurred | **That didn't work. Let's try again.** |

Rules: at most **6 words** per button, **one idea per sentence**, no jargon, no abbreviations
(except "BP" and "SOS" with a picture), and questions a person can answer with yes/no or a number.

### 4.3 Accessibility tools built into MedLog (beyond what Android gives)
Turn on **Easy mode** once (during setup or from the home screen) to get all of these. Each can also be switched individually.

1. **Read Aloud button, always on screen.** A large speaker button sits fixed in the bottom-left of *every* screen.
   - Tap: reads the screen in a sensible order: title → question → choices → main button.
   - Tap any item after that to hear just that item.
   - Speech is slower by default (0.85×), and speed and voice can be changed.
   - It also reads aloud automatically when a new screen opens (can be switched off).
   - It works alongside TalkBack but does not need it. Many seniors find TalkBack too hard to use.
2. **Big mode:** bigger text and buttons, one column, and at most 4 choices per screen, with "More" for the rest.
3. **Steady touch (for tremor):** the tap happens when the finger lifts, repeat taps within 0.6 s are ignored, extra-large touch area around each button, no swipe-only or long-press-only actions, no drag.
4. **Hold to confirm** only for the few serious actions: SOS, and deleting a helper. Each also has a tap-and-confirm alternative.
5. **Nothing expires:** no timeouts, and messages stay until dismissed.
6. **Undo everywhere:** a big "Undo" bar for 10 seconds, and deleted notes go to "Removed" for 30 days.
7. **Voice for everything:** every question can be answered by voice or by tap. "Yes", "no", numbers and "stop" work everywhere.
8. **Hearing support:** every spoken prompt is also shown as text. Alerts flash the screen and the camera light (optional) and vibrate in a strong pattern.
9. **Memory support:**
   - "**Did I take my medicines?**" is one tap or spoken, and answers with the time and a photo of the pill.
   - "**What did I tell today?**" gives a simple spoken summary.
   - The **double-dose guard** warns "You already took this at 8:02. Take again?" and tells a helper if confirmed.
10. **Low vision:** high contrast, a colour-blind-safe palette, a "zoom any picture" tap, and optional bold text.
11. **Language:** English plus your chosen Indian languages (D2). Labels can be **shown in two languages** (e.g., English + Tamil) and speech understands mixed speech ("vayiru pain after lunch").
12. **Simple view by the helper:** a helper can hide features the user doesn't need, so a user who only needs "Tell", "Medicines" and "Help" sees only those three.
13. **Lost-proof navigation:** the Home button is always visible, Back always goes one step back, and the app never ends up in a menu it can't get out of.
14. **Left-hand mode:** mirrors the fixed buttons.
15. **Full TalkBack / Switch Access support** for people who already use them: proper labels, reading order, and actions.

### 4.4 Home screen (in the app)

```
┌─────────────────────────────────────┐
│  Good morning, Kamala          ⚙    │
│                                     │
│  ┌───────────────────────────────┐  │
│  │   🎤  Tell how you feel        │  │  ← biggest button
│  └───────────────────────────────┘  │
│                                     │
│  Recent:                            │
│  [🤮 Vomiting ] [🤕 Headache] [😵 Dizzy]│  ← pictograms from the user's own history
│                                     │
│  ┌──────────────┐ ┌──────────────┐  │
│  │ 💊 Medicines  │ │ 🍽 Food       │  │
│  │ Next: 1 pm   │ │              │  │
│  └──────────────┘ └──────────────┘  │
│  ┌──────────────┐ ┌──────────────┐  │
│  │ 👨‍⚕️ For doctor│ │ 📈 How am I   │  │
│  └──────────────┘ └──────────────┘  │
│                                     │
│  ┌───────────────────────────────┐  │
│  │  🆘  Help                      │  │  ← red, always at bottom
│  └───────────────────────────────┘  │
│ 🔊                                 🏠│  ← Read Aloud (fixed) / Home
└─────────────────────────────────────┘
```
(The emoji above are placeholders for the real pictograms.)

---

## 5. All screens

| # | Screen | Purpose | Main action |
|---|---|---|---|
| 1 | **Home** | Everything one tap away | Tell how you feel |
| 2 | **Tell: Listening** | Capture speech | Speak, then "Done" |
| 3 | **Tell: Check** | Read back what was understood | **Yes, that's right** / Change |
| 4 | **Tell: One more question** (max 2) | Fill critical missing facts | Yes / No / number |
| 5 | **Tell: Pick a problem** | Picture grid for people who prefer tapping | Tap a picture |
| 6 | **Tell: Where does it hurt?** | Body map (front/back, zoom) | Tap the body |
| 7 | **Danger sign** | Full-screen, spoken | **Call 108 now** / Call helper |
| 8 | **My notes (timeline)** | Everything by day, pictures + words | Tap to see/edit |
| 9 | **Medicines today** | Each dose as a big card | **I took it** |
| 10 | **Add medicine** | Photo of strip/prescription → OCR → confirm | Done |
| 11 | **Food & water** | Photo or voice, water counter | + Glass of water |
| 12 | **Readings** | BP, sugar, temperature, oxygen, weight | Say or type a number |
| 13 | **Help** | Nearby messages + SOS | Tap a message / SOS |
| 14 | **For doctor** | Preview the one-page summary; choose dates | Share / Print |
| 15 | **After doctor visit** | Voice capture of what the doctor said (maps to the appointment log form you shared) | Done |
| 16 | **How am I doing** | Trends, weekly spoken summary | Choose problem |
| 17 | **Settings** | Easy mode, language, helpers, reminders | Short, plain list |
| 18 | **Helper setup** | Detailed setup for the carer (locked behind a simple PIN) | — |
| 19 | **Helper mode** (on helper's phone) | Receive alerts, see missed doses, acknowledge | "I'm coming" |
| 20 | **Add my old reports** | Photo/PDF of past reports & prescriptions → history (from v1, see Section 21) | Done |

---

## 6. Home-screen widget

### 6.1 Sizes
| Size | Content |
|---|---|
| **Small 2×2** | 🎤 "Tell how I feel" (big) + 🆘 Help (small, bottom) |
| **Medium 4×2** | 3 recent problem tiles + 🎤 "Something else" + next medicine time |
| **Large 4×4** | 6 recent problem tiles + 🎤 + next medicine card with **I took it** + 🆘 |

A **Quick Settings tile** ("Tell MedLog") and an optional **always-there notification** with the same
buttons make it reachable from the pull-down shade too, including on the lock screen where the phone allows it.

### 6.2 Which problems appear on the widget
Each problem gets a score:
`score = Σ over its notes of e^(−age_in_days / 3)` (a note from today counts fully and one from a week ago counts about 10%)
- **Ongoing problems** (logged in the last 48 h) are always shown first, with a small badge: "**3rd time today**".
- Problems marked by the helper as **"watch this"** (e.g., "chest pain" for a heart patient) are pinned.
- Ties go to the most recent. The order changes **at most once a day** unless something new is logged, so the tiles don't jump around under the user's finger (principle 8).
- On a new install, the widget shows the 6 most common problems for that person's age group until real history exists.

### 6.3 Tapping a tile (the entire input)
1. Tap **🤮 Vomiting** on the widget.
2. MedLog opens **straight to Listening**, with a large vomiting picture and the words
   **"Vomiting. Tell me about it."** (spoken too). Target: **under 0.8 s** from tap to listening.
3. User speaks, e.g.: *"Twice after lunch, yellowish, my stomach is also paining."*
4. They tap **Done**, or just stop talking, which is detected after 2 s of silence.
5. **Check screen** (spoken): "**Vomited 2 times, after lunch, yellow. Also: stomach pain.** Is that right?"
   → **[ Yes, that's right ]** (huge) · [ Change ] · [ Say again ]
6. At most **2 follow-up questions**, only if they are clinically important and missing (Section 8.4), e.g.:
   "**Was there any blood?**" → [Yes] [No] (or just say it).
7. Saved: tone + vibration + "**Saved. Get well soon.**" → back to where they were.

**Budget:** ≤ 3 taps, ≤ 30 seconds. Saying nothing more than "I vomited" is fine. Missing details stay "not said".

If they tap **Change**, the understood facts appear as **big chips** ("2 times", "after lunch", "yellow",
"stomach pain"). Tap a chip to fix it by voice or with simple choices, or remove it with ✕.

---

## 7. Voice understanding pipeline

### 7.1 Steps
```
Mic → noise reduction → voice activity detection → Whisper (on-device)
    → transcript + word confidence
    → [A] rule parser + medical lexicon       (always)
    → [B] on-device LLM, strict JSON schema   (Tier A/B phones)
    → [C] merger + validator + clinical rules
    → read-back sentence + follow-up questions
    → user confirms → saved
    → background: episode linking, trend update, danger check, widget refresh
```

### 7.2 [A] Rule parser (the safety core)
- **Medical lexicon:** every catalogue item (Section 8) has names, plain words, synonyms, common
  mispronunciations, and regional/colloquial terms per language ("throwing up", "puked", "ulti", "vanthi",
  "vomit panniten"). About 150–400 phrases per problem.
- **Negation detection** (NegEx-style, for every supported language): "**no** blood", "**not** dizzy", "**never** fainted", "without fever".
  Negation errors are the most dangerous mistake, so they get their own test suite (Section 17).
- **Numbers & counts:** "twice", "3–4 times", "two three times" (a range), "one and a half".
- **Time:** "after lunch", "since yesterday night", "for 3 days", "every morning", "an hour ago", resolved to actual times.
- **Body site:** "left side of my stomach", "behind the eye", "lower back going down the leg", mapped to body-map regions + side.
- **Severity words:** "a little", "very bad", "unbearable", "worst ever", mapped to the 0–10 scale.
- **Medicine names:** matched against **the user's own medicine list** first (fuzzy match on sound), then common names.

### 7.3 [B] On-device LLM (detail layer)
- Receives the transcript, the problem the user tapped, and the **exact JSON schema** for that problem's details.
- **Grammar-constrained output:** it can only produce valid fields and allowed values, so no free-form made-up text.
- Its job is the rich detail: "yellowish-green, bitter" becomes `colour: yellow-green, taste: bitter`, and it links associated problems.
- It **cannot overrule** the rule parser on: problem identity, negations, numbers, danger words. If the two disagree, the app **asks the user** in plain words.

### 7.4 [C] Merger and validator
- Every field gets a **source**: `said` (in the user's words), `inferred` (worked out by the app), or `asked` (answered a follow-up).
- Fields below the confidence threshold are **not guessed**. They are dropped or asked about.
- Impossible values are rejected (temperature 390, BP 15/9, a vomiting count of 200).
- The original **transcript is always stored** with the note. The **audio clip** is stored for 30 days (optional, on by default) so a helper or doctor can replay it.

### 7.5 Vocabulary biasing
Whisper gets a starting prompt built from the tapped problem, the user's medicines and their past problems.
This greatly improves recognition of medicine names and medical words.

### 7.6 Device tiers
| Tier | Phone | What runs | Result |
|---|---|---|---|
| **A** | ≥ 6 GB RAM, 2021+ chip | Whisper small + LLM 1B | Full automatic detail, read-back in ≤ 3 s |
| **B** | 3–6 GB | Whisper base + LLM 0.5B (or LLM off on older chips) | Good detail, read-back in ≤ 6 s |
| **C** | < 3 GB | Whisper tiny/base + rules only | Core facts by voice, rest by 1–3 tap questions |

Tier detection happens automatically on first run with a short benchmark. **The safety features are the same on every tier.**

Storage: about 60 MB for the app, plus 150–900 MB for models depending on tier. On a phone that is short of space, the app says so plainly during setup.

---

## 8. Clinical model

### 8.1 Every problem is recorded the way a doctor takes a history (SOCRATES + extras)
| Field | Meaning | Example |
|---|---|---|
| **Problem** | Catalogue item (mapped to ICD-11 code) | Vomiting |
| **Site** | Body-map region + side, depth | Upper middle stomach |
| **Onset** | When it started, sudden or slow | Today 1:30 pm, sudden |
| **Character** | What it's like (per-problem list) | Burning / cramping / sharp / dull / throbbing / pressing |
| **Radiation** | Does it spread? Where? | To the back |
| **Associated** | Other problems at the same time (linked notes) | Stomach pain, dizziness |
| **Time pattern** | Constant / comes and goes / how often / how long each time | 2 times in 3 h |
| **Makes worse / better** | Triggers & reliefs | Worse after food; better lying down |
| **Severity** | 0–10 with faces + words; impact on daily life | 6 "Bad"; couldn't eat lunch |
| **Context** | After food / medicine / activity / fall / time of day | 30 min after lunch; after starting Metformin |
| **Problem-specific** | Extra fields per problem (below) | Colour yellow; no blood |
| **What I did** | Medicine taken, rest, went to hospital | Took Pan-D at 2 pm |
| **Source flags** | said / inferred / asked, confidence | |

**Problem-specific detail examples:**
- **Vomiting:** count, colour, **blood / coffee-ground look**, food or liquid, projectile, can keep water down?, when last passed urine
- **Loose motions:** count, **Bristol stool picture scale**, **blood / black stool**, mucus, urgency
- **Cough:** dry/wet, phlegm colour, **blood**, worse at night, weeks duration
- **Fever:** **measured temperature** (or "felt hot"), chills/shivering, sweating, pattern through the day
- **Chest pain:** pressure/tightness vs sharp, **spreads to arm/jaw/back**, **with sweating/breathlessness**, on exertion or rest
- **Breathing:** at rest / walking / lying flat, number of pillows, ankle swelling, oxygen reading
- **Fall:** **hit head?**, **on blood thinners?**, could get up alone?, how long on floor, pain where, dizziness before
- **Headache:** **sudden & worst ever?**, one side, with vision change/vomiting/stiff neck, morning
- **Rash:** photo, itchy/painful, spreading, blisters, new medicine recently
- **Dizziness:** spinning vs light-headed, on standing up, with falls
- **Confusion (reported by helper):** new or usual, sudden, with fever
- **Urine:** burning, frequency, blood, **none for how long**
- **Swelling:** where, one side or both, painful, red/hot
- **Sleep, mood, memory:** simple scales; **thoughts of self-harm** handled with care (see 10.3)

### 8.2 Problem catalogue: all OP departments
About **260 problems** in total, organised by department (for doctors) *and* by plain groups (for users:
"Head", "Tummy", "Chest & breathing", "Skin", "Pain", "Toilet", "Mind & sleep", "Injuries", "Women's health", "Other").
Each has a pictogram. Representative list:

| Department | Problems (plain-English labels) |
|---|---|
| **General medicine** | Fever, chills/shivering, tiredness, weakness, weight loss, weight gain, night sweats, feeling unwell, loss of appetite, too thirsty, dehydration |
| **Cardiology** | Chest pain, chest tightness, heart racing/pounding, irregular heartbeat, breathless on walking, breathless lying flat, swollen ankles, fainting, high BP reading, low BP / dizzy on standing |
| **Pulmonology** | Cough (dry), cough (with phlegm), coughing blood, hard to breathe, wheezing, low oxygen reading, snoring/stops breathing at night |
| **Gastroenterology** | Stomach pain, acidity/heartburn, feel like vomiting, vomiting, vomiting blood, loose motions, constipation, blood in stool, black stool, bloating/gas, burping, hiccups, difficulty swallowing, yellow eyes/skin |
| **Neurology** | Headache, migraine, dizziness (spinning), light-headed, fainting, fits/seizure, numbness, tingling, weakness one side, face drooping, speech trouble, confusion, memory problems, shaking/tremor, balance problems |
| **ENT / Audiology** | Earache, ear discharge, ringing in ears, hearing loss, blocked ear, sore throat, hoarse voice, runny nose, blocked nose, sneezing, nosebleed, sinus pain, loss of smell/taste |
| **Ophthalmology** | Eye pain, red eye, watery eye, blurred vision, sudden vision loss, double vision, dry eyes, floaters/flashes, itchy eyes |
| **Dental** | Toothache, bleeding gums, mouth ulcer, loose tooth, denture pain, dry mouth |
| **Dermatology** | Rash, itching, hives, blisters, sunburn, skin wound not healing, bruise, pressure sore/bed sore, new/changing mole, hair loss, nail problem, fungal infection |
| **Orthopedics / Rheumatology** | Back pain, neck pain, knee pain, hip pain, shoulder pain, joint swelling, stiffness in morning, muscle cramps, broken bone, sprain, fall, can't walk properly, gout attack |
| **Endocrinology / Diabetes** | Low sugar reading/shaky & sweaty, high sugar reading, passing urine often, very thirsty, foot numbness, foot wound, feeling cold/hot all the time |
| **Nephrology / Urology** | Burning urine, passing urine often, can't pass urine, blood in urine, leaking urine, no urine for long, pain in side (kidney), swelling of face in morning |
| **Obstetrics & Gynecology** | Period pain, heavy bleeding, bleeding after menopause, white discharge, breast lump, breast pain, hot flushes, pregnancy concerns |
| **Psychiatry / Mind** | Feeling low, worried/anxious, can't sleep, sleeping too much, feeling alone, not enjoying things, confused thinking, seeing/hearing things, thoughts of self-harm |
| **Oncology / Hematology** | Lump anywhere, bleeding easily, bruising easily, unexplained weight loss, swollen glands, pale/very tired |
| **Allergy** | Sneezing fits, itchy eyes, swelling of lips/face, allergic reaction to food/medicine |
| **Injuries / Urgent care** | Cut, bleeding, burn, bruise, swelling, sprain, broken arm/leg, head injury, animal bite, insect sting, choking episode |
| **Medicine side effects** | Felt bad after a medicine (linked to that medicine), rash after medicine, dizziness after medicine |
| **Daily living** (geriatric) | Trouble walking, near-fall, trouble eating, trouble with toilet, bladder/bowel accidents, not drinking enough, sleep problems, pain at night |

The complete list with codes, lexicon, detail schema and follow-up questions lives in `clinical/catalogue.json`.
It is version-controlled, and every change needs clinician sign-off.

### 8.3 Episodes: turning repeated notes into one story
- Notes of the same problem within **48 h** (the window can be set per problem) join one **episode**.
  "Vomiting since Monday: 11 times in 4 days".
- An episode **ends** when the user says "I'm better now" or after 72 h with no new notes. The app asks
  "Is your vomiting better now?" once, gently.
- Associated problems logged together are **linked**, so the doctor sees "vomiting + stomach pain + dizziness" as one event.

### 8.4 Follow-up questions: how the app decides what to ask
The clinical rules list, for each problem, **the key missing facts that change what a doctor would do**,
in priority order. For example, for vomiting: blood? → can keep water down? → urine in last 8 h?
- At most **2 questions** per note, never more.
- Danger-sign questions come first.
- The same question is not repeated within 12 h if already answered ("Still no blood?" is asked only if the episode continues).
- If the user says "stop" or "I don't know", the app stops, saves what it has and thanks them.

### 8.5 "Worth checking": the app's reasoning, kept away from the patient
Pattern rules combine problems, timing, medicines and readings into **observations for the doctor**.
Examples:
- "Vomiting 4×/day and low water intake for 2 days → **possible dehydration**."
- "Dizziness began 3 days after **Amlodipine dose increased** → **possible medicine effect**."
- "Loose motions + started **Metformin** 5 days ago."
- "Breathless lying flat + ankle swelling + weight up 2 kg in 3 days → **fluid build-up**."

These are shown **only on the doctor page**, labelled "◇ Pattern noticed by MedLog, not a diagnosis".
The patient sees care guidance instead, e.g., "Sip water often. Call your doctor if you can't keep water down."

---

## 9. Pictograms

### 9.1 Style
- **One consistent character**, available in 4 variants the user picks at setup: older woman, older man,
  adult woman, adult man, each in a range of skin tones. Seniors identify more easily with a figure that looks like them.
- Flat, soft shapes and a calm palette, with red **only** for pain, blood or danger markers, used consistently.
  Thick outlines readable at 48dp, and still clear at 200dp in big mode.
- Each pictogram = **figure + where** (glow on the body part) + **what** (a simple motion mark).
- Always shown **with its word label** underneath.

### 9.2 Production pipeline
1. **Style guide and 10 master drawings** made by hand (or with Figma), which you approve.
2. **Batch generation** of the remaining ~250 using an image model, prompted from the master drawings for a consistent style.
3. **Vector clean-up** by a designer into SVG → Android VectorDrawable (tiny, sharp at any size, themeable for high contrast).
4. **Clinical check:** is the body part right, is it unambiguous, is it respectful.
5. **Senior recognition test:** ≥ 85% of test users name each picture correctly within 3 seconds.
   Pictures that fail are redrawn.
6. Shipped inside the app, with nothing generated on the phone.

### 9.3 Body map
Front/back full-body drawing with about 60 tappable regions (head split into forehead, temples, eyes, ears,
jaw, and so on). Tap to zoom into hands, feet and face. Selected areas glow, and pain can be marked as "spreads
from here to there" by tapping two points.

---

## 10. Safety: danger signs

### 10.1 How it works
- A **deterministic rule set**, not AI, runs on every note, reading and missed dose.
- The rules are based on published red-flag guidance (e.g., NICE, WHO, AHA stroke/heart attack signs)
  and **adapted and signed off by a licensed doctor** (D6).
- **Recall first:** in testing, the rules must catch **100%** of the danger cases. Some over-alerting is accepted, but missed dangers are not.

### 10.2 Examples (to be finalised with the clinician)
| Level | Trigger | What the user sees and hears |
|---|---|---|
| **■ Red: get help now** | Chest pain/pressure with sweating, breathlessness, or spreading to arm/jaw | "**This could be serious. Call 108 now.**" [Call 108] [Call Meera]. Helpers are alerted automatically |
| ■ Red | Face drooping, arm weakness or slurred speech (**FAST**) | Same, plus "Note the time it started: 10:42" (recorded automatically) |
| ■ Red | Vomiting blood / coffee-ground; black tarry stool | Same |
| ■ Red | Severe difficulty breathing; oxygen < 90% | Same |
| ■ Red | Fainted / fit; fall **with head injury while on a blood thinner**; can't get up | Same |
| ■ Red | Sudden "worst ever" headache; sudden vision loss | Same |
| ■ Red | Sugar < 70 mg/dL with confusion or sweating | "Take sugar now: 3 teaspoons in water or juice" + call helper |
| ■ Red | Thoughts of self-harm | Calm, caring screen, national helpline + helper call. Never an alarm sound |
| **▲ Amber: call doctor today** | Vomiting/loose motions ≥ 6 times in 24 h, or can't keep water down; no urine 8–12 h; fever ≥ 38 °C for 2+ days or any fever with new confusion; BP ≥ 180/110 without symptoms; sugar > 300; new swelling of one leg; a medicine missed 2 doses in a row (for critical medicines) | "**Please call your doctor today.**" [Call Dr. Iyer] + helper informed |
| **● Green** | Everything else | Saved normally, plus care tips where relevant |

**Emergency screens never ask questions first.** They show help first, then collect details afterwards if the person is able.

### 10.3 Tone
Serious but calm. Plain words and one clear action, spoken slowly. No flashing red walls, no sirens
(except SOS on helper phones). For mental-health topics the app uses warmth, never an alarm.

---

## 11. The doctor page

### 11.1 Principles
- **One A4 page** (a second page appears only if the doctor wants the full notes).
- **Most important first.** Concerns are ranked by clinical urgency, not by date.
- **Exact numbers and dates, and nothing softened.** Write "Vomited 11 times in 4 days, rising (2 → 4/day)", never "some vomiting".
- **The patient's own words are quoted** for key items ("*it felt like an elephant on my chest*").
- **Negatives that matter are stated:** "No blood in vomit (asked 23 Sep)".
- Every inferred item is marked **◇**, so said and inferred are never mixed.
- Readable in **60 seconds**. A doctor tests it in usability testing (Section 17).

### 11.2 Layout
```
┌──────────────────────────────────────────────────────────────────────┐
│ MedLog: Patient summary          22 Sep – 25 Sep 2026 (4 days)       │
│ Kamala R · F · 78 y · DOB 12 Mar 1948 · Blood O+ · Hospital ID 4471   │
│ ALLERGIES: Penicillin (rash)   Conditions: Type 2 diabetes, HTN, OA   │
├──────────────────────────────────────────────────────────────────────┤
│ ⚠ NEEDS ATTENTION                                                    │
│ ■ 1. Vomiting 11× in 4 days, increasing (2→4/day). Yellow, no blood.  │
│      Could not keep water down on 24 Sep. Urine only once on 24 Sep.  │
│ ▲ 2. Dizziness on standing, 5× since 22 Sep; one near-fall 24 Sep.    │
│      ◇ Began 3 days after Amlodipine increased 5→10 mg (19 Sep).      │
│ ▲ 3. Missed Metformin 3 of 8 doses (vomiting).  Sugar 64 on 25 Sep AM.│
├──────────────────────────────────────────────────────────────────────┤
│ TIMELINE   22   23   24   25      (■ severe ▲ moderate ● mild)        │
│ Vomiting   ●●   ▲▲▲  ■■■■ ▲▲                                          │
│ Stomach pn ●    ▲    ▲    ●                                           │
│ Dizziness       ●●   ▲▲   ●                                           │
├──────────────────────────────────────────────────────────────────────┤
│ DETAILS (per problem: site · character · timing · triggers · relief) │
│ Vomiting: after meals (9/11), yellow-green, bitter; relieved by rest. │
│ Stomach pain: upper-middle, burning, 5/10, worse after food. "like    │
│   fire after eating"                                                  │
├──────────────────────────────────────────────────────────────────────┤
│ MEDICINES           dose        taken     notes                        │
│ Metformin           500 mg BD   5/8 (63%) missed due to vomiting       │
│ Amlodipine          10 mg OD    4/4       ↑ from 5 mg on 19 Sep        │
│ Pantoprazole (PRN)  40 mg       taken 3×  self-started 23 Sep          │
├──────────────────────────────────────────────────────────────────────┤
│ READINGS  BP 104/62 (25 Sep, standing) ↓ from 138/84 avg · Sugar 64 ↓  │
│           Temp 37.1 · Weight 58.2 kg (−1.1 kg in 4 days)              │
├──────────────────────────────────────────────────────────────────────┤
│ FOOD & WATER: ~2 glasses/day on 23–24 Sep (usual 6). Appetite poor.   │
├──────────────────────────────────────────────────────────────────────┤
│ PATIENT'S QUESTIONS: "Can I stop the new BP tablet?" "Is this the     │
│ sugar tablet causing it?"                                             │
├──────────────────────────────────────────────────────────────────────┤
│ Patient-reported via MedLog. ◇ = pattern noticed by the app, not a   │
│ diagnosis. Full notes: page 2.        Generated 25 Sep 2026 09:14     │
└──────────────────────────────────────────────────────────────────────┘
```

### 11.3 Getting it to the doctor
- **Show on phone** (a large, zoomable screen view the doctor can scroll).
- **Share PDF** through the Android share sheet (WhatsApp to the doctor, email, Bluetooth, or Nearby Share).
- **Print** via Android print (clinic Wi-Fi printer).
- The page is prepared automatically **the evening before** any appointment in the calendar, and the user is reminded: "Your doctor page is ready for tomorrow."

### 11.4 After the visit (your appointment-log form)
A 30-second voice capture: "What did the doctor say?" It fills in the same fields as the form you shared:
date/time, doctor, who went with you, reason, who you saw, what happened (tests, injections), referral
(to whom, for what, when, referral number), new prescription (what for, dose), next appointment, and questions for next time.
- A new prescription starts the add-medicine flow and its reminders.
- A next appointment or test is added to the calendar and to Meeting Timer.
- "Questions for next visit" are carried onto the next doctor page.

---

## 12. Medicines, food, reminders, Google Calendar and Meeting Timer

### 12.1 Adding medicines
- **Photo of the strip or the prescription** → offline text recognition → "Is this **Metformin 500 mg**?" → how often (picture choices: 🌅 morning ☀️ afternoon 🌙 night, before/after food) → how many days → done.
- The pill is photographed, and **that photo is shown at every reminder** so the user recognises the right tablet.
- **Pill count & refill:** "You have about 5 days of Metformin left." The helper is told as well.
- **Critical medicine flag** (blood thinners, insulin, heart, seizure medicines): stronger reminders and faster helper alerts.
- As-needed medicines (painkillers, antacids) are logged by voice ("took a paracetamol"). Their use counts appear on the doctor page.
- **Not in v1:** drug-interaction checking. It needs a licensed, maintained drug database. We will not ship a half-accurate version.

### 12.2 Dose flow
At dose time: big card → **pill photo, name, dose, "after food"**, spoken aloud →
**[ I took it ]** · [ Remind me in 10 min ] · [ I'll skip it ] (asks a simple "why?" by tap: feeling sick / ran out / forgot / other).
- **Double-dose guard:** if already taken, the app shows the time and a photo and asks before recording again.
- **Escalation:** +10 min: second reminder, louder. +30 min (+15 for critical medicines): helper gets "Kamala hasn't taken her 8 pm medicines", and can call with one tap.

### 12.3 Food and water
- Voice: "Had two idlis and coffee" → items + time. Or snap a photo (stored locally; no calorie AI in v1).
- **Water counter:** one big "+ glass" button. The app gives gentle nudges if intake is low **and** there is vomiting, loose motions or fever.
- Food timing is linked to symptoms for patterns ("vomiting after meals 9 of 11 times").
- Optional **diabetic mode:** meal timing next to sugar readings.

### 12.4 Integration with Meeting Timer (your repo)
**What I found in `D:\Code\Mockup Pro\Google calendar timer`:** Meeting Timer 2.4 is a Kotlin Android app
(`com.suryaprakash.meetingtimer`, minSdk 26) plus an Electron Windows app. It signs into Google with OAuth,
reads events through the Calendar REST API, arms exact alarms, shows a floating overlay card with
Join / Snooze / Dismiss, sounds the alarm on the alarm channel (so it rings in silent mode), and syncs decisions
across devices through a **private extendedProperty flag** on the event (`Google.writeFlag`).

**Design (recommended):**
```
MedLog (no calendar or Google access of its own)
   │  1. Schedule changes → local, signature-protected ContentProvider + broadcast
   ▼
Meeting Timer
   │  2. Creates/updates events in a dedicated "MedLog" calendar in the user's Google account
   │     title: "Medicine time" (neutral); private extendedProperty: {medlog: doseId}
   │  3. Reminds using its existing engine, with a new "Medicine" card variant
   │  4. "I took it" → tells MedLog locally → MedLog records it
   ▼     (and sets the event flag so Meeting Timer on other devices stops ringing)
Google Calendar (visible on family members' shared calendar if the user chooses)
```
**Changes needed in Meeting Timer:**
1. A **local source bridge**: a `ContentProvider` client that reads MedLog's schedule (names, doses, photo) with a
   **signature permission**. This needs both apps signed with **the same release key** (D5).
2. A **"Create MedLog calendar"** step (Calendar API `calendars.insert`) plus creating recurring events.
3. A **Medicine card** variant: appears **at** dose time (not 5 minutes early), shows the pill photo, large type, reads aloud,
   and has buttons **I took it / 10 min / Skip** instead of Join / Snooze / Dismiss.
4. Recognising events that carry the `medlog` private property, so they are never treated as meetings.
5. Doctor appointments and tests from MedLog appear as normal Meeting Timer reminders (lead time 1 day + 2 hours, with travel time).

**Backup alarms in MedLog:** MedLog arms its own backup alarm 2 minutes after each dose time. If Meeting Timer
hasn't reported that it showed the reminder (it may be uninstalled, killed by the phone maker, or signed out of
Google), MedLog shows the reminder itself. **This way a dose is never missed because another app failed.**

**"No Google" mode:** Meeting Timer reads the schedule straight from MedLog with no Google Calendar, which keeps everything on the phone.

---

## 13. Help: nearby messages and SOS

### 13.1 Helpers
- A helper is a family member, neighbour or carer. Each helper has a name, photo, phone number, and relationship,
  and is marked as a **nearby device** (their phone runs MedLog Helper mode) and/or an **SOS contact**.
- **Pairing** happens face to face: the helper scans a QR code on the user's phone. Keys are exchanged for encrypted messages. There are no accounts.
- **Helpers talk to each other** (2.6): the person's phone gives every paired helper a shared family key, and helper phones share an
  encrypted family chat the person's phone never reads. When one helper answers ("I'm coming"), the others see it automatically.
- **"How are you?" from a helper** (2.6): the person's phone shows four big answers; the answer goes back to that helper.
- **Setup file** (2.6): the first-time setup can be saved and loaded on a new phone, or skipped.
- **Updates from inside the app** (2.7): MedLog reads latest.json from the public medlog-app downloads repository, downloads
  the file for the phone, checks its SHA-256 and that it is signed by the same key, then Android asks to confirm.

### 13.2 Nearby messages
The **Help** screen shows big message tiles (pictogram + words), which the helper can reorder or edit:
> 🙋 **Please come** · 💧 **I need water** · 🚽 **Help with bathroom** · 💊 **Bring my medicine** ·
> 🤒 **I don't feel well** · 🍽 **I'm hungry** · 📞 **Call me** · ✍ **Custom** (typed or **recorded voice**)

**Sending:** Tap → "Sending to Ravi and Meera…" → **"Ravi saw it ✓, he's coming"** (the helper taps "I'm coming" / "5 minutes" / "Can't now").
- **Transport, both at once:**
  - **Nearby:** Nearby Connections (Bluetooth/Wi-Fi Direct, no internet, whole-home range). Helper phones listen in the background.
  - **Far away:** the internet link. The person's phone leaves the sealed note in the helper's mailbox on the relay; the helper's
    phone keeps one connection open to it (in the same small background service) and gets it in about a second. Replies
    ("I'm coming") come back the same way.
  - **How it's kept safe and simple:**
    - Sealed with AES-256-GCM using the pairing key. The mailbox names are worked out from that key (HMAC-SHA256), so
      pairing needs no extra step and nobody else can find the mailbox. Phones paired before 2.5 get it automatically.
    - The relay address travels with the pairing, so both phones always use the same relay.
    - The same message arriving both ways rings once. The helper's phone says "got it" as soon as the alert is on screen,
      so the person sees "Ravi's phone got it" even before Ravi answers.
    - A phone that was switched off picks up missed alerts when it comes back (the relay keeps them 12 hours). One older
      than 30 minutes shows as a normal notification with the time it was sent, not an alarm at 3 am.
    - Voice messages ride along; if one is too big for the relay, the words alone are sent.
- **If nobody acknowledges in 60 s** → SMS to the same helpers → after 3 min, "Nobody answered. Call Meera?" (one tap).
- The helper's phone shows a full-screen alert with a distinct sound, even in silent mode for "Please come" or "I don't feel well".
- A **home tablet** can run Helper mode as a permanent room alert (for night carers).

### 13.3 SOS
- **Trigger:** the red **Help → SOS** button (hold 2 s, or tap then confirm), **or press power 5×** (Android's own emergency
  gesture, which we tie into where the phone allows), **or say "Help MedLog"** while the app is open.
- **10-second cancel countdown**, spoken ("Calling for help in 10… say stop or tap Cancel").
- **Then, all at once:**
  1. **SMS to all SOS helpers:** "SOS from Kamala. She needs help. Location: [maps link from GPS, taken on the phone]. Last note: chest pain 10:42." (sent by SMS with no internet; the phone creates the maps link without going online)
  2. **Alarm on all nearby helper devices.**
  3. **Phone call cascade:** call helper 1 on speaker. If there's no answer in 25 s, call helper 2, and so on. After the list is exhausted, **offer the emergency number** (108/112 for India; set by country, D7).
  4. **Optional:** open the family **WhatsApp SOS group** and start a group call via Accessibility automation (D4). If automation fails, the chat stays open with a big spoken prompt "Tap the phone button at the top".
- **Afterwards:** the event is logged with times, who answered and location, and appears on the doctor page if it was health-related.

### 13.4 Looking out for the user
- **Daily "I'm OK":** at a chosen time (e.g., 10 am) the app asks "Good morning! How are you today?" → 😊 Good / 😐 OK / 😟 Not well.
  If nobody answers in 2 h, helpers are told gently: "Kamala hasn't checked in today."
- **Fall detection** (Phase 3, optional): a phone/watch sensor detects a sudden fall followed by stillness. It asks "Did you fall? Are you OK?", and if there's no answer in 60 s it starts SOS. False alarms are cancelled by voice.
- **Gentle nudges:** water when unwell, "Is your vomiting better?", a medicine refill, "Doctor visit tomorrow: your page is ready".
- **Weekly summary** spoken on Sunday: "This week you felt dizzy 3 times, fewer than last week. You took 96% of your medicines. Well done."

---

## 14. How am I doing (reports)

- **Per problem:** a chart of how often and how bad over 1 week / 1 month / 3 months / 1 year, with plain captions: "**Less often than last month.**"
- **Time-of-day pattern:** "Your headaches are mostly in the morning."
- **Links (counts, not claims):** "Stomach pain came after meals 9 of 11 times." "Dizziness 4 of 5 times within 1 hour of your BP tablet."
- **Medicines:** taken % by medicine and by time of day (e.g., "evening doses missed most").
- **Readings:** BP/sugar/weight charts with the target range shaded (targets set by the doctor or helper).
- **Export:** a monthly report PDF for the helper or doctor, and the full data as an encrypted file for backup or a new phone.

Charts use large labels, at most 2 lines per chart, and direct labels instead of legends. Every chart has a spoken summary.

---

## 15. Data, privacy and security

- **Everything stays on the phone** in an encrypted database (SQLCipher, AES-256). The key lives in Android Keystore hardware where available.
- **Internet only for sealed help alerts** (3.2), **no analytics, no crash reporting to servers, no ads, no accounts.**
- **Android cloud backup is off** (`allowBackup=false`), so health data never goes to Google Drive silently.
- **Backup & move to new phone:** an encrypted backup file (password or helper QR), saved wherever the user chooses (SD card, computer, or Drive if *they* choose). The app reminds the helper monthly.
- **App lock:** optional (fingerprint/PIN), **off by default** for the senior. The helper chooses. The SOS and Help screens work even when locked.
- **Helper access:** helpers see alerts and missed doses, not the full diary, unless the user allows it (a simple "Share my notes with Meera: Yes/No").
- **Audio clips** are kept 30 days, then deleted (setting). Transcripts are kept.
- **Data rights:** "Delete everything" is in Settings, behind a helper PIN plus a spoken confirmation, and it is a real, permanent wipe.
- **Law:** designed for India's **DPDP Act 2023**, and in line with GDPR/HIPAA principles (the app keeps no server data, so most obligations reduce to local security and consent).

---

## 16. Reliability engineering (Apple-level "it just works")

| Risk | Mitigation |
|---|---|
| Phone makers killing background apps (Xiaomi, Vivo, Oppo, Realme, Samsung) | Exact alarms (not polling) wake the app; a **guided per-brand setup** screen for battery settings with pictures; a daily self-test that alerts the helper if reminders were blocked |
| Phone restart / app update / time change / time zone travel | Re-arm all alarms on boot, package replaced, time and time zone change (same receivers Meeting Timer uses) |
| Meeting Timer missing or failing | MedLog backup alarms (12.4) |
| No mobile signal during SOS | Nearby helper devices still work; SMS is queued and retried; the screen says honestly "No signal. Trying again." |
| Low battery | At 15%: "Charge your phone" + helper told; SOS and reminders still work down to shutdown |
| Speech model fails / slow phone | Instant fallback to picture choices; nothing blocks saving |
| Corrupted database | Write-ahead logging, daily local snapshot (encrypted), automatic self-repair |
| Accidental taps | Undo, steady touch, confirm only for serious actions |
| Storage full | Warn early; audio clips removed first; notes never removed |
| Clock/date wrong | Doses scheduled against the real clock, with a warning if the phone's time looks wrong |

**Targets:** crash-free sessions ≥ 99.9%; **zero missed dose reminders** in the 30-day device-farm soak test;
widget tap to listening ≤ 0.8 s; cold start ≤ 1.2 s on a Tier C phone; SOS start to first SMS sent ≤ 3 s after the countdown.

---

## 17. Quality and testing

### 17.1 Speech & understanding
- **Test set:** ≥ 3,000 recorded utterances from people aged 60–90 in English + each launch language, including mixed speech,
  soft voices, dentures, TV noise, and fan noise. Collected **with consent** from volunteers.
- **Targets:**
  | Measure | Target |
  |---|---|
  | Problem identified correctly | ≥ 97% |
  | Negation correct ("no blood") | ≥ 99.5% |
  | Numbers/counts correct | ≥ 98% |
  | Medicine name correct (user's list) | ≥ 97% |
  | **Danger sign missed after read-back** | **0** |
  | Wrong fact saved **after user said Yes** (tested in usability) | < 1% |
- Every clinical rule and every catalogue item has automated tests, and changes can't be merged without them.

### 17.2 Clinical
- A licensed physician (and a geriatrician if possible) reviews the **catalogue, questions, danger signs, patterns and the doctor page**.
- **Case tests:** 200 written clinical cases (e.g., "78 F, vomiting + dizziness on day 3 of new BP tablet") run through the app. The doctor checks the output.
- **Doctor page test:** 5 OP doctors read 10 pages each. They must find the top concern in ≤ 30 s, with ≥ 90% agreement on what matters most.

### 17.3 People
- **Usability tests with ≥ 12 seniors (65–90)**, including people with tremor, low vision, hearing loss and mild memory problems, plus 5 carers.
- **Pass mark:** ≥ 95% complete "log a problem from the widget" **unassisted** after one demonstration, in ≤ 30 s. ≥ 90% find and use SOS without help. Each pictogram named correctly by ≥ 85%.
- Rounds at the Figma prototype stage, alpha and beta. **Anything that confuses two people gets redesigned.**

### 17.4 Devices
A device lab of Pixel, Samsung (A-series), Xiaomi/Redmi, Vivo, Oppo, Realme, Motorola, and one 3 GB RAM phone,
running Android 8 to 16, tested with Doze and battery savers, 30-day alarm soak, reboot/update/time-change tests, and TalkBack full walkthroughs.

---

## 18. Regulatory and ethical position

- **Positioning:** a **personal health record and care-coordination tool with safety alerts**, not a diagnostic device.
  The patient never sees a disease name. Danger signs follow published public guidance and tell the user to seek care.
- This is the lowest-risk position for India (CDSCO Medical Device Rules 2017), the US (FDA's general-wellness and
  low-risk CDS policies) and the EU (MDR). **A regulatory consultant should confirm this before public launch**, especially for danger-sign alerts.
- **In-app disclaimer**, written plainly: "MedLog helps you remember and share how you feel. It is not a doctor. In an emergency, call 108."
- **Consent** is explained simply at setup, including what helpers can see.
- **No dark patterns, no upsells in health moments, no guilt messages** about missed doses. Wording is always kind.

---

## 19. Build order

**Phase 0: Foundations** (before any app code)
1. Clinical content v0.1: the catalogue (260 items), detail schemas, follow-up questions, danger-sign rules, reviewed by a clinician.
2. Plain-word vocabulary list, reviewed with 5 seniors.
3. Design system + 10 master pictograms + body map. Figma prototype of Home, Widget, Tell, Check, Medicines, Help, and the Doctor page.
4. **Usability round 1 on the prototype** with seniors and a doctor. Revise.
5. Voice test-set collection starts.

**Phase 1: Core (usable MVP)**
Tell (voice + pictures + body map) · check & follow-up · timeline with undo · danger signs · medicines with MedLog's own
alarms + double-dose guard · water · widget (all sizes) + QS tile · Read Aloud + Easy mode (all 15 features) · SOS (SMS +
call cascade + emergency number) · doctor page (screen + PDF + share + print) · encryption, backup file · English only · Tiers B/C.

**Phase 2: Connected care**
Meeting Timer integration + Google Calendar · Helper mode + nearby messages + missed-dose/check-in alerts · after-visit capture ·
reports & weekly spoken summary · "Add my old reports" (ported v1 analyzer) · Assistant App Actions · food logging · readings (BP, sugar, and so on) · on-device LLM (Tier A) · full 260-pictogram set ·
launch languages (D2) · usability round 2 · 30-day device soak · clinical case tests.

**Phase 3: Extra care**
WhatsApp SOS group call automation · Bluetooth health devices (standard BP, glucose, SpO2, thermometer profiles) ·
fall detection · Wear OS watch (SOS + medicine tap) · more languages · evaluation of an iOS version.

Each phase ends with the Section 17 gates passed. **No phase ships to real users while any gate fails.**

---

## 20. Decisions I need from you

| # | Decision | My recommendation |
|---|---|---|
| **D1** | Platform for v1 | **Android only**, native Kotlin. iOS looked at after Phase 2. |
| **D2** | Languages at launch | English + **which Indian languages?** (e.g., Tamil, Hindi). Each language adds a voice test set and a lexicon. |
| **D3** | Show possible causes to the patient? | **No.** Reasoning drives questions, danger alerts and "worth checking" on the doctor page only. |
| **D4** | WhatsApp group call in SOS | **Optional extra via Accessibility automation**, never the main path. The main path is calls + SMS + nearby alerts. |
| **D5** | Meeting Timer changes and signing | OK to **modify Meeting Timer** (medicine card, MedLog calendar, local bridge) and **sign MedLog with the Meeting Timer key**? |
| **D6** | Clinical reviewer | Who is the licensed doctor who will sign off the clinical rules? (Required before real users.) |
| **D7** | Country / emergency number | India (108 ambulance / 112) as the default? |
| **D8** | Lowest phone we must support | 3 GB RAM Android 8+ (Tier C) as the floor? |
| **D9** | Pictogram characters | Four figures (older woman/man, adult woman/man) with skin-tone options, OK? |
| **D10** | Audio clip retention | Keep 30 days by default, OK? |
| **D11** | Relay for far-away helpers | **ntfy.sh (free, public) for testing; before real users, the family's or our own ntfy relay** (one small program on any server). The public one has daily limits and no guarantee. Alerts are sealed either way, and SMS always goes too. |

---

## 21. What we keep from MedLog v1 (github.com/surya-prakash-design/medlog)

v1 was an Expo / React Native app (about 7,000 lines, 6 commits, April 2026) built around **typed free-text
observations**, **keyword highlighting**, **search**, **weekly reports**, **importing hospital reports**, and a
**Google Sheets backup**. v2 is native Kotlin, so almost no files carry over as-is. The ideas and some of
the logic do carry over.

| v1 part | Keep? | How it carries into v2 |
|---|---|---|
| `utils/reportAnalyzer.ts`: pulls dates, findings, medicine lines and lab rows out of hospital reports, and removes boilerplate | **Port to Kotlin** (`:core:clinical`) | Powers a **new v2 feature: "Add my old reports"** (new screen 20). Photograph or pick a PDF, and past findings, medicines and dates are added to history, so the doctor page has background from day one. Its date parser (4 formats, 2-digit years) and medicine-line detector (`1-0-1`, "after food", mg/ml rules that exclude lab units like mg/dL) are tuned to real Indian reports. |
| `services/ImportService.ts`: digital PDF text first, OCR fallback when too little text is found, merging duplicate lines | **Port the approach** | Same flow using ML Kit (bundled) and Android PdfRenderer |
| "Retrieval-only" rule (`LLMService` / `RetrievalService`): answers only with lines that exist in the user's documents, and never makes up text | **Keep as a rule** | Becomes principle 3 ("Never guess silently") for questions like "When did the doctor change my BP tablet?" Answers quote the stored line and its source. |
| SQLite design: WAL, FTS5 full-text search with triggers, soft delete, numbered migrations | **Keep the design** | Room + FTS4/5 on transcripts and imported lines. Soft delete becomes the 30-day "Removed" bin. |
| Highlight words ("watch words", each with its own colour) | **Keep the idea** | Becomes the helper's **"watch this"** problems (6.2), which are pinned on the widget and raised on the doctor page |
| Weekly report: day-by-day spread, peak time of day, word combinations that appear together | **Keep the logic** | Feeds Section 14's time-of-day patterns and "comes together" links, calculated on structured problems instead of raw words |
| Deep link `medlog://new-observation` + assistant launch ("Take Observation") | **Keep the idea** | Add **Google Assistant App Actions**: "Hey Google, tell MedLog I have a headache" goes straight to Tell with the text filled in |
| Onboarding that sets up the patient profile from an imported report | **Keep, simplified** | Optional helper step during setup |
| Export to TXT / HTML / DOCX | Partly | Replaced by the doctor page PDF + full-notes appendix + encrypted backup |
| Google Drive / Sheets backup (`driveService.ts`), `INTERNET` permission | **Drop** | Breaks the no-internet promise. Replaced by the encrypted backup file the user saves wherever they choose. |
| `expo-notifications` reminders | **Drop** | Not exact and not reliable when the phone is dozing or the maker kills background apps. Replaced by exact alarms + Meeting Timer. |
| `LLMService` stub / `ModelDownloadScreen` | **Drop** | It had no real model (the progress bar was simulated). Replaced by Section 7. |
| Dark theme, 15sp body text, small chips | **Drop** | Too small and low-contrast for seniors. Replaced by Section 4. |
| Voice through the keyboard mic | **Drop** | Replaced by on-device Whisper + read-back |

**Before any porting, clean up the old repo (it is public):**
- `test-report.pdf` (18 MB) is committed. The analyzer's rules mention one specific hospital and specific scan
  types, which suggests this may be **a real patient's report. If so, it is publicly downloadable right now.**
  Make the repo private, or delete the file from history, as soon as possible.
- The repo root also has about 45 debug screenshots, which may show real data too.
- The package id `com.medlog.app` is generic and may be taken on Play. v2 will use `com.suryaprakash.medlog`
  (matching Meeting Timer's naming, which D5 needs).

**Test cases:** v1 has no automated tests. When porting the analyzer, we'll write tests from **made-up**
reports that copy the real layouts (never real patient files), so the tuning is kept without keeping anyone's data.

Once you approve (with answers or changes to the above), I'll start Phase 0: the clinical catalogue JSON, the
vocabulary list, the design tokens, and the Android project skeleton with the module layout in Section 3.3.
