# Clinical completeness standard

**AI-assisted; not clinician-approved.** A licensed clinician must review every dimension list, archetype assignment and waiver before release.

Spec: CLAUDE.md section 1 item 4b. Files (all in `app/src/main/assets/clinical/`): `standard.json` (archetypes and dimensions), `archetypes.json` (problem to archetypes, plus waivers), `dims.json` (which dimensions each field and question covers). Check: `python3 tools/lint_completeness.py` must show zero gaps.

Every problem must also cover the universal dimensions: onset, course, severity, impact, tried, danger.

| Archetype | Dimensions | Follows |
|---|---|---|
| pain | site, onset, character, radiation, associated, timing, aggravating, relieving, severity | SOCRATES pain history (Site, Onset, Character, Radiation, Associations, Time course, Exacerbating/relieving, Severity) |
| substance | any, amount, colour, shade, consistency, smell, blood, timing | Bristol Stool Form Scale for stool |
| bleeding | site, amount, duration_of_bleeding, stops_with_pressure, cause_trigger, anticoagulant, faint_from_loss, other_bleeding | NICE CKS bleeding/epistaxis |
| skin | distribution, appearance, colour, itch_pain, spread, triggers, fever, contacts | British Association of Dermatologists / NICE CKS rash history (distribution, morphology, itch, spread, triggers, systemic features, contacts) |
| swelling | site, size, colour_warmth, tenderness, change, systemic | NICE CKS lumps and swellings |
| breathing | exertion_tolerance, at_rest, lying_flat, wheeze, cough, chest_pain, swelling, fever | MRC dyspnoea scale and NYHA (exertion tolerance, orthopnoea) |
| neuro | speed_of_onset, duration_each, pattern, trigger, one_sided, vision_speech, consciousness, associated | FAST/BE-FAST stroke screen |
| collapse | warning_before, duration_unresponsive, position_trigger, recovery, heart_symptoms, injury_from, first_time | NICE CG109 transient loss of consciousness |
| systemic | duration, trend, amount_of_change, associated, fluids_intake | NICE CKS fever, fatigue, weight loss and dehydration |
| mind | duration, mood_anxiety, sleep, function, safety | PHQ-9, GAD-7 and PHQ-2 domains |
| sleep | sleep_pattern, hours_slept, daytime_effect, breathing_in_sleep, night_waking, substances | Consensus Sleep Diary items |
| injury | mechanism, time_since, consciousness, weight_bearing, deformity, bleeding, anticoagulant | Ottawa ankle/knee rules |
| sensation | site, constant_or_comes_and_goes, trigger, weakness, spread | Neurological symptom history (distribution, pattern, weakness, progression) |
| eye_ear_nose_throat | side, vision_hearing_change, discharge, pain, swelling_redness, associated | NICE CKS red eye, otitis, hearing loss, tinnitus |
| nose_throat | side, discharge_or_blockage, pain, voice_or_swallow, smell_taste, trigger_or_allergy, associated | NICE CKS sore throat (FeverPAIN/Centor), sinusitis, rhinitis, hoarseness, epistaxis |
| mouth_teeth | site, pain, swelling_redness, bleeding, eating_drinking, ulcer_or_patch | NICE CKS dental abscess, mouth ulcers, oral cancer red flags (3-week rule) |
| urinary_bowel_function | frequency, control, pain_passing, night, associated | NICE CKS lower urinary tract symptoms, UTI, constipation, diarrhoea |
| digestive | meal_relation, frequency, duration, trigger, swallowing, appetite_weight | Rome IV functional GI symptom domains |
| womens | cycle_or_bleeding_pattern, pain, discharge, age_context | FIGO abnormal uterine bleeding (PALM-COEIN) history |
| heart | rate_regularity, duration_each, exertion_link, faint_dizzy, chest_pain, trigger | EHRA symptom classification |
| reading_monitoring | reading, known_condition, on_treatment, missed_treatment, symptoms_with, recent_change | NICE NG136 hypertension, NG17/NG28 diabetes hypo/hyperglycaemia, BTS oxygen saturation guidance |
| daily_function | activity_affected, cause_or_pain, aids_or_help, safety_risk, since_when | Barthel Index / Katz ADL and Lawton IADL domains |
| medicine_effect | which_medicine, timing_vs_dose, new_or_changed, stopped_helps | WHO-UMC causality assessment and Naranjo adverse-drug-reaction questions (drug, timing, dechallenge) |

## Add a problem
1. Add the problem to `catalogue.json` with its fields and follow-up questions.
2. Add `"<problemId>": ["<archetype>", ...]` (1 to 3) under `problems` in `archetypes.json`. Pick by the clinical nature of the complaint; if none fits, add an archetype to `standard.json` rather than waive.
3. Add any new field or question id to `dims.json`. Tag a dimension only if the field truly asks it.
4. Run the lint. Fix gaps by adding a real question, not by tagging loosely.

## Waivers
A waiver (`waive.<problemId>.<dim>`) says a dimension does not apply to that problem. It needs a written reason, and the dimension must belong to one of the problem's archetypes. Waive only when the question would be meaningless (for example weight bearing for a rash). Never waive to hide a missing question. A clinician reviews all waivers; remove any they reject and add the question.
