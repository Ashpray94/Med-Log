# MedLog: rules for any AI working in this repo

MedLog is a health-logging app for older people and their carers. Mistakes here can hurt someone. These rules are strict.
Read `docs/HANDOFF.md` (state of work), `docs/DESIGN.md` (look and feel) and `PLAN.md` (product) before changing anything.

## 0. The user test (overrides every other rule when they conflict)
**A 90-year-old with cognitive impairment, who can still recognise pictures and visual cues, must be able to use this app alone to navigate and log her data.**
Clinical depth, reports and sync are backend work: they must never add a word, a choice, a step or a risk on her screens. Before shipping any patient-facing change ask:
could she do this with one finger, no reading, no memory and no fear of breaking it? If not, the change is wrong.
- **Picture first, word second.** Every choice is a large icon or pictogram with a 1-3 word label (labels ≤5 words). Meaning never depends on colour alone.
- **One thing per screen,** one obvious main button (≥88dp tall for the main action), same place every time. Back and Home are always visible and always in the same spot.
- **Tap only.** No swipe-only, long-press, double-tap, drag or shake-only actions. Shake feedback is optional extra and must never be required. Spacing between targets ≥12dp.
- **No time pressure, no surprises.** Nothing disappears on a timer; no countdowns on the patient's own screens (only the spoken SOS cancel). Every destructive action has big
  Yes/No with icons and an Undo. Nothing she does can be lost or broken by a wrong tap.
- **Spoken and seen.** Each question/alert is read aloud automatically (with a speaker button to repeat); "Why ask?" text is hidden behind a speaker icon for her and shown as text only in helper mode.
- **Size and contrast:** body ≥20sp on patient screens (≥18sp elsewhere), labels ≥16sp, contrast ≥7:1, targets ≥56dp, primary ≥88dp.
- **Helpers carry the complexity:** filters, edit/delete menus, settings depth, chat, reports and audit detail live in helper mode or behind a clearly secondary "More" area.
- Test new screens with the largest text size setting, TalkBack-style reading order, and one-handed reach.

## 1. Clinical conduct (non-negotiable)
1. **History-taking follows recognised methods:** OPQRST / SOCRATES for symptoms (onset, provocation/palliation, quality, region/radiation, severity,
   timing), plus associated symptoms, red flags, past history, medicines, allergies. Never invent a custom questioning scheme.
2. **Red flags come from published guidance,** cited in `docs/clinical-audit/*.md` (NICE CKS, WHO IMCI/PEN, AHA/ACC, BE-FAST stroke, NICE NG51 sepsis, NICE NG232 head injury, Ottawa rules, etc.).
   Every symptom with a dangerous cause needs at least one danger question. Red flags err on recall: when in doubt, raise the level.
3. **The patient never sees a disease name or diagnosis.** The app asks questions, states urgency (green / amber / red) and gives first aid. "Worth checking"
   patterns go on the clinician's page only, marked as inferred (◇). This is a deliberate safety and regulatory decision (PLAN.md section 0, D3). Do not reverse it.
4. **Questions are conditional, never blind.** A dependent question (colour, shade, amount, type) is gated behind its prerequisite ("did anything come up?").
   A question that does not apply to the problem must not be asked (for example, coffee-ground vomit only under vomiting). Use catalogue `gate`, `minAge`, `maxAge`.
4a. **Main information first, detail after.** The first screens capture only the core: when it started, how bad, where (if it has a place) and at most two danger checks, never more than 5 questions and never a wall of options. Every granular question (amount, colour, shade, consistency, smell, timing, modifiers, history) sits behind "Tell more", is optional, and can be stopped at any time with one tap. A test (`CoreSizeTest`) enforces the cap for every problem.
4b. **Granular for EVERY ailment, enforced by a check.** Every problem is assigned one or more archetypes (pain, substance/discharge/bleeding, skin, swelling/lump, breathing,
   neuro, systemic, mind/sleep, injury, sensation, medicine effect, etc.) in `app/src/main/assets/clinical/archetypes.json`. Each archetype lists required clinical dimensions in
   `standard.json` (for example pain = site, onset, course, character, radiation, associated, timing, aggravating, relieving, severity, impact, tried). Every problem also needs the
   universal dimensions: onset, course, severity, impact, tried, danger screen. Fields and questions are tagged with the dimensions they cover in `dims.json`.
   `python3 tools/lint_completeness.py` must print zero gaps before any catalogue change is merged or shipped; a dimension may be waived only with a written reason in
   `archetypes.json` that a clinician reviews. Anything that comes out of the body (phlegm, vomit, stool, urine, discharge, bleeding) follows the chain
   any? → amount → colour + shade → consistency → smell → blood → timing.
5. **Every question carries a 2-line explanation:** line 1 what it means, line 2 why it is asked. Plain words, about grade 5, ≤12 words per sentence.
6. **Nothing is saved as fact without read-back.** Unknown stays "not said". Inferred facts are flagged. Never fabricate a value, code or reference range.
7. **No clinical rule ships as "reviewed" without a licensed clinician.** `catalogue.json` keeps `"reviewed": false` and `DangerRules.VERSION` ends in `-unreviewed`
   until a clinician signs off. Any rule change bumps `DangerRules.VERSION` and is listed in the audit report.
8. Medicine safety: never suggest a dose or a drug. Record name, strength, dose, route, frequency, timing versus food, last taken. Double-dose protection stays on.

## 2. Records and reports use standard formats (no invented formats)
- **Clinician summary:** SOAP order. S = patient-reported (chief concern, HPI in OPQRST/SOCRATES order, review of symptoms, medicines, allergies, history).
  O = measured values only (vitals, glucose, weight) with units and date/time. A and P are left blank for the clinician. Urgent handover uses **SBAR**.
- **Medication list:** drug name, strength, dose, route, frequency, indication if known, start/stop, adherence (taken / missed / skipped with reason).
- **Units:** SI or conventional with the unit always written (mmHg, mmol/L or mg/dL, °C or °F, bpm, SpO₂ %, kg). Dates ISO 8601 in data, readable in the PDF.
  Times in the patient's local zone with offset in exports.
- **Coded exchange:** HL7 **FHIR R4** JSON export. Vitals use **LOINC** (BP panel 85354-9, systolic 8480-6, diastolic 8462-4, heart rate 8867-4, SpO₂ 59408-5,
  body temperature 8310-5, body weight 29463-7, glucose 2339-0 or 2345-7 as applicable). Medicines as MedicationStatement, allergies as AllergyIntolerance.
  **Do not add SNOMED, ICD-10 or RxNorm codes unless the mapping is certain and cited;** otherwise use plain text display. A wrong code is worse than none.
- Every report footer states: generated date/time, app and rules version, "patient-reported; not a diagnosis; ◇ = inferred by the app".
- Reports are for the named patient only. Share only through the user's explicit action.

## 3. Privacy and safety
- Notes, medicines and reports never leave the phone except through the user's share action, or as sealed help alerts/sync events over the paired relay.
- No analytics, no accounts, no third-party SDK that sees health data. Never log health data to logcat in release builds.
- The dev flavor (`.dev`) must never contact the real relay or update channel.
- Emergency paths (SOS, fall) never wait on questions and never depend on the internet (SMS and calls always go).

## 4. Interface rules (full rules in `docs/DESIGN.md`)
- Hospital-sign minimalism: ≥7:1 contrast, body ≥18sp, touch targets ≥48dp (primary 56dp), icon plus a word, ≤4-word titles, one main action per screen.
- Apple HIG: clear hierarchy, consistent placement, forgiving (Undo on deletes), no colour-only meaning.
- Messages look like messages (bubbles), alerts like alerts, medicines like medicines. A card wraps one real item only, never a whole screen.
- Every create, edit and delete gives exactly one toast (`Announce.done`) and is shared to the other paired phones.
- Every notification has a defined row in `notify/NotifySpec.kt` with tier, audience and responses; add the row first, then the code.

## 5. How to work here
- Nothing in this repo may be claimed "working" until it compiles and its tests pass. The original authoring sandbox had no Android SDK: build and run the unit tests
  (`gradle :app:testProdDebugUnitTest`) and try the dev flavor on an emulator (`gradle :app:installDevDebug`) before saying it works.
- Keep the change small and in the existing style. One agent per file at a time. Commit only the files you changed.
- Clinical content changes go through `app/src/main/assets/clinical/audit/*.json` patches and `python3 tools/merge_audit.py`, with a matching note in `docs/clinical-audit/`.
- Model use: plan with the strongest model, execute with the smallest that can do it; use a stronger model for clinical content and foundations only.
