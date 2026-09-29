# MedLog privacy policy (draft)

*Draft for review. Publish it at a public web address before a Play Store release. Last updated: 26 September 2026 (2.8).*

MedLog helps a person record how they feel, remember their medicines, and get help from family. It is built so
that health information stays on the person's own phone, and on the phones of the family the person has paired.

## What stays on the phone

Notes about symptoms, readings (blood pressure, sugar and others), medicines and doses, food and water, photos of reports, and the doctor page are stored on the phone, in an encrypted database.
MedLog has no accounts and no MedLog server, and it does not collect any of this. If the person pairs a family helper's phone, the same records are also kept on that phone (see below). Nothing else leaves the phone unless the person shares it.

## What leaves the phone, and only when

| What | When | Where it goes |
|---|---|---|
| Help messages and SOS texts, including the person's location during an SOS | When the person asks for help, presses SOS, or an automatic alert they set up is due (missed medicine, no morning check-in, a possible fall) | To the helpers the person chose, by SMS and phone calls |
| **Shared health records** once a helper is paired: the profile and care plan, medicines and limits, doses (taken, skipped, missed), health notes, appointments, imported report lines, and the helper list (names, phone numbers, what each helper may see) | Whenever any paired phone adds, changes or deletes one of these, and when a paired phone starts up (to catch up) | Through the message relay to the person's paired family phones, so every paired phone holds the same records and each can add, change and delete them. **Each message is encrypted** with a family key that only the paired phones hold. The relay can't read it and keeps it for up to 12 hours. **Photos, voice clips and each phone's own pairing keys are never sent.** The person can turn the relay off. |
| Help alerts to paired helper phones, replies, "How are you?", and helpers' family chat | When the same events happen, or a helper writes | Through a message relay (ntfy.sh by default, or a relay the family chooses). **Each message is encrypted** with a key that only the paired phones have. The relay can't read it and keeps it for up to 12 hours. The person can turn this off. |
| Spoken words, to turn them into text | Only when the person taps Speak in a search or text box. The phone's own speech typing (Google or Samsung) handles it; MedLog only receives the text | To the phone's own speech service, under its privacy policy |
| Medicine times | Only if the person turns on Google Calendar reminders. Titles say "Medicine time" by default, without medicine names. | To the person's own Google Calendar |
| The doctor page, backups and setup files | Only when the person shares, prints or saves them | Wherever the person chooses |

## Permissions and why

- **No microphone permission.** Speaking is done by the phone's own speech typing, only when the person taps Speak.
- **Notifications, exact alarms, full-screen alerts:** medicine reminders and SOS alerts.
- **SMS and phone:** to text and call the person's helpers when they ask for help.
- **Location:** only during an SOS, only sent to the person's helpers.
- **Bluetooth and nearby devices:** to reach paired family phones nearby, and Bluetooth health machines.
- **Calendar:** only if Google Calendar reminders are turned on.
- **Internet:** only for the encrypted help relay described above (and speech, if allowed).

## No ads, no tracking, no selling

MedLog shows no ads, contains no analytics or tracking, and never sells or shares data for advertising.

## Deleting data

Settings → Privacy → Delete everything erases all MedLog data on that phone, and the phone forgets the family key, so it stops sharing. The erase is not sent to the other paired phones: they keep their copies until their own owners delete them. Deleting a single note, medicine or appointment inside MedLog is shared, and removes it from every paired phone. Uninstalling MedLog also removes the data on that phone.
Relay messages expire by themselves within 12 hours.

## Children

MedLog is meant for adults and their carers.

## Contact

[Add a contact email before publishing.]
