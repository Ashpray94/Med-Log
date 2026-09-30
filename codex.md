# MedLog owner requirements

- The app must be usable by a 90-year-old person with cognitive impairment who recognises pictures. Follow CLAUDE.md Rule 0; the patient never sees a disease name.
- Aim for medically rigorous clinical history-taking and reporting, comparable in structure and completeness to an MD physician's examination. Do not imply the app is a physician or that AI-assisted content is clinician-reviewed.
- Systematically identify and fill gaps, following the handoff approach and patch workflow. Never weaken tests or add unreviewed codes.
- Use a laser-focused approach with minimal usage: one main model oversees analysis; use the lowest available model agents for narrow execution tasks, one agent per file.
- Keep owner-facing responses very short. Make small edits, build after each change through GitHub Actions, and commit only own files.
- Locate and integrate the owner's 2.14.1 source before shipping to the real app. Test dev first; back up the real app before installation.

## Full owner scope (confirmed 2026-09-30)

Four roots (the earlier phrase "3 main navigations" lists four): Home, Timeline, Helpers + Messages, Settings. Home contains feature choices only. Timeline defaults to today with Early morning/Morning/Noon/Afternoon/Evening/Night/Late night accordions, food/feeds/medicines/records, and helper-mode filters. Helpers has separate helper-only and patient/helper message channels and helper tools. Settings follows current mode.

Classify all notification/reminder/alert types and responses; an alert must offer meaningful replies, not only Open app. Sustain a loud middle-C piano-like alert; do not claim a sampled Steinway recording when synthesized. Validate sound on a device.

Clinical flows for every ailment: recognised history-taking, core information first (at most five), optional granular Tell more, conditional prerequisites, quantities/colour/shade/consistency/smell/blood/timing as applicable, and two-line plain-language help. Never show disease names to the patient. AI-assisted content remains unreviewed until licensed clinician sign-off.

Sync every real change from either patient or helper, online and nearby; notify the receiving side once. One success acknowledgement per action with clear actor. Preserve stable cross-device references, durable retries, missed-update recovery and record isolation.

Feedback text is primary; screenshot and annotation optional. Shake is optional, and a tappable feedback entry is required. Messages look like messages, alerts like alerts, medicines like medicines; cards represent individual items. Helper CRUD includes visible Edit and secondary Details; patient deletion remains forgiving under Rule 0.

Use dev/prod isolation; dev for emulator/device testing before production. Provide a test APK from an artifact-only build. Verify largest text, Hindi/Tamil, spoken questions and pictorial choices; apply Apple HIG and hospital-sign clarity subject to Rule 0.

Keep a detailed handoff with decisions, exact files, completed checks, unfinished work and next bounded agent tasks. Main oversees; lowest available execution models; one agent per file, exact briefs and stop conditions. Keep progress replies very short.
