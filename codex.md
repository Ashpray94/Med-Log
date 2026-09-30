# MedLog owner requirements

- The app must be usable by a 90-year-old person with cognitive impairment who recognises pictures. Follow CLAUDE.md Rule 0; the patient never sees a disease name.
- Aim for medically rigorous clinical history-taking and reporting, comparable in structure and completeness to an MD physician's examination. Do not imply the app is a physician or that AI-assisted content is clinician-reviewed.
- Systematically identify and fill gaps, following the handoff approach and patch workflow. Never weaken tests or add unreviewed codes.
- Use a laser-focused approach with minimal usage: one main model oversees analysis; use the lowest available model agents for narrow execution tasks, one agent per file.
- Keep owner-facing responses very short. Make small edits, build after each change through GitHub Actions, and commit only own files.
- Locate and integrate the owner's 2.14.1 source before shipping to the real app. Test dev first; back up the real app before installation.
