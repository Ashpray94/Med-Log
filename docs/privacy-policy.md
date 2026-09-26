# MedLog privacy policy (draft)

*Draft for review. Publish it at a public web address before a Play Store release. Last updated: 26 September 2026.*

MedLog helps a person record how they feel, remember their medicines, and get help from family. It is built so
that health information stays on the person's own phone.

## What stays on the phone

Notes about symptoms, readings (blood pressure, sugar and others), medicines and doses, food and water, voice
recordings, photos of reports, and the doctor page are stored **only on the phone**, in an encrypted database.
MedLog has no accounts and no MedLog server, and it does not collect any of this.

## What leaves the phone, and only when

| What | When | Where it goes |
|---|---|---|
| Help messages and SOS texts, including the person's location during an SOS | When the person asks for help, presses SOS, or an automatic alert they set up is due (missed medicine, no morning check-in, a possible fall) | To the helpers the person chose, by SMS and phone calls |
| Help alerts to paired helper phones, replies, "How are you?", and helpers' family chat | When the same events happen, or a helper writes | Through a message relay (ntfy.sh by default, or a relay the family chooses). **Each message is encrypted** with a key that only the paired phones have. The relay can't read it and keeps it for up to 12 hours. The person can turn this off. |
| Spoken words, to turn them into text | Only if "Use the internet if needed" is on and the phone has no offline speech for the language | To the phone's own speech service (Google or Samsung), under their privacy policy |
| Medicine times | Only if the person turns on Google Calendar reminders. Titles say "Medicine time" by default, without medicine names. | To the person's own Google Calendar |
| The doctor page, backups and setup files | Only when the person shares, prints or saves them | Wherever the person chooses |

## Permissions and why

- **Microphone:** to hear what the person says. Audio stays on the phone.
- **Notifications, exact alarms, full-screen alerts:** medicine reminders and SOS alerts.
- **SMS and phone:** to text and call the person's helpers when they ask for help.
- **Location:** only during an SOS, only sent to the person's helpers.
- **Bluetooth and nearby devices:** to reach paired family phones nearby, and Bluetooth health machines.
- **Calendar:** only if Google Calendar reminders are turned on.
- **Internet:** only for the encrypted help relay described above (and speech, if allowed).

## No ads, no tracking, no selling

MedLog shows no ads, contains no analytics or tracking, and never sells or shares data for advertising.

## Deleting data

Settings → Privacy → Delete everything erases all MedLog data on the phone. Uninstalling MedLog also removes it.
Relay messages expire by themselves within 12 hours.

## Children

MedLog is meant for adults and their carers.

## Contact

[Add a contact email before publishing.]
