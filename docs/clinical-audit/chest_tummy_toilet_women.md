# Audit: chest, tummy, toilet, women

**AI-assisted audit; not clinician-approved.** A licensed clinician must review and sign off every line before use.

Patch: `app/src/main/assets/clinical/audit/chest_tummy_toilet_women.json` (catalogue.json and Kotlin untouched).

Scope: 40 problems (12 chest, 10 tummy, 11 toilet, 7 women). 317 red flags, 117 new or changed fields, 118 questions.

## Checklist outcomes (all problems)

- Onset/duration: numeric fields (days, hours, minutes, weeks) so flags can use them; old free-text duration removed.
- Severity: kept only where it changes advice (belly, side, period pain); dropped elsewhere.
- Prerequisites: blood before coffee-ground (gate); puffer before puffer-failed; one side before calf pain; lying flat before pillows; BP tablets before missed tablets.
- Older-adult modifiers: blood thinner, confusion, dehydration (no urine 8h, cannot keep fluids), faint; falls and diabetes only partly covered (see open questions).
- Limits: max 9 visible questions each (vomiting lists 10 ids, 9 show by age/gate). At least one danger question for each dangerous symptom.
- Engine limits: red flags support only gte, so 'low' readings use bands (oxygen, top BP). No age or sex in red flags; no sex gate for pregnancy questions.
- Shared ids not changed: colour, blood, severity, side, count, context, duration etc. Only `projectile` (vomiting only) got age limit and help; `pillows` got a gate and help.
- Cross-agent: nothing written for `cough`; coffeeGround only in vomiting and vomit_blood.


## Chest & breathing (12)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| chest_pain | AHA/ACC 2021 chest pain guideline; NICE CG95 | straight; pillows-style gates none | Dropped duration text, onset, severity (no advice change). Added minutes-so-far, pain on breathing, faint, sudden/worst-ever. Danger Qs: arm/jaw, sweat, breath, faint, worst-ever. | Is 15 minutes the right RED cut-off? Should diabetic or older people (silent ACS) be RED without typical features? Age cannot be used in red flags. |
| chest_tight | AHA/ACC 2021 chest pain guideline; NICE CG95 | straight | Duration text replaced by minutes; added at-rest and sickness. Tightness at rest or with sickness is RED. | Is sickness alone RED or AMBER? |
| palpitations | NICE CKS Palpitations; ESC SVT/AF guidance | straight | Added minutes, steady vs uneven, pulse count, chest pain, faint. Removed vague 'dizzy before' and 'reading'. | Pulse below 40 cannot be flagged (only gte supported). Family history of sudden death not asked. Caffeine and thyroid not asked. |
| breathless | NICE CKS Breathlessness; BTS oxygen guidance | branched: pillows only if worse lying flat | Added onset, talk test, blue lips, pain on breathing, confusion, oxygen band. Oxygen as band (gte-only engine). Removed exertion (at-rest question covers). | COPD target 88-92 percent: is 'below 92 RED' safe for them? Calf swelling (DVT) not asked here. Wells/PERC not applied. |
| wheeze | BTS/SIGN asthma; GINA; anaphylaxis (Resus Council UK) | branched: puffer-not-helping only if uses puffer | Added puffer use/failure, talk test, blue lips, lip swelling (existing q_lips), oxygen band. Removed generic breathless Q. | Peak-flow not asked. Foreign body in children? Add child-specific flow? |
| cough | NICE CKS Cough; NICE NG12; NTEP (India) | NOT PATCHED (other agent owns flow) | No change by instruction. GAPS: no red flag for blood in cough (AMBER/RED); no talk-test or blue lips; no fever; no weight loss or night sweats (TB screen); no chest pain on breathing; no 2-3 week AMBER; no choking/child croup or whooping features; no smoker or ACE-inhibitor question. | Who adds a blood-in-cough RED flag and a weeks gte 2-3 TB AMBER flag? Field 'colour' left untouched. |
| cough_blood | NICE NG12 (haemoptysis); BTS; NICE CKS | straight | Added amount bands, fever, weight loss, pain on breathing, blood thinner Q, duration. Removed nothing. | Volume cut-offs for 'massive' (100-200 mL) are approximate. Smoking history not asked. |
| low_oxygen | BTS emergency oxygen guideline | straight | Replaced unlabeled number with oxygen bands. Added at-rest, talk test, blue lips, confusion. | Reliability of finger meters (cold hands, dark skin, nail polish) not asked. COPD targets. |
| swollen_ankles | NICE CKS Oedema; NICE NG158 (DVT, Wells); NICE NG106 (HF) | branched: painful calf only if one leg | Added duration, calf pain, weight gain, pain on breathing. Kept red/hot, lying flat. | Medicine-induced swelling (amlodipine) not asked. Pregnancy swelling/pre-eclampsia not covered. |
| snoring | NICE NG202 (OSA); STOP-BANG | straight | Replaced free-text note with snore, witnessed pauses, gasping, sleepiness, BP. Danger Qs: drowsy driving, night chest pain. | Neck size, BMI, age not asked. Driving advice wording (DVLA style vs Indian rules). |
| high_bp | NICE NG136; ESH/ESC hypertension guidance | branched: missed tablets only if on tablets | Replaced single reading with top and bottom numbers. Added stroke signs, chest pain, breathless, vision, worst headache. 180/120 plus symptoms is RED. | Should 180/120 without symptoms be RED? Home vs clinic reading not asked. Pregnancy pre-eclampsia thresholds (140/90) not covered. |
| low_bp | NICE CKS Hypotension/Syncope; Surviving Sepsis | straight | Replaced reading with band (gte-only engine). Added fluid loss, bleeding, faint, chest pain, confusion, fever. Kept standing. | Diabetic/insulin users and drugs (diuretics, alpha-blockers) only partly covered. Heart rate not asked. |

## Tummy (10)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| stomach_pain | NICE CKS Acute abdomen/Abdominal pain; RCS acute abdomen | straight | Removed worse/better/context/character. Added hours, onset, location (8 zones), hard belly, no wind, blood, fever, faint. Severity 8+ RED. | Pregnancy/ectopic (women 14-50) cannot be asked: engine has no sex gate. AAA in older men: rely on sudden+severe. Right lower pain AMBER only. |
| acidity | NICE CG184 dyspepsia alarm features; AHA/ACC (chest mimic) | straight | Removed free-text context. Added duration, effort, weight loss, food sticking, heart screen, blood. | Age 55+ new indigestion not flaggable. Is effort-related burning chest AMBER enough? |
| nausea | NICE CKS Nausea/Vomiting in adults; dehydration guidance | straight | Removed free-text context. Added days, new medicine, cannot keep fluids, no urine 8h, head/neck/confusion, chest pain, blood, hard belly. | Pregnancy, diabetic ketoacidosis and medicine toxicity (digoxin) not asked. |
| vomiting | NICE CKS Gastroenteritis; BSG upper GI bleed; obstruction/acute abdomen | branched: coffee-ground only if blood; no-urine only useful with fluids; projectile under age 2, head/neck over age 3 | coffeeGround kept ONLY here and vomit_blood (gated on blood). Dropped colour/content/context (shared or redundant). Added days, green bile, hard belly, head/neck, dehydration. Lists hold 10 ids but max 9 show per age. | Diabetic sick-day rules, pregnancy (hyperemesis), infant feeding not covered. Is 6+ vomits AMBER right? |
| vomit_blood | BSG/NICE NG141 upper GI bleed; Glasgow-Blatchford items | straight | Added amount, black stool, faint, painkillers, blood thinner. Everything RED. | Liver disease/alcohol (varices) not asked for sensitivity. Pulse/BP at home not asked. |
| bloating | NICE NG12 (ovarian cancer persistent bloating); CKS; obstruction | straight | Removed context. Added weeks, daily bloat, early fullness, weight loss, fast swelling, no wind, vomiting, hard belly, blood. | Sex/age 50+ ovarian rule cannot be gated; reason text mentions women. |
| burp | NICE CG184 alarm features; AHA/ACC | straight | Removed count/context. Added weeks, weight loss, food sticking, effort, heart screen, blood, vomiting. | Low value problem; is 4-week persistent rule needed? |
| hiccup | NICE CKS Hiccups | straight | Replaced free-text duration with hours (48h AMBER). Added new medicine, stroke signs, breath, arm/jaw, vomiting. | Chemotherapy/steroid history not asked. |
| swallowing | NICE NG12 (dysphagia urgent referral); CKS | straight | Replaced yes/no water Q and context with what is hard to swallow. Added spit, stuck food, choking, regurgitation, weight loss, blood, breath. | Is any new swallowing trouble AMBER within days correct for age under 55? |
| jaundice | NICE CKS Jaundice; paracetamol overdose guidance; NICE CG98 (newborn) | straight | Replaced generic colour and temperature. Added days, dark urine, pale stool, itch, upper right pain, fever, confusion, paracetamol, blood. | NEWBORN jaundice (under 28 days) needs an age-based flag: engine cannot express. Alcohol/hepatitis exposure not asked. |

## Toilet (11)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| loose_motions | NICE CKS Diarrhoea/Gastroenteritis; WHO dehydration | straight | Removed stool type and mucus. Added days, count, dehydration pair, black stool, fever, hard belly, confusion. | Infants/elderly need own thresholds. Travel, antibiotics, cholera-area not asked. SGLT2/diuretic sick-day advice. |
| constipation | NICE CKS Constipation; NG12; obstruction | straight | Removed stool type/count/painful. Added days without stool, bowel change, new medicine, no wind, swollen belly, vomiting, hard belly, blood. | Opioid use, faecal impaction in frail elders, pain at anus not covered. |
| blood_stool | NICE NG12 rectal bleeding; BSG lower GI bleed | straight | Replaced shared colour with blood-specific colour and amount. Added faint, hard belly, bowel change, weight loss. | Piles vs cancer: age 40+ not gateable. Is bright red alone AMBER right? |
| black_stool | BSG/NICE NG141 melaena | straight | Added days, vomited blood, faint, blood thinner, breathless. Iron/bismuth question left out (would not change advice). | Should iron or pink stomach medicine downgrade from RED? |
| burning_urine | NICE NG109 (UTI); NG111/NG112; CKS pyelonephritis; NICE NG51 sepsis | straight | Added days, flank pain, no urine 8h, shaking chills, fever, confusion, fluids. | Pregnancy and men's UTI, STI/discharge not asked. Catheter use not asked. Fever cut-off 100.4F (38C). |
| frequent_urine | NICE CKS LUTS in men; diabetes; NICE CG97 | straight | Added weeks, per day, night trips, thirst, weight loss, burning, blood, poor emptying, fever. | Diabetes thresholds and pregnancy not covered. Medicine (diuretic timing) not asked. |
| no_urine | NICE CKS Acute urinary retention; cauda equina guidance; AKI (NICE NG148) | straight | Added bladder pain, only drops, numb bottom, fever, confusion, cannot keep fluids. 6h RED. | Is 6h cut-off right? Medicine/constipation causes not asked. |
| blood_urine | NICE NG12 haematuria; CKS | straight | Replaced shared colour. Added colour bands, days, burning, flank pain, weight loss, blood thinner, fever, no urine 8h. | Age 45+ urgent rule not gateable. Smoking and dye exposure not asked. |
| leaking_urine | NICE NG123 (incontinence); cauda equina guidance | straight | Replaced count/context. Added onset, weeks, leak pattern, emptying, burning, numb bottom, stool leak, confusion, fever. | Pad use and fluid intake not asked. Post-birth incontinence not covered. |
| kidney_pain | NICE CKS Renal colic; EAU urolithiasis; AAA guidance | straight | Replaced blood field. Added onset, waves, urine blood, fever, chills, no urine, faint. | Older person with sudden severe flank pain: is RED at 8+ right? Pregnancy not covered. |
| face_swelling_morning | NICE CKS Oedema/Angioedema; Resus Council anaphylaxis | straight | Replaced context. Added onset, weeks, leg swelling, foamy urine, less urine, lip swelling, breath, vision. | Pregnancy pre-eclampsia, thyroid, medicine (ACE inhibitors) not asked. |

## Women's health (7)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| period_pain | NICE CKS Dysmenorrhoea; RCOG ectopic/PID guidance | straight; pregnancy and late period shown only ages 12-50/55 | Added side, new pain, late, could be pregnant, faint, fever, soaking pads, hard belly. | Sensitivity of pregnancy Q for young teens. Sex gating by profile not available. |
| heavy_bleeding | NICE NG88 (HMB); RCOG; early pregnancy loss guidance | straight | Replaced count. Added days, pads per hour, clots, pregnancy, late period, faint, breath, blood thinner. | Anaemia symptoms and clot size (coin) wording. Age under 12 and over 55 not covered. |
| postmeno_bleeding | NICE NG12 (PMB 2-week pathway) | straight; age 40 plus months field | Replaced count. Any bleeding AMBER; heavy RED. Added months since period, amount, days, hormones, faint, pelvic pain. | Is AMBER the right label for an urgent cancer-pathway symptom? |
| discharge | NICE CKS Vaginal discharge; BASHH; PID | straight | Replaced shared colour. Added days, colour, smell, burning, new partner, pregnancy, pelvic pain, fever. | Sexual-history wording; genital sores and discharge in men not covered. |
| breast_lump | NICE NG12 (breast 2-week pathway) | straight | Removed painful. Added weeks, growing, hard/fixed, skin, nipple, armpit, red/hot, fever. All AMBER. | Men's breast lumps. Is AMBER right for hard/fixed (same-week)? |
| breast_pain | NICE CKS Mastalgia/Mastitis; AHA/ACC (chest mimic) | straight | Added weeks, cyclic, lump, red/hot, breastfeeding, fever, heart screen. | Is 'pain in left breast plus arm' RED too cautious? |
| hot_flush | NICE NG23 (menopause); CKS | straight | Added weeks, per day, periods changed, night soaking, weight loss, racing heart, fever, post-meno bleeding. | Low mood/self-harm screen left to Mind group; confirm. Early menopause under 40 needs age gate. |

## Cough gaps (for the other agent)

See the `cough` row. Blood-in-cough RED flag and TB-duration AMBER flag are the priority.
