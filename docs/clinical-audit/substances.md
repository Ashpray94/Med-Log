# Substances audit: things that come out of the body

**AI-assisted audit; not clinician-approved.** A licensed clinician must review every threshold and red flag below. Catalogue version after merge: 0.1.2 (`reviewed` stays false). `DangerRules.VERSION` must be bumped by its owner because red flags changed (file not touched here).

Patch: `app/src/main/assets/clinical/audit/substances.json`. Merged with `python3 tools/merge_audit.py`; `--check` prints "Catalogue is valid".

## Method
For each substance the chain is: is there any, how much, colour and shade, consistency, smell, blood, timing or pattern. Each later question is gated on an earlier answer where an "is there any" answer exists. Where the problem itself says something is coming out (ear discharge, vomiting, loose motions), the first question is ungated and the rest are asked freely. All help text is two lines (what it means, why it is asked), at most 12 words per sentence. No disease names are shown to the patient. Red flags use `is` (yes/no or a choice) and `gte` (numbers). Extended questions stay optional ("tell more").

Standards and tools used (from general clinical practice; clinician to confirm the exact editions):
- Sputum: standard respiratory history (amount, colour, consistency, smell, blood, timing); NICE CKS chronic cough, and the WHO 2-week (NICE 3-week) persistent-cough trigger. The app uses 3 weeks for AMBER. Talk test for breathlessness (can you finish a sentence).
- Vomit: standard GI history; NICE CKS dyspepsia and upper GI bleeding red flags (haematemesis, coffee-ground); faecal-smelling vomit as a bowel-obstruction sign.
- Stool: **Bristol Stool Chart** (Lewis and Heaton, 1997), types 1 to 7.
- Urine: urine colour chart and NICE CKS UTI (lower) and pyelonephritis (fever with flank pain as an upper-tract red flag).
- Discharge: standard wound, ear, eye and nose history (amount, colour, consistency, smell, blood, side). Wound amount language follows common dressing-saturation wording.
- Bleeding: first-aid rule of 10 minutes firm pressure (NICE CKS epistaxis, St John/Red Cross first aid); blood thinner and faintness as escalation factors.

## Sputum (cough, cough_blood)
Chain for cough: dryWet, phlegm (gate: wet), sputumAmount, phlegmColour, shade, sputumConsistency, sputumSmell (all gated on phlegm = yes), blood (existing, ungated because blood can come with a dry cough), sputumWhen (gated on phlegm), weeks (existing), coughBreathTalk (core, danger), coughBreathWalk, coughFever, nightSweatsAlso, weightLossLately, coughWheeze.
- Amount: a little (under a teaspoon), some (a few spoons), a lot (a cupful or more).
- Colour and shade reuse existing `phlegmColour` and `shade` (identical choices). `colour` is untouched.
- Consistency: thin and watery, thick and sticky, frothy. When worst: morning, night, all day.
- Red flags: blood in cough RED; red phlegm RED; pink phlegm AMBER; cough 3 weeks or more AMBER; cannot finish a sentence RED; breathless walking AMBER; fever plus night sweats plus weight loss AMBER; bad-smelling phlegm AMBER.
- Changed: `cough` had only dry/wet, phlegm, colour, shade, blood, weeks. `cough_blood` now also asks dry/wet and phlegm so the same gated chain applies; its existing red flags are unchanged. Order puts the short yes/no extras before the phlegm chain so they survive the extended cap.

## Vomit (vomiting, vomit_blood)
Chain: vomitAmount (a little, a cupful, a large amount, cannot keep anything down), vomitColour (clear, white, yellow, green, brown, red, black), vomitShade (gated on yellow, green, brown, red), existing blood then coffeeGround (existing, gated on blood, vomiting only), existing count (times in 24 h), projectile (existing, maxAge 1), vomitFaecal (core, RED), cantKeepFluids (existing).
- "What it looked like" is covered by colour plus the existing gated blood, coffee-ground and green (bile) questions, to avoid asking the same thing twice. `coffeeGround` was NOT added to any `fields` list, so it is never asked ungated.
- Red flags: faecal smell RED; red or black vomit RED; green RED (same as existing vomitGreen); brown AMBER; cannot keep anything down AMBER.
- `vomit_blood` also gets amount, colour, shade, faecal smell and cantKeepFluids.
- Not changed: `nausea` (nothing comes up), `hiccup`, `burp`, `acidity`, `swallowing`.

## Stool (loose_motions, constipation, blood_stool, black_stool)
Chain: stoolShape (Bristol 1 to 7: hard lumps, lumpy sausage, cracked sausage, smooth sausage, soft blobs, mushy, watery), stoolColour (brown, yellow, green, pale or clay, red, black and tarry), stoolShade (gate: brown, yellow, green, red), blood (existing) then stoolBloodWhere (on paper only, streaks on stool, mixed in stool, dripping in bowl; gate blood = yes), mucus (existing field), stoolSmell, stoolFloats, count (existing, loose_motions), painful (existing field, pain passing it).
- `blood_stool` uses `stoolBloodPlace` (same choices, no gate) because blood is already the complaint; its existing stoolBloodColour and stoolBloodAmount stay.
- `black_stool` gets shape, colour, shade, smell and pain only (blood chain not meaningful). `constipation` has no times-in-24 h (daysNoStool instead).
- Red flags: black and tarry RED (all four); pale or clay AMBER; red stool AMBER (loose, constipation); blood mixed in or dripping AMBER.
- Overlap: `q_black_stool` (core RED) and stoolColour both capture black stool. Kept for safety; clinician may drop one.

## Urine (burning_urine, frequent_urine, no_urine, blood_urine, leaking_urine)
Chain: urineColour (pale straw, dark yellow, tea or brown, pink or red, cloudy), urineAmount (little, normal, large), urinePerDay and nightTimes (existing), urineSmell, blood (existing field), burning (existing field), flankPain gated on burning (question `q_urineFlank`), temperature (existing `q_temp`).
- `blood_urine` keeps `urineBloodColour` as its colour question (no duplicate urineColour). `no_urine` has no amount question (no urine passed) and asks the colour of the last urine.
- Red flags: flank pain plus temperature 100.4 or more RED (added for frequent, no_urine, leaking; burning_urine and blood_urine already had it); tea or brown AMBER; pink or red AMBER.
- `flankPain` was deliberately not added to `fields` of frequent, no_urine and leaking, so the gated question is the only way it is asked. `kidney_pain` unchanged.

## Discharge (ear_discharge, runny_nose, itchy_eyes, discharge, wound_not_healing, bed_sore)
Chain: any (existing `eyeDischarge` for eyes, `woundDischarge` for wounds; by definition for ear, nose, vaginal), dischargeAmount (a few drops, wets a tissue, soaks dressing or pad), colour (+ shade), fluidConsistency (watery, thick, pus-like), smell, blood, side (existing `side` or `oneSide`).
- Colour keeps each problem's existing field where choices differ: `earDischargeColour`, `dischargeColour` (vaginal), `woundColour`; new `fluidColour` for nose and eyes. Smell reuses `woundSmell` and `fishySmell`; blood reuses `noseBloodStained` for the nose.
- Question-level gates differ per problem (variants `_w`, `_e`), so the shared `fluid*` fields carry no field-level gate.
- Side is not asked for wounds, bed sores or vaginal discharge (not meaningful).
- Red flags: blood AMBER; soaks dressing or pad AMBER; pus-like AMBER; bad smell AMBER (ear, nose, eye). Existing flags unchanged.

## Bleeding (nosebleed, cut, bleeding, heavy_bleeding, postmeno_bleeding, bleeding_gums, bruising_easily)
Chain: where from (existing side or bleedSite), bleedVolume (a few spots, soaked a tissue, soaks a pad per hour, more than that), bleedColour (bright red, dark red), how long (existing bleedMinutes; bleedDays for heavy and postmenopausal), stops with 10 minutes pressure (existing `bleedingStops` or `bleedingContinues`; opposite polarity kept as in each problem), blood thinner (existing `q_thinner`; no PersonContext fact could be checked), dizzy or faint (existing feelFaint, faintDizzy, faintWeak).
- `heavy_bleeding` and `postmeno_bleeding` keep padsHour, bigClots and bleedAmount as their amount, and gain bleedColour only. Pressure does not apply to vaginal bleeding. `bruising_easily` gains only faintDizzy.
- Red flags: more than that RED; soaks a pad per hour plus dizzy RED; soaks a pad per hour alone AMBER; bleeding over 20 minutes AMBER (cut, bleeding); bruising with other bleeding and dizziness RED.

## Interview.kt
`extended()` limit changed from 9 to 14 (comment added). Closed-gate questions still use slots in the cap; a later change could count only open gates.

## Open questions for the clinician
1. Sputum amounts (teaspoon, few spoons, cupful) and the 3-week cough trigger (WHO 2 weeks versus NICE 3 weeks).
2. Should red phlegm be RED or AMBER; pink phlegm AMBER.
3. Vomit: is green always RED, or only with belly swelling; amounts for "large".
4. Bristol mapping to plain words and the pictures used; whether types 1-2 or 6-7 should ever raise a flag.
5. Pale or clay stool AMBER; is AMBER enough for red stool.
6. Urine colour flags and the 100.4 F threshold for flank pain plus fever.
7. Pad thresholds: a pad per hour AMBER alone; "more than that" RED. Is 20 minutes the right cut for cut and bleeding?
8. Blood thinner: should it come from the person's saved medicines rather than a question.
9. Should ungated first questions for ear, nose and vaginal discharge instead use an explicit "anything coming out" gate.

## For the UI owner (rule 0)
The engine cannot yet express these, so today they appear as words only:
- Colour choices (phlegmColour, vomitColour, stoolColour, urineColour, fluidColour, bleedColour, shade) should render as colour swatches, never colour alone (keep the word).
- Bristol types (stoolShape) should render as the seven standard pictures.
- Amount choices (teaspoon, cupful, tissue, pad) should use pictures of a spoon, cup, tissue and pad.
- Choice labels such as "a little (under a teaspoon)" exceed 5 words; the picture should carry the meaning.
