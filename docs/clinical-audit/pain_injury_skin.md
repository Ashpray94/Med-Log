# Pain, injury and skin clinical audit

**AI-assisted audit; not clinician-approved.** A licensed clinician must review and sign off before use.

Source: patch `app/src/main/assets/clinical/audit/pain_injury_skin.json`. catalogue.json and Kotlin were not edited.

## Conventions and assumptions

- Checklist outcomes (applied to every problem): (1) onset added via `onsetWhen`/`happenedWhen` where missing; (2) severity kept where pain scale is useful; (3) only advice-changing discriminators kept; (4) prerequisites ordered (injury before weight-bearing, discharge before colour/smell, burn cause before depth); (5) red flags per standard below; (6) older-adult modifiers: blood thinner, diabetes, weak bones, falls, dehydration, where they change advice; (7) low-value questions dropped (free-text worse/better/context/side where not useful); (8) every problem has 9 or fewer fields; (9) danger questions added where a dangerous cause exists; (10) wording 12 words or fewer per sentence.
- Assumed a problem's question is resolved by field id via the questions map; `fu` lists are emptied and all questions now live in `fields` (old `fu` ids duplicated fields).
- Shared `colour`, `context`, `couldGetUp`, `bleedingStops`, `visionChange` etc. left unchanged; new ids created (e.g. `woundColour`, `stuckOnFloor`, `bleedingContinues`, `visionLoss`).
- Only 2 danger questions (priority 85+) are asked in the core set; others wait for 'tell more'. Red flags still fire on any answered field.
- History questions (diabetes, cancer, blood thinner) name the patient's own condition; no diagnosis is ever named to the patient. Problem labels such as 'Gout attack' are catalogue-owned and flagged below.
- RF temperature thresholds assume Fahrenheit (field unit).

### back_pain

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| back_pain | NICE CKS Low back pain and sciatica (red flags, cauda equina) | conditional; no gates. Red flags ask first by priority | Fields now 9: onsetWhen, injuryFirst, steroidBone, severity, bladderBowel, legWeak, numbGroin, feverish, weightLoss. Added: onsetWhen, injuryFirst, steroidBone, bladderBowel, legWeak, numbGroin, feverish, weightLoss. Removed: side, character, radiation, worse, better, q_severity. Red flags: 7. | Is 2 core danger questions enough for cauda equina (3 signs)? Age over 50 new onset not modelled (no age RF). |

### neck_pain

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| neck_pain | NICE CKS Neck pain; Canadian C-spine rule; meningitis red flags (NICE NG240) | straight | Fields now 7: onsetWhen, injuryFirst, severity, stiffNeckLight, feverish, armNumbWeak, chestTight. Added: onsetWhen, injuryFirst, severity, stiffNeckLight, feverish, armNumbWeak, chestTight. Removed: side, radiation, temperature. Red flags: 7. | Should any neck injury in over-65s be RED? RF cannot test age. |

### knee_pain

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| knee_pain | Ottawa knee rule; NICE CKS Knee pain; septic arthritis | branched: cannotBend gated on injuryFirst | Fields now 8: onsetWhen, injuryFirst, cannotWalk, cannotBend, severity, redHot, feverish, calfSwollen. Added: onsetWhen, injuryFirst, cannotWalk, cannotBend, redHot, feverish, calfSwollen. Removed: side, worse, impact, q_severity. Red flags: 5. | Age 55+ and fibula head/patella tenderness not asked (9-question cap). |

### hip_pain

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| hip_pain | NICE CG124 Hip fracture; NICE CKS Hip pain in adults | branched: legTurned gated on injuryFirst | Fields now 7: onsetWhen, injuryFirst, legTurned, cannotWalk, severity, feverish, steroidBone. Added: onsetWhen, injuryFirst, legTurned, cannotWalk, feverish, steroidBone. Removed: side, impact, q_severity. Red flags: 5. | Is AMBER correct for cannot-walk with no injury in older people? |

### shoulder_pain

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| shoulder_pain | NICE CKS Shoulder pain; ACS chest pain guidance (NICE CG95); septic arthritis | straight | Fields now 9: onsetWhen, injuryFirst, looksBent, severity, exertion, chestTight, armJaw, redHot, feverish. Added: onsetWhen, injuryFirst, looksBent, chestTight, armJaw, redHot, feverish. Removed: side, q_severity. Red flags: 6. | Is armJaw (existing wording, back included) too broad for shoulder? side dropped. |

### joint_swelling

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| joint_swelling | BSR/NICE: hot swollen joint = septic arthritis until excluded | straight | Fields now 8: onsetWhen, side, injuryFirst, redHot, feverish, jointStuck, severity, diabetes. Added: onsetWhen, injuryFirst, feverish, jointStuck, severity, diabetes. Removed: context. Red flags: 5. | Immunosuppression and prosthetic joint not asked (cap). |

### morning_stiffness

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| morning_stiffness | NICE NG100 (RA); NICE CKS PMR; NICE CKS Giant cell arteritis | straight | Fields now 6: onsetWhen, stiffLength, jointSwollen, shoulderHip, headScalpJaw, visionLoss. Added: onsetWhen, stiffLength, jointSwollen, shoulderHip, headScalpJaw, visionLoss. Removed: duration. Red flags: 4. | GCA RED versus AMBER thresholds. |

### cramps

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| cramps | NICE CKS Leg cramps; NICE NG158 (DVT); limb ischaemia | straight | Fields now 7: onsetWhen, side, atNight, calfSwollen, coldPale, newMedicine, lowFluids. Added: onsetWhen, atNight, calfSwollen, coldPale, newMedicine, lowFluids. Removed: context. Red flags: 3. | Statin-related muscle symptoms: add CK advice? |

### leg_pain

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| leg_pain | NICE NG158 DVT (Wells); NICE CG147 PAD; cauda equina | straight | Fields now 9: onsetWhen, side, calfSwollen, redHot, coldPale, clotRisk, exertion, legWeak, breathless. Added: onsetWhen, calfSwollen, coldPale, clotRisk, legWeak, breathless. Removed: oneSide, q_onesided. Red flags: 6. | oneSide dropped (side has both). Exertion kept but no RF; PE signs kept to breathless only. |

### arm_pain

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| arm_pain | NICE CG95 chest pain; fracture/dislocation; stroke FAST | straight | Fields now 9: onsetWhen, injuryFirst, looksBent, side, exertion, chestTight, armJaw, breathless, numbTingle. Added: onsetWhen, injuryFirst, looksBent, exertion, chestTight, breathless, numbTingle. Removed: context. Red flags: 6. | Is numbTingle AMBER enough for sudden arm numbness? |

### foot_pain

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| foot_pain | NICE NG19 Diabetic foot; Ottawa foot rule; limb ischaemia | branched: cannotWalk only meaningful after injuryFirst (asked after, not gated) | Fields now 9: onsetWhen, injuryFirst, cannotWalk, side, diabetes, openSore, redHot, coldPale, feverish. Added: onsetWhen, injuryFirst, cannotWalk, diabetes, openSore, redHot, coldPale, feverish. Removed: context. Red flags: 7. | Gate cannotWalk on injuryFirst? Left ungated because shared field. |

### gout

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| gout | NICE CKS Gout; septic arthritis exclusion | straight | Fields now 8: onsetWhen, side, firstTime, redHot, severity, feverish, jointStuck, newMedicine. Added: onsetWhen, firstTime, severity, feverish, jointStuck, newMedicine. Removed: none. Red flags: 3. | Problem label 'Gout attack' is a disease name shown to patient; cannot be changed via patch. Suggest 'Hot painful joint'. |

### body_ache

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| body_ache | NICE NG51/NG253 sepsis; NICE NG240 meningitis; statin myopathy | straight | Fields now 9: onsetWhen, temperature, severity, nonBlanching, stiffNeckLight, confusion, breathless, lowUrine, newMedicine. Added: onsetWhen, nonBlanching, stiffNeckLight, confusion, breathless, lowUrine, newMedicine. Removed: none. Red flags: 8. | Temperature in F thresholds 100.4 and 103: confirm unit handling by Kotlin. |

### rash

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| rash | NICE CKS Meningococcal disease; SJS/TEN; shingles (ophthalmic); anaphylaxis (Resus Council UK) | conditional: rashNearEye gated on blisters; itchy removed (9 cap) | Fields now 9: onsetWhen, nonBlanching, feverish, spreading, blisters, rashNearEye, mouthEyeSores, newMedicine, lipSwelling. Added: onsetWhen, nonBlanching, feverish, rashNearEye, mouthEyeSores. Removed: itchy, q_lips, q_newmed, q_spreading. Red flags: 6. | Non-blanching RED always; is AMBER right for shingles near eye? |

### itching

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| itching | NICE CKS Pruritus; anaphylaxis (Resus Council UK) | straight | Fields now 8: onsetWhen, skinMarks, spreading, newMedicine, lipSwelling, breathless, yellowSkin, weightLoss. Added: onsetWhen, skinMarks, lipSwelling, breathless, yellowSkin, weightLoss. Removed: none. Red flags: 5. | Liver/kidney/lymphoma screens are brief; confirm. |

### hives

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| hives | Resus Council UK Anaphylaxis; BSACI urticaria | straight | Fields now 8: onsetWhen, lipSwelling, breathless, throatTight, faintDizzy, bellyVomit, trigger, newMedicine. Added: onsetWhen, throatTight, faintDizzy, bellyVomit, trigger. Removed: q_lips, q_breathless. Red flags: 5. | Only 2 danger questions in core set: throat/faint wait for 'tell more'. Raise cap for this problem? |

### allergic_reaction

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| allergic_reaction | Resus Council UK Anaphylaxis (airway, breathing, circulation, skin) | straight | Fields now 9: onsetWhen, lipSwelling, breathless, throatTight, faintDizzy, bellyVomit, trigger, newMedicine, hasPen. Added: onsetWhen, throatTight, faintDizzy, bellyVomit, trigger, hasPen. Removed: context, q_breathless, q_lips. Red flags: 6. | Problem label names 'allergic'; same core cap issue. hasPen RF text tells patient to use pen. |

### blisters

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| blisters | NICE CKS Shingles; SJS/TEN; bullous disorders | straight; mouthEyeSores ungated | Fields now 9: onsetWhen, rubbedBurnt, side, painful, spreading, oneSideBand, mouthEyeSores, feverish, newMedicine. Added: onsetWhen, rubbedBurnt, oneSideBand, mouthEyeSores, feverish, newMedicine. Removed: none. Red flags: 4. | Bullous pemphigoid in older people not separately screened. |

### sunburn

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| sunburn | NICE CKS Sunburn; heat illness | straight | Fields now 6: onsetWhen, sunArea, blisters, feverish, faintDizzy, confusion. Added: onsetWhen, sunArea, feverish, faintDizzy, confusion. Removed: burnDepth, burnSize. Red flags: 5. | Infants/children not handled (no age RF). |

### wound_not_healing

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| wound_not_healing | NICE CKS Chronic wounds/leg ulcers; NICE NG19; sepsis NG51 | branched: woundColour, woundSmell gated on woundDischarge; new woundColour replaces shared colour | Fields now 9: weeks, diabetes, woundDischarge, woundColour, woundSmell, redHot, spreading, feverish, blackSkin. Added: diabetes, woundDischarge, woundColour, woundSmell, spreading, feverish, blackSkin. Removed: colour, temperature, q_weeks. Red flags: 9. | Venous/arterial leg ulcer split not asked. |

### bed_sore

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| bed_sore | EPUAP/NPIAP categories; NICE CG179 pressure ulcers | branched: woundColour, woundSmell gated on woundDischarge; new sorePhase | Fields now 9: onsetWhen, sorePhase, woundDischarge, woundColour, woundSmell, spreading, feverish, confusion, diabetes. Added: onsetWhen, sorePhase, woundDischarge, woundColour, woundSmell, spreading, feverish, confusion, diabetes. Removed: colour, painful. Red flags: 9. | Category 2 as AMBER-this-week: confirm. |

### mole

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| mole | NICE NG12 suspected cancer (7-point checklist, ABCDE) | straight; multi-select moleChange | Fields now 4: weeks, moleChange, moleNew, skinCancerBefore. Added: weeks, moleChange, moleNew, skinCancerBefore. Removed: note. Red flags: 5. | Are 'this week' AMBER messages acceptable for 2-week-wait pathway? |

### hair_loss

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| hair_loss | NICE CKS Alopecia; thyroid/iron causes | straight | Fields now 6: weeks, hairPattern, scalpSore, newMedicine, recentStress, tiredCold. Added: hairPattern, scalpSore, newMedicine, recentStress, tiredCold. Removed: none. Red flags: 1. | No dangerous cause: checklist 9 not applicable. |

### fungal

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| fungal | NICE CKS Fungal skin infection; cellulitis differential | straight | Fields now 6: onsetWhen, itchy, spreading, redHot, diabetes, feverish. Added: onsetWhen, redHot, diabetes, feverish. Removed: none. Red flags: 3. | Nail vs scalp vs body site not asked. |

### bruising_easily

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| bruising_easily | NICE CKS Easy bruising/bleeding; petechiae | straight; bloodThinner kept first-class | Fields now 8: onsetWhen, bloodThinner, noKnock, newMedSteroid, otherBleeding, tinyDots, feverish, tiredPale. Added: onsetWhen, noKnock, newMedSteroid, otherBleeding, tinyDots, feverish, tiredPale. Removed: q_thinner. Red flags: 4. | Order: unprovoked bruising in older age on aspirin? added newMedSteroid wording 'new medicine'. |

### fall

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| fall | NICE NG249 Falls; NICE NG232 Head injury (anticoagulants); NICE CG124 hip fracture | branched: longOnFloor gated on stuckOnFloor | Fields now 9: happenedWhen, hitHead, bloodThinner, lostConsciousness, confusion, stuckOnFloor, longOnFloor, cannotWalk, dizzyBefore. Added: happenedWhen, lostConsciousness, confusion, stuckOnFloor, longOnFloor, cannotWalk. Removed: couldGetUp, timeOnFloor, context, q_hithead, q_thinner, q_getup. Red flags: 9. | couldGetUp replaced by inverted stuckOnFloor. timeOnFloor replaced by longOnFloor. |

### near_fall

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| near_fall | NICE NG249 Falls; TIA/syncope screening | branched: standingDizzy gated on dizzyBefore | Fields now 8: happenedWhen, dizzyBefore, standingDizzy, nearFaint, heartRacing, weakSide, fallsBefore, newMedicine. Added: happenedWhen, standingDizzy, nearFaint, heartRacing, weakSide, fallsBefore, newMedicine. Removed: context. Red flags: 6. | Stroke screen weakSide placed in core set by priority 98. |

### head_injury

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| head_injury | NICE NG232 Head injury (CT 1 h and 8 h criteria) | straight | Fields now 9: happenedWhen, bloodThinner, lostConsciousness, confusion, vomitedMore, seizure, headacheBad, weakVision, clearFluid. Added: happenedWhen, vomitedMore, seizure, headacheBad, weakVision, clearFluid. Removed: context, q_thinner, q_lostcon. Red flags: 8. | Age 65+ and dangerous mechanism not askable as RF; 'cantRemember' dropped (cap). |

### cut

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| cut | NICE CKS Wound management; first-aid direct pressure 10 min; tetanus | straight | Fields now 9: happenedWhen, bleedingContinues, bloodThinner, cutDeep, numbTingle, dirtInside, tetanusOld, redHot, feverish. Added: happenedWhen, bleedingContinues, cutDeep, numbTingle, dirtInside, tetanusOld, redHot, feverish. Removed: bleedingStops, q_bleedstop. Red flags: 8. | Cut is AMBER on tetanus only with dirt; confirm. |

### bleeding

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| bleeding | First aid; GI bleed, NICE CG141; NICE NG12 | conditional via bleedSite | Fields now 6: happenedWhen, bleedSite, bleedingContinues, heavyBleed, faintDizzy, bloodThinner. Added: happenedWhen, bleedSite, bleedingContinues, heavyBleed, faintDizzy. Removed: bleedingStops, q_bleedstop, q_thinner. Red flags: 8. | Pregnancy bleeding only as generic AMBER. |

### burn

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| burn | ABA/NICE CKS Burns and scalds | straight | Fields now 8: happenedWhen, burnCause, burnSite, smokeFace, burnDepth, burnSize, coolWater, redHot. Added: happenedWhen, burnCause, burnSite, smokeFace, coolWater, redHot. Removed: blisters, side. Red flags: 11. | Cannot compute %TBSA for children; blisters field removed (burnDepth covers). |

### bruise

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| bruise | NICE NG232 (head on anticoagulants); compartment syndrome | straight | Fields now 7: happenedWhen, injuryFirst, hitHead, bloodThinner, tightLump, numbTingle, redHot. Added: happenedWhen, injuryFirst, hitHead, tightLump, numbTingle, redHot. Removed: context. Red flags: 6. | Keep bruise separate from bruising_easily? |

### swelling

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| swelling | NICE NG158 DVT; cellulitis NG141; angioedema | conditional via swellSite | Fields now 9: onsetWhen, swellSite, injuryFirst, side, redHot, painful, breathless, lipSwelling, feverish. Added: onsetWhen, swellSite, injuryFirst, breathless, lipSwelling, feverish. Removed: oneSide. Red flags: 6. | oneSide removed (side has both). |

### sprain

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| sprain | Ottawa ankle and foot rules | straight | Fields now 8: happenedWhen, sprainSite, side, cannotWalk, boneTender, looksBent, numbTingle, coldPale. Added: happenedWhen, sprainSite, cannotWalk, boneTender, looksBent, numbTingle, coldPale. Removed: couldGetUp. Red flags: 5. | Midfoot/ankle malleolar tenderness sites simplified to boneTender. |

### broken_bone

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| broken_bone | NICE NG38 fractures; open fracture; neurovascular checks | conditional via boneSite | Fields now 9: happenedWhen, boneSite, side, looksBent, boneThrough, cannotUse, numbTingle, coldPale, bleedingContinues. Added: happenedWhen, boneSite, looksBent, boneThrough, cannotUse, numbTingle, coldPale, bleedingContinues. Removed: none. Red flags: 10. | All hip/spine/head RED. |

### bite

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| bite | NICE CKS Bites; UKHSA rabies guidance; tetanus | conditional via biteBy | Fields now 9: happenedWhen, biteBy, skinBroken, bleedingContinues, biteSite, tetanusOld, redHot, feverish, lipSwelling. Added: happenedWhen, biteBy, skinBroken, bleedingContinues, biteSite, tetanusOld, redHot, feverish, lipSwelling. Removed: context. Red flags: 11. | Travel history for rabies not asked separately. |

### sting

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| sting | Resus Council UK Anaphylaxis; NICE CKS Insect bites and stings | straight | Fields now 9: happenedWhen, lipSwelling, breathless, throatTight, faintDizzy, hivesBody, hasPen, spreadSwell, feverish. Added: happenedWhen, throatTight, faintDizzy, hivesBody, hasPen, spreadSwell, feverish. Removed: q_lips. Red flags: 8. | Tick bites (Lyme) not modelled. |

### choking

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| choking | Resus Council UK Choking (adult) | conditional: four questions gated on choking | Fields now 8: choking, coughStrong, cannotSpeak, blueLips, lostResponse, breathless, stuckFeeling, happenedWhen. Added: coughStrong, cannotSpeak, blueLips, lostResponse, stuckFeeling, happenedWhen. Removed: none. Red flags: 6. | Gates on choking=true; confirm UI ordering for emergencies. |

## Sign-off

Not reviewed by a clinician. Please confirm red-flag wording, RED versus AMBER levels and age-specific rules.