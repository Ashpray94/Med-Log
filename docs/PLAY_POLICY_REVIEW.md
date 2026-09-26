# Google Play policy review — MedLog 2.8.0

Reviewed 26 Sep 2026 against the Play Developer Policy Center (permissions, health apps, foreground services,
accessibility, data safety, target API). **Status:** fixed in code / needs a Play Console form / must change before
Play / fine.

## Must change before a Play release

| # | Area | Problem | What to do |
|---|---|---|---|
| 1 | **SMS** (`SEND_SMS`) | SMS permissions are restricted to the default SMS app and a short list of exceptions. Sending SOS texts automatically is not a listed exception, so Play would very likely reject the app. | Make a Play build that opens the phone's SMS app with the SOS text and helpers already filled in (`ACTION_SENDTO`), one tap to send, and keep automatic sending in the GitHub build only. The relay and nearby alerts still reach paired helper phones automatically. |
| 2 | **Accessibility service** (WhatsApp group call) | Play allows `AccessibilityService` mainly for tools that help people with disabilities. Pressing buttons inside another app (WhatsApp) is automation, which is a common reason for removal. Now declared `isAccessibilityTool="false"`. | Leave the WhatsApp auto-call out of the Play build (it's already an optional extra; calls and SMS are the main SOS path). |
| 3a | **In-app updater** (`REQUEST_INSTALL_PACKAGES`) | Play forbids apps updating themselves outside Play, and allows this permission only for apps whose main job is installing apps. The updater already switches itself off when MedLog was installed from Play. | Remove the permission and the updater from the Play build. |
| 3 | **Target API** | The app targets API 35. Play's yearly rule will ask for API 36 for new apps and updates. | Move to compileSdk and targetSdk 36 (needs a newer Android Gradle Plugin), then test alarms, full-screen alerts and foreground services again. |

## Needs a Play Console form or declaration (allowed with the right answers)

| # | Area | Notes |
|---|---|---|
| 4 | **Health apps declaration** | Required. Say plainly it records symptoms and reminders, is **not a medical device**, gives no diagnosis, and that the danger-sign advice ("call a doctor today") has not yet been checked by a clinician (plan D6). The app already shows this disclaimer. |
| 5 | **Medical-device risk** | The danger-sign rules ("Call 108 now") could count as clinical decision support in some countries (CDSCO in India, FDA in the US, EU MDR). Get the clinical review (D6) done and keep the wording as general safety advice. |
| 6 | **Full-screen intent** (`USE_FULL_SCREEN_INTENT`) | From Android 14 this is granted automatically only to calling and alarm apps. Declare it for "medicine alarms and SOS alerts". The app already checks and asks the person when it isn't allowed. |
| 7 | **Foreground services** | Declare each one with a short video: SOS (`specialUse` + `location`, while an SOS the person started is running), fall detection (`health`), helper link (`remoteMessaging` for messages between the family's phones, plus `connectedDevice` for Bluetooth to a nearby phone). **Fixed:** the helper link now uses `remoteMessaging` instead of relying on `connectedDevice` alone. |
| 8 | **Battery optimisation** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) | Allowed only when the core feature breaks without it. Justify as "medicine alarms and family SOS alerts must arrive while the phone sleeps". Be ready to show that the app still works if the person says no (it does, just less reliably). |
| 9 | **Location** | Only in the foreground, only during an SOS, only sent to the person's helpers. There is no background location permission. Needs the prominent disclosure (already on the permissions screen) and a Data safety entry. |
| 10 | **Phone** (`CALL_PHONE`, `READ_PHONE_STATE`) | Not restricted, but sensitive: calling helpers in turn during SOS, and noticing when a call ends. Explain in the Data safety form. |
| 11 | **Calendar** (`READ/WRITE_CALENDAR`) | Optional, only if the person turns on Google Calendar reminders. Explain in Data safety. |
| 12 | **Data safety form** | Declare: health info, name, phone numbers of helpers, approximate/precise location (SOS only), messages (help alerts, **encrypted end to end**, sent through a relay: ntfy.sh or the family's own server). Speech typing is the phone's own (MedLog receives text only). No ads, no analytics, no selling. Data can be deleted in the app. |
| 13 | **Privacy policy URL** | Required for health apps and for these permissions. A draft is in [`privacy-policy.md`](privacy-policy.md). It must be published at a public web address before submitting. |

## Fixed in this version
- **Microphone** (`RECORD_AUDIO`) removed. Voice input now goes through the system speech-typing screen (`RecognizerIntent`), which needs no permission, so there is no audio in the Data safety form.

- **Exact alarms:** swapped `USE_EXACT_ALARM` (only allowed for alarm-clock and calendar apps) for `SCHEDULE_EXACT_ALARM`, which the person grants. Setup, the home screen ("Reminders are off") and Settings → Permissions already ask for it.
- **Foreground service type** for the helper link: `remoteMessaging` (see 7).
- **Accessibility service** declares `isAccessibilityTool="false"` honestly (see 2).
- **Library usage reporting** (ML Kit datatransport) stays removed, so the Data safety answers are complete.
- **In-app updater** turns itself off for Play Store installs (see 3a).
- **Tone of family messages** is checked by `WordingTest` (no alarm words, no capitals, no exclamation marks).

## Fine as it is

- No `READ_CONTACTS` (helpers are picked with the system contact picker), no `READ_SMS`/`RECEIVE_SMS`, no call-log access, no background location, no ads SDKs.
- The camera is used through the system camera app (no `CAMERA` permission).
- Bluetooth and nearby Wi-Fi are used only to reach paired family phones and health machines.
