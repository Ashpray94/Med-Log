# Audit: general, mind and daily problems

**AI-assisted audit; not clinician-approved.** A licensed clinician must review and sign off every item before use.

Scope: 27 problems (general 16, mind 7, daily 4) from `catalogue.json`. Patch file: `app/src/main/assets/clinical/audit/general_mind.json`. `catalogue.json` and Kotlin were not edited.

Conventions: fields asked in listed order; a gate skips a question unless the earlier gate field has a listed answer. RED = emergency, AMBER = see a doctor soon. No disease name is shown to the patient. Mental-health items use calm wording and give a helpline route (Tele-MANAS 14416; emergency 112; regional numbers need clinician confirmation). Existing shared fields and questions were not changed; new ids carry help text (line 1 meaning, line 2 reason). Existing questions used here keep their original text and have no help line (not changed).

Global open questions: (a) sugar bands are mg/dL; (b) temperatures in F; (c) no "lte" flag operator, so low values (low temperature, low sugar) use bands or yes/no items; (d) no "not" in flags, so danger items are worded positively; (e) 108 (ambulance) used in patient text; confirm regional numbers.

## fever (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| fever | NICE NG51 (sepsis) and NICE CKS Fever in adults | Branched by red flags only; straight list, no gates. Gates: none | Added duration, rash, no-urine, too-weak, low-immunity. Removed chills, sweating, pattern (do not change advice). Temp and red flags added. | Is 103F AMBER / 104F RED right for adults? Low temperature (<96F) in older adults cannot be flagged (no lte operator). |

Order: howLong -> temperature -> confusion -> breathless -> stiffNeck -> feverRash -> noUrine8h -> tooWeak -> lowImmunity

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 12 rules (8 RED, 4 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: fever may be absent; low-immunity item added; diabetes not asked (covered by doctor review). 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## chills (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| chills | NICE NG51 (rigors as sepsis feature); WHO malaria guidance (fever with chills) | Straight list, no gates. Gates: none | Added duration, too-weak, no-urine, low-immunity. Kept temperature and pattern (cyclic chills suggest malaria). Removed sweating. | Should a malaria-area travel question be added for the target region? |

Order: howLong -> temperature -> pattern -> confusion -> breathless -> tooWeak -> noUrine8h -> lowImmunity

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 8 rules (4 RED, 4 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: low-immunity asked; rigors in the old may be the only infection sign. 7 removed: see "what changed". 8 visible questions: 8 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## tired (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| tired | NICE CKS Tiredness/fatigue in adults | Straight list, no gates. Gates: none | Added duration and red-flag screen (breathless, black stool, fainting, weight loss, mood, new medicine). Kept severity 0-10 and impact. | Should sleep apnoea (snoring) and thyroid symptoms be added? |

Order: howLong -> severity -> impact -> breathless -> blackStool -> lostConsciousness -> unplannedWeightLoss -> lowInterest -> newMedicine

Checklist outcome: 1 onset/duration: yes. 2 scale: severity 0-10 kept. 3 discriminating features and 5 red flags: 7 rules (3 RED, 4 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: blood loss (black stool), fainting, mood and medicines covered. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## weakness (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| weakness | NICE NG128 (stroke FAST); NICE CKS Stroke; Guillain-Barre red flags (ascending weakness) | Straight list; onset asked first. Gates: none | Replaced armWeak/oneSide with clear one-side item. Added FAST items, climbing weakness, duration, diabetes. | Should potassium-lowering medicines (water tablets) be asked? |

Order: onset -> howLong -> weakOneSide -> faceDroop -> speech -> breathless -> spreadingUp -> diabetes -> impact

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 7 rules (5 RED, 2 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: sudden onset and diabetes (sugar) covered. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## unwell (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| unwell | NICE NG51 (sepsis screen); NEWS2-style danger items | Straight list. Gates: none | Removed free-text note. Added duration, chest pain, too-weak, no-urine, diabetes and sepsis screen. | Nonspecific illness in elderly may present without fever; confirm we want to keep AMBER for long duration only. |

Order: howLong -> severity -> temperature -> confusion -> breathless -> chestDiscomfort -> tooWeak -> noUrine8h -> diabetes

Checklist outcome: 1 onset/duration: yes. 2 scale: severity 0-10 kept. 3 discriminating features and 5 red flags: 9 rules (5 RED, 4 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: atypical heart attack (chest item), diabetes, delirium (confusion). 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## no_appetite (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| no_appetite | NICE CKS Malnutrition; NICE NG12 (weight loss and cancer) | Straight list. Gates: none | Removed impact and free-text note. Added weight loss, swallowing, vomiting, black stool, yellow skin, belly pain, mood, medicines. | Should sick-day advice be shown for diabetics who stop eating? |

Order: howLong -> unplannedWeightLoss -> cantSwallowWater -> cantKeepFluids -> blackStool -> yellowSkin -> bellyPain -> lowInterest -> newMedicine

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 7 rules (2 RED, 5 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: dehydration and medicine causes covered. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## weight_loss (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| weight_loss | NICE NG12 (suspected cancer) and NICE CKS Unintentional weight loss | Conditional: onPurpose gates 8 follow-ups. Gates: weightLostKg if onPurpose in [False] | Added "trying to lose weight" gate so dieters skip all follow-ups. Replaced misleading "Reading" with kg lost. Added symptoms that point to a cause. | Is 5 kg the right flag? Some guidance uses 5 percent of body weight in 6 to 12 months. |

Order: onPurpose -> weightLostKg -> weeks -> peeingMore -> nightSweatsAlso -> longCough -> swallowHard -> blackStool -> bowelChange

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 7 rules (1 RED, 6 AMBER). 4 prerequisites: gates used. 6 older adult: Older adult: swallowing and bowel change covered. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## night_sweats (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| night_sweats | NICE NG12 and NICE CKS Night sweats/menopause; NICE NG33 (TB) | Straight list. Gates: none | Replaced vague count with nights per week. Added duration, weight loss, long cough, lumps, diabetes (night low sugar), medicines. | Should menopause be asked so benign cases get reassurance? |

Order: howLong -> nightsPerWeek -> temperature -> unplannedWeightLoss -> longCough -> lumpNeckArmpit -> diabetes -> newMedicine

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 5 rules (0 RED, 5 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: diabetes (night lows) and medicines covered. 7 removed: see "what changed". 8 visible questions: 8 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## thirsty (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| thirsty | NICE NG28 / NG17 and hyperglycaemia guidance (Diabetes UK); DKA/HHS warning signs | Conditional: sugar reading only if diabetes. Gates: none | Removed vague "going often" and note. Added sugar band, vomiting, confusion, fast breathing, vision, weight loss. | sugarBand uses mg/dL. Is that right for all users? sugarBand asked for everyone (not only diabetics) so a meter owner can answer. |

Order: howLong -> peeingMore -> diabetes -> sugarBand -> unplannedWeightLoss -> visionChange -> cantKeepFluids -> confusion -> breathless

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 8 rules (4 RED, 4 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: dehydration and diabetes emphasised. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## dehydrated (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| dehydrated | WHO dehydration assessment; NICE CG84 (diarrhoea/vomiting); NICE NG148 (AKI risk) | Conditional: dark urine asked only if urine passed. Gates: darkUrine if noUrine8h in [False] | Replaced positive-worded urine/water questions with danger-worded ones so flags work. Added cause, dizziness, confusion, weakness, water pills. | Should NSAID / SGLT2 medicines be named in help text? |

Order: howLong -> vomitOrLoose -> cantKeepFluids -> noUrine8h -> darkUrine -> dizzyOnStanding -> confusion -> tooWeak -> onWaterPills

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 6 rules (3 RED, 3 AMBER). 4 prerequisites: gates used. 6 older adult: Older adult: dizzy on standing, confusion, water tablets covered. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## low_mood (mind)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| low_mood | NICE NG222 (depression); PHQ-2 style screen; C-SSRS-style screener (calm, gated) | Conditional: plan and unsafe-now asked only after a yes to thoughts of self-harm. Gates: selfHarmPlan if selfHarm in [True]; selfHarmNow if selfHarm in [True] | Added duration, interest item (PHQ-2 half) and two gated follow-ups. Calm wording for self-harm question. Helpline in patient message. | Confirm helpline numbers for each region (Tele-MANAS 14416, KIRAN 1800-599-0019, emergency 112). |

Order: howLong -> lowInterest -> sleepHours -> impact -> selfHarm -> selfHarmPlan -> selfHarmNow

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 5 rules (3 RED, 2 AMBER). 4 prerequisites: gates used. 6 older adult: Older adult: sleep and impact retained; confusion not asked here. 7 removed: see "what changed". 8 visible questions: 7 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## anxious (mind)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| anxious | NICE CG113 (anxiety and panic); chest-pain exclusion per NICE CG95 | Straight list; heart-related items first. Gates: none | Added duration, chest pain, fast heart, blackout, medicines, calm self-harm question. Helpline in message. | Should alcohol or caffeine be asked? |

Order: howLong -> context -> chestDiscomfort -> breathless -> fastHeart -> lostConsciousness -> newMedicine -> impact -> selfHarm

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 5 rules (3 RED, 2 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: heart and blackout items first; medicines. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## cant_sleep (mind)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| cant_sleep | NICE CKS Insomnia | Straight list. Gates: none | Replaced "When" with type of sleep problem. Added breathless waking, snoring, mood, medicines, night confusion, calm self-harm question. | Should alcohol/caffeine and restless legs be added? |

Order: howLong -> sleepTrouble -> sleepHours -> wakesBreathless -> snoring -> lowInterest -> newMedicine -> confusion -> selfHarm

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 4 rules (1 RED, 3 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: night confusion (delirium) and breathless waking. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## sleep_too_much (mind)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| sleep_too_much | 4AT delirium features (alertness, acute change); NICE CG103 (delirium); NICE CKS Sleep problems | Straight list. Gates: none | Added duration, hard to wake, head injury, medicines, diabetes, mood, snoring. | Should low thyroid and low sugar be asked? |

Order: howLong -> sleepHours -> hardToWake -> confusion -> hitHead -> newMedicine -> diabetes -> lowInterest -> snoring

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 5 rules (3 RED, 2 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: head knock, delirium, medicines, diabetes. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## lonely (mind)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| lonely | NICE NG222 and WHO mhGAP (loneliness and depression screen); WHO elder abuse guidance | Straight list. Gates: none | Removed free-text note. Added loss, support, mood, sleep, safety at home, calm self-harm question. Helpline in message. | Add Elder Line 14567 (India) to the say text? |

Order: howLong -> recentLoss -> noOneToTalk -> lowInterest -> sleepHours -> unsafeAtHome -> selfHarm

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 3 rules (1 RED, 2 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: elder-abuse safety item added. 7 removed: see "what changed". 8 visible questions: 7 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## hallucination (mind)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| hallucination | 4AT and NICE CG103 (delirium); NICE CG178 (psychosis); alcohol withdrawal (NICE CG100) | Conditional: voices question only if voices. Gates: voicesHarm if hallucType in ['hearing voices', 'both'] | Added type, voices-give-orders, duration, medicines, alcohol stop. Calm self-harm question. | Should Lewy body / Parkinson medicine be named in help? |

Order: onset -> howLong -> hallucType -> voicesHarm -> confusion -> temperature -> newMedicine -> stoppedAlcohol -> selfHarm

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 7 rules (4 RED, 3 AMBER). 4 prerequisites: gates used. 6 older adult: Older adult: delirium items (confusion, fever, medicines) first. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## self_harm (mind)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| self_harm | C-SSRS screener style; NICE NG225 (self-harm); WHO mhGAP | Conditional: three follow-ups only after a yes. Gates: selfHarmNow if selfHarm in [True]; selfHarmActed if selfHarm in [True]; selfHarmPlan if selfHarm in [True] | Removed free-text note. Question order follows C-SSRS. Calm wording; every yes gives helpline route. | Confirm helpline numbers and "say" wording with a mental-health clinician. |

Order: selfHarm -> selfHarmNow -> selfHarmActed -> selfHarmPlan -> notAlone -> howLong -> lowInterest -> sleepHours

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 4 rules (4 RED, 0 AMBER). 4 prerequisites: gates used. 6 older adult: n/a older-adult specifics; safety-first path. 7 removed: see "what changed". 8 visible questions: 8 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## low_sugar (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| low_sugar | NICE NG17 / NG28 and ADA hypoglycaemia levels (54 and 70 mg/dL); Diabetes UK hypo guidance | Conditional: medicine question only if diabetes. Gates: takesSugarMedicine if diabetes in [True] | Replaced number reading with bands so low values can be flagged. Added diabetes, medicine, shaky, hard to wake, swallowing, missed meal. | Band edges 54 and 70 mg/dL; confirm. mmol/L users need conversion in help. |

Order: diabetes -> takesSugarMedicine -> sugarBand -> sweating -> shaky -> confusion -> hardToWake -> cantSwallowWater -> skippedMeal

Checklist outcome: 1 onset/duration: n/a. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 6 rules (4 RED, 2 AMBER). 4 prerequisites: gates used. 6 older adult: Older adult: diabetes, missed meals, confusion. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## high_sugar (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| high_sugar | NICE NG17 / NG28; JBDS DKA/HHS guidance; Diabetes UK hyperglycaemia | Straight list. Gates: none | Replaced number reading with bands. Added vomiting, belly pain, fast breathing, thirst, urine, duration. | Ketone testing question left out; add if the app supports ketone strips? |

Order: diabetes -> sugarBand -> howLong -> veryThirsty -> peeingMore -> cantKeepFluids -> bellyPain -> breathless -> confusion

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 7 rules (5 RED, 2 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: dehydration and confusion. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## foot_numb (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| foot_numb | NICE NG19 (diabetic foot); NICE CKS Peripheral neuropathy; cauda equina red flags (CKS Back pain) | Straight list. Gates: none | Added onset, duration, diabetes, foot sore, cold foot, one-side weakness, climbing numbness, saddle numbness. | Should alcohol and B12 be asked? |

Order: onset -> howLong -> side -> diabetes -> footSore -> footColdPale -> weakOneSide -> spreadingUp -> saddleNumb

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 7 rules (3 RED, 4 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: diabetes foot and stroke items. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## lump (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| lump | NICE NG12 (suspected cancer); NICE CKS Breast lumps / Lymphadenopathy / Hernia | Conditional: trapped-lump question only for groin or belly lumps. Gates: cannotPushBack if lumpWhere in ['groin', 'belly'] | Added lump place, growth, hardness, red/hot, fever, weight loss, trapped-gut question. Dropped "side". | Is 3 weeks right for all sites? Testicular lumps are not covered by the place list. |

Order: lumpWhere -> weeks -> painful -> lumpGrowing -> lumpHard -> lumpRedHot -> temperature -> unplannedWeightLoss -> cannotPushBack

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 8 rules (2 RED, 6 AMBER). 4 prerequisites: gates used. 6 older adult: Older adult: weight loss and hard lump items. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## pale (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| pale | NICE CKS Anaemia; NICE NG12 | Straight list. Gates: none | Replaced vague "dizzy before" with dizzy on standing. Added duration, chest pain, blackout, black stool, blood thinner, heavy periods, fast heart. | No sex field exists, so heavy-periods question shows for all; add gate? |

Order: howLong -> breathless -> chestDiscomfort -> lostConsciousness -> blackStool -> bloodThinner -> heavyPeriods -> fastHeart -> dizzyOnStanding

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 8 rules (4 RED, 4 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: blood thinner, black stool, blackout. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## side_effect (general)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| side_effect | NICE CG134 / Resuscitation Council UK (anaphylaxis); MHRA Yellow Card; serious skin reaction signs | Conditional: timing only if new medicine. Gates: whenAfterMedicine if newMedicine in [True] | Removed free text. Added timing, throat, hives, skin peeling, faint, bleeding. Anaphylaxis items first. | Should the medicine name be captured for the doctor page (free text)? |

Order: newMedicine -> whenAfterMedicine -> lipSwelling -> throatTight -> breathless -> rashHives -> skinPeeling -> lostConsciousness -> unusualBleeding

Checklist outcome: 1 onset/duration: n/a. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 8 rules (6 RED, 2 AMBER). 4 prerequisites: gates used. 6 older adult: Older adult: bleeding and faint items. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## trouble_walking (daily)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| trouble_walking | NICE NG249 (falls); NICE NG128 (stroke FAST); CKS Cauda equina; DVT signs (NICE NG158) | Conditional: head and blood-thinner asked only after a fall. Gates: hitHead if fellRecently in [True]; bloodThinner if fellRecently in [True] | Removed oneSide and painful. Added FAST items, calf swelling, saddle numbness, fall branch with head injury and blood thinner. | Pain and joint causes are no longer asked; acceptable? |

Order: onset -> weakOneSide -> faceDroop -> speech -> legSwollen -> saddleNumb -> fellRecently -> hitHead -> bloodThinner

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 8 rules (5 RED, 3 AMBER). 4 prerequisites: gates used. 6 older adult: Older adult: falls, anticoagulant, stroke. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## trouble_eating (daily)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| trouble_eating | NICE CKS Dysphagia; NICE NG128 (stroke swallow screen) | Conditional: face droop asked only if onset was sudden. Gates: faceDroop if onset in ['sudden'] | Replaced positive swallow question with danger-worded one. Added choking, mouth, pain, weight loss, stroke and breathing check. | Should denture use be asked? |

Order: onset -> howLong -> cantSwallowWater -> coughsChokes -> mouthSore -> painSwallowing -> unplannedWeightLoss -> faceDroop -> breathless

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 7 rules (3 RED, 4 AMBER). 4 prerequisites: gates used. 6 older adult: Older adult: aspiration and weight loss. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## toilet_accident (daily)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| toilet_accident | NICE NG123 (urinary incontinence); NICE CG49 (faecal incontinence); CKS Cauda equina | Straight list. Gates: none | Replaced "When" with type of accident. Added burning, retention, saddle numbness, one-sided weakness, confusion, fever. | Should medicines (water tablets, sleeping tablets) be asked? |

Order: onset -> whichAccident -> count -> burnsPeeing -> cannotPass -> saddleNumb -> weakOneSide -> confusion -> temperature

Checklist outcome: 1 onset/duration: yes. 2 scale: not needed for this symptom. 3 discriminating features and 5 red flags: 7 rules (5 RED, 2 AMBER). 4 prerequisites: none needed. 6 older adult: Older adult: delirium, urine infection, retention. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## night_pain (daily)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| night_pain | NICE NG12; NICE CKS Back pain / Chest pain; cauda equina red flags | Conditional: chest follow-ups and saddle numbness by pain place. Gates: armJaw if painWhere in ['chest']; breathless if painWhere in ['chest'] | Added pain place, duration, chest branch, cancer history, weight loss, saddle numbness. Kept severity and side. | saddleNumb is asked for all places (gate on back needs an extra question id); acceptable? |

Order: painWhere -> howLong -> severity -> side -> armJaw -> breathless -> cancerHistory -> unplannedWeightLoss -> saddleNumb

Checklist outcome: 1 onset/duration: yes. 2 scale: severity 0-10 kept. 3 discriminating features and 5 red flags: 7 rules (3 RED, 4 AMBER). 4 prerequisites: gates used. 6 older adult: Older adult: cancer and weight loss, chest branch. 7 removed: see "what changed". 8 visible questions: 9 (max 9). 9 danger question present: yes. 10 plain words: asks checked at 12 words or fewer; every new field and question has 2-line help.

## Sign-off

Clinician name, registration number, date: ____________________
