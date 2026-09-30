# Clinical audit: head group (40 problems)

**AI-assisted audit; not clinician-approved.** A licensed clinician must review and sign off every line before real-world use.

Patch: `app/src/main/assets/clinical/audit/head.json` (catalogue.json not edited).

## Checklist outcome (applies to all 40)

1. Onset and duration: the fixed WHEN question covers start time for every problem. Onset speed (sudden/slow) added where it changes urgency. Duration beyond one week is only a clinician gap (see open questions).
2. Severity: 0-10 scale kept only for pain problems (headache, migraine, ear, throat, eye, sinus, tooth). Dropped elsewhere.
3. Discriminating features: only those that change advice were added.
4. Prerequisites: earlier questions gate later ones (dizzy, numbness, tingling, red_eye). Nosebleed asks about stopping before time.
5. Red-flag screen per standard in each table; 130 red flags, erring on recall.
6. Older-adult modifiers: blood thinner, diabetes, falls, dehydration, delirium, new medicine added where relevant.
7. Removed: free-text 'context', 'count' and 'note' questions, colour of runny nose, and similar non-discriminating items.
8. Max 9 visible questions per problem (checked by script).
9. Every problem with a dangerous cause has at least one danger question (priority 85 or more).
10. Plain words, 12 words or fewer per sentence (checked by script). Every new field and question has a two-line help.

Notes: `q_vision`, `q_worst`, `q_temp`, `q_severity`, `q_weeks`, `q_thinner`, `q_bleedstop` are existing shared questions and were reused unchanged. The shared field `worstEver` (label 'Sudden, worst ever') keeps its meaning; a new question `q_hd_headsame` asks it as 'sudden very bad headache with it'. Existing field `newMedicine` was reused.

### headache (Headache)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| headache | NICE CKS Headache-assessment; NICE CG150; SNNOOP10; GCA per BSR/NICE CKS | straight | Was fields ['side', 'character', 'severity', 'worstEver', 'visionChange', 'stiffNeck', 'onset', 'context'] + fu ['q_worst', 'q_vision', 'q_severity']. Now 9 questions: q_worst, q_hd_stiff, q_strokeSigns, q_temp, q_scalpJawPain, q_vision, q_headKnockWeeks, q_hd_thinner, q_severity. 10 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### migraine (Migraine)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| migraine | NICE CG150 / CKS Migraine; SNNOOP10; aura vs stroke (AHA) | straight | Was fields ['side', 'severity', 'visionChange', 'worstEver'] + fu ['q_worst', 'q_severity']. Now 9 questions: q_migraineUsual, q_worst, q_hd_stiff, q_strokeSigns, q_scalpJawPain, q_temp, q_sickManyTimes, q_painkillerDays, q_severity. 8 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### dizzy (Dizzy)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| dizzy | NICE CKS Dizziness; BE-FAST (AHA/ASA); NICE NG128 | branched: q_dizzyLasts gated on spinning=yes | Was fields ['spinning', 'onStanding', 'count', 'duration', 'context'] + fu ['q_standing', 'q_spinning']. Now 9 questions: q_spinning, q_standing, q_dizzyLasts, q_strokeSigns, q_heartSigns, q_dizzyHeadache, q_bleedSigns, q_hasDiabetes, q_hd_thinner. 8 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### fainted (Fainted)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| fainted | NICE CG109 Transient loss of consciousness; ESC syncope 2018 | straight | Was fields ['hitHead', 'duration', 'dizzyBefore', 'fits', 'context'] + fu ['q_hithead']. Now 9 questions: q_hithead, q_faintCardiac, q_notRecovered, q_standing, q_hd_warning, q_hd_faintjerk, q_hd_thinner, q_bleedSigns, q_hasDiabetes. 8 red flags added. Problem is marked red in catalogue; escalation flags added. | Confirm thresholds and RED/AMBER levels for this problem. |

### fits (Fit / seizure)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| fits | NICE NG217 Epilepsies; UK Resus Council status epilepticus | straight | Was fields ['duration', 'hitHead', 'lostConsciousness'] + fu ['q_hithead']. Now 9 questions: q_fitLong, q_fitRepeat, q_firstEverFit, q_hithead, q_notRecovered, q_hasDiabetes, q_hd_thinner, q_temp, q_hd_stiff. 9 red flags added. Problem is marked red in catalogue; escalation flags added. | Confirm thresholds and RED/AMBER levels for this problem. |

### face_droop (Face drooping)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| face_droop | BE-FAST / FAST (AHA/ASA); NICE NG128 stroke | straight | Was fields ['side', 'armWeak', 'speech'] + fu ['q_armweak', 'q_speech']. Now 9 questions: side, q_armweak, q_speech, q_balanceLost, q_vision, q_hd_headsame, q_settledAlready, q_earPainRash, q_lastWell. 7 red flags added. Problem is marked red in catalogue; escalation flags added. | Confirm thresholds and RED/AMBER levels for this problem. |

### speech_trouble (Speech trouble)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| speech_trouble | BE-FAST (AHA/ASA); NICE NG128 | straight | Was fields ['faceDroop', 'armWeak'] + fu ['q_face', 'q_armweak']. Now 9 questions: q_speechType, q_face, q_armweak, q_settledAlready, q_hd_headsame, q_balanceLost, q_vision, q_lastWell, q_hasDiabetes. 7 red flags added. Problem is marked red in catalogue; escalation flags added. | Confirm thresholds and RED/AMBER levels for this problem. |

### one_side_weak (Weak on one side)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| one_side_weak | BE-FAST; NICE NG128; NICE CKS cord compression / GBS red flags | straight | Was fields ['side', 'faceDroop', 'speech'] + fu ['q_face', 'q_speech']. Now 9 questions: onset, side, q_weakWhere, q_face, q_speech, q_settledAlready, q_hd_headsame, q_bladderBowelChange, q_legsWeakening. 8 red flags added. Problem is marked red in catalogue; escalation flags added. | Confirm thresholds and RED/AMBER levels for this problem. |

### numbness (Numbness)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| numbness | BE-FAST; NICE CKS Neuropathy; cauda equina guidance (NICE CKS Back pain) | branched: side gated on oneSide=yes | Was fields ['side', 'oneSide', 'duration'] + fu ['q_armweak']. Now 9 questions: onset, q_numbWhere, q_hd_oneside, q_hd_side, q_hd_dur, q_strokeSigns, q_bladderBowelChange, q_saddleNumb, q_hasDiabetes. 7 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### tingling (Tingling)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| tingling | NICE CKS Peripheral neuropathy; BE-FAST; anaphylaxis (Resus Council UK) | branched: side gated on tinglingWhere=one limb | Was fields ['side', 'context'] + fu []. Now 9 questions: onset, q_tinglingWhere, q_hd_tside, q_strokeSigns, q_swellingLipsThroat, q_legsWeakening, q_bladderBowelChange, q_hasDiabetes, q_lowB12Diet. 8 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### confusion (Confused)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| confusion | NICE CG103 Delirium; BE-FAST; sepsis NICE NG51 | straight | Was fields ['onset', 'temperature', 'context'] + fu ['q_temp']. Now 9 questions: onset, q_strokeSigns, q_hardToWake, q_temp, q_hasDiabetes, q_headKnockWeeks, q_notDrinking, q_urineBurning, q_newMedicine. 9 red flags added. Problem is marked red in catalogue; escalation flags added. | Confirm thresholds and RED/AMBER levels for this problem. |

### memory (Forgetting things)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| memory | NICE NG97 Dementia; WHO mhGAP; TIA/transient amnesia red flags | straight | Was fields ['note'] + fu []. Now 9 questions: q_memoryCourse, q_strokeSigns, q_headKnockWeeks, q_repeatsQuestions, q_gotLost, q_dailyTasksHarder, q_moodLow, q_newMedicine, q_hasDiabetes. 8 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### tremor (Shaking hands)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| tremor | NICE NG71 Parkinson's; NICE CKS Tremor; alcohol withdrawal NICE CG115 | straight | Was fields ['side', 'context'] + fu []. Now 8 questions: onset, side, q_tremorWhen, q_slowStiff, q_newMedicine, q_heartRacingSweat, q_alcoholCut, q_strokeSigns. 7 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### balance (Poor balance)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| balance | NICE CKS Dizziness; WHO falls in older adults; BE-FAST | straight | Was fields ['dizzyBefore', 'context'] + fu []. Now 9 questions: onset, q_spinning, q_standing, q_feetNumb, q_strokeSigns, q_headacheWithBalance, q_newMedicine, q_fellRecently, q_hasDiabetes. 7 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### earache (Earache)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| earache | NICE CKS Otitis media/externa; NICE NG91; malignant otitis externa guidance | straight | Was fields ['side', 'severity', 'temperature'] + fu ['q_severity']. Now 8 questions: side, q_severity, q_temp, q_swollenBehindEar, q_face, q_hasDiabetes, q_hearingDrop, q_earFluid. 8 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### ear_discharge (Ear discharge)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| ear_discharge | NICE CKS Otitis externa/media; head injury NICE NG232 (CSF leak signs) | straight | Was fields ['side', 'colour'] + fu []. Now 9 questions: side, q_earDischargeColour, q_afterHeadKnock, q_hd_painful, q_swollenBehindEar, q_face, q_hasDiabetes, q_hearingDrop, q_temp. 8 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### ringing_ears (Ringing in ears)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| ringing_ears | NICE NG155 Tinnitus; NICE CKS Hearing loss (sudden SNHL) | straight | Was fields ['side'] + fu []. Now 8 questions: onset, side, q_hearingDrop, q_pulseTinnitus, q_spinning, q_strokeSigns, q_newMedicine, q_tinnitusDistress. 8 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### hearing_loss (Hearing less)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| hearing_loss | NICE NG98 Hearing loss in adults; NICE CKS sudden SNHL | straight | Was fields ['side', 'onset'] + fu []. Now 9 questions: onset, side, q_earBlocked, q_hd_painful, q_earFluid, q_hasRinging, q_spinning, q_strokeSigns, q_newMedicine. 7 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### sore_throat (Sore throat)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| sore_throat | NICE NG84 / CKS Sore throat; FeverPAIN/Centor; NICE NG51 | straight | Was fields ['severity', 'temperature', 'swallowWater'] + fu ['q_swallow']. Now 8 questions: q_breathTrouble, q_swallow, q_mouthOpen, q_temp, q_severity, q_lowImmune, q_neckLump, q_weeks. 8 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### hoarse (Hoarse voice)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| hoarse | NICE NG12 (suspected cancer, hoarseness over 3 weeks, age 45+); CKS Laryngitis | straight | Was fields ['weeks'] + fu ['q_weeks']. Now 8 questions: q_breathTrouble, q_weeks, q_usesTobacco, q_swallowHurts, q_neckLump, q_bloodInSpit, q_lostWeight, q_afterColdOrShouting. 6 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### runny_nose (Runny nose)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| runny_nose | NICE CKS Rhinitis / Sinusitis; NG79; CSF rhinorrhoea red flag | straight | Was fields ['colour', 'temperature'] + fu []. Now 8 questions: q_clearFluidAfterKnock, q_hd_oneside, q_noseBloodStained, q_eyelidSwollen, q_temp, q_faceSinusPain, q_doubleWorse, q_itchySneeze. 5 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### blocked_nose (Blocked nose)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| blocked_nose | NICE CKS Nasal obstruction; NG12 unilateral obstruction | straight | Was fields ['side'] + fu []. Now 8 questions: side, q_weeks, q_afterNoseKnock, q_noseBloodStained, q_sprayOverWeek, q_faceSinusPain, q_eyelidSwollen, q_itchySneeze. 5 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### sneeze (Sneezing)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| sneeze | NICE CKS Allergic rhinitis; Resus Council UK anaphylaxis | straight | Was fields ['count', 'context'] + fu []. Now 6 questions: q_swellingLipsThroat, q_breathTrouble, q_afterNewTrigger, q_itchySneeze, q_sneezeTrigger, q_temp. 4 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### nosebleed (Nose bleeding)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| nosebleed | NICE CKS Epistaxis | straight | Was fields ['duration', 'bleedingStops', 'bloodThinner'] + fu ['q_bleedstop', 'q_thinner']. Now 9 questions: side, q_bleedstop, q_thinner, q_bleedMinutes, q_feelFaint, q_afterHeadKnock, q_otherBleeding, q_bleedsOften, q_highBpKnown. 8 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### sinus (Sinus pain)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| sinus | NICE NG79 Sinusitis; CKS orbital complications red flags | straight | Was fields ['side', 'severity'] + fu []. Now 8 questions: side, q_severity, q_temp, q_eyelidSwollen, q_vision, q_hd_stiff, q_doubleWorse, q_weeks. 6 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### no_smell (Lost smell or taste)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| no_smell | NICE CKS Anosmia; ENT UK; head injury NICE NG232 | straight | Was fields ['onset'] + fu []. Now 9 questions: onset, q_smellOrTaste, q_noseBlockedNow, q_temp, q_afterHeadKnock, q_strokeSigns, q_noseBloodStained, q_newMedicine, q_appetiteLow. 5 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### eye_pain (Eye pain)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| eye_pain | NICE CKS Red eye; acute angle-closure glaucoma (NICE NG81); GCA (BSR) | straight | Was fields ['side', 'severity', 'visionChange'] + fu ['q_vision']. Now 9 questions: side, q_eyeInjury, q_vision, q_redEye, q_lightHurts, q_eyeHalos, q_sickWithEye, q_contactLens, q_severity. 8 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### red_eye (Red eye)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| red_eye | NICE CKS Red eye; College of Optometrists / RCOphth red-eye triage | branched: halos and light gated on painful=yes; gunk gated on painful=no | Was fields ['side', 'painful', 'visionChange'] + fu ['q_vision']. Now 9 questions: side, q_eyeInjury, q_hd_painful, q_vision, q_re_halos, q_re_light, q_contactLens, q_rashForehead, q_re_gunk. 8 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### watery_eye (Watery eyes)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| watery_eye | NICE CKS Watering eye; Bell's palsy (NICE CKS) | straight | Was fields ['side'] + fu []. Now 8 questions: side, q_hd_painful, q_vision, q_eyeInjury, q_lumpInnerCorner, q_cannotCloseEye, q_itchySneeze, q_weeks. 7 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### blurred_vision (Blurred vision)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| blurred_vision | NICE CKS Sudden vision loss; RCOphth; BE-FAST; GCA (BSR) | straight | Was fields ['side', 'onset'] + fu []. Now 9 questions: onset, side, q_hd_painful, q_scalpJawPain, q_strokeSigns, q_curtainShadow, q_eyeHalos, q_hasDiabetes, q_thirstPee. 8 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### vision_loss (Sudden vision loss)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| vision_loss | NICE CKS Sudden vision loss / retinal detachment; RCOphth; GCA (BSR) | straight | Was fields ['side', 'onset'] + fu []. Now 9 questions: onset, side, q_settledAlready, q_strokeSigns, q_curtainShadow, q_flashesLight, q_hd_painful, q_scalpJawPain, q_eyeInjury. 9 red flags added. Problem is marked red in catalogue; escalation flags added. | Confirm thresholds and RED/AMBER levels for this problem. |

### double_vision (Seeing double)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| double_vision | NICE CKS Diplopia; BE-FAST; RCOphth; GCA (BSR) | straight | Was fields ['onset', 'faceDroop'] + fu ['q_face']. Now 9 questions: onset, q_doubleGoesOneClosed, q_strokeSigns, q_eyelidDroop, q_hd_headsame, q_hd_painful, q_scalpJawPain, q_worseEvening, q_hasDiabetes. 9 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### dry_eyes (Dry eyes)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| dry_eyes | NICE CKS Dry eye; RCOphth | straight | Was fields ['side'] + fu []. Now 8 questions: side, q_hd_painful, q_vision, q_lightHurts, q_contactLens, q_dryMouthToo, q_cannotCloseEye, q_newMedicine. 6 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### floaters (Floaters or flashes)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| floaters | NICE CKS Flashes and floaters (same-day retinal assessment) | straight | Was fields ['side', 'onset'] + fu []. Now 9 questions: side, onset, q_flashesLight, q_manyFloaters, q_curtainShadow, q_vision, q_eyeInjury, q_shortSighted, q_hasDiabetes. 8 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### itchy_eyes (Itchy eyes)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| itchy_eyes | NICE CKS Allergic/ Red eye; RCOphth | straight | Was fields ['side'] + fu []. Now 9 questions: side, q_hd_painful, q_vision, q_lightHurts, q_contactLens, q_itchySneeze, q_eyeDischarge, q_eyelidSwollen, q_rashForehead. 8 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### toothache (Toothache)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| toothache | NICE CKS Acute dental problems; SDCEP; AHA/ACC chest pain (referred jaw pain) | straight | Was fields ['side', 'severity', 'context'] + fu ['q_severity']. Now 9 questions: side, q_toothChest, q_faceSwollen, q_breathTrouble, q_swallow, q_mouthOpen, q_temp, q_severity, q_painWakes. 9 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### bleeding_gums (Bleeding gums)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| bleeding_gums | NICE CKS Gingivitis / bleeding; BSH anticoagulant bleeding | straight | Was fields ['bloodThinner'] + fu []. Now 8 questions: q_bleedOnlyBrushing, q_bleedSpont, q_bleedstop, q_thinner, q_otherBleeding, q_tiredPale, q_temp, q_gumPus. 7 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### mouth_ulcer (Mouth ulcer)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| mouth_ulcer | NICE CKS Aphthous ulcer; NICE NG12 (ulcer over 3 weeks) | straight | Was fields ['count', 'weeks'] + fu ['q_weeks']. Now 9 questions: q_weeks, q_ulcerNumber, q_usesTobacco, q_ulcerHard, q_neckLump, q_hd_painful, q_painStopsDrinking, q_newMedicine, q_temp. 9 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### denture_pain (Denture pain)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| denture_pain | NICE CKS Denture-related problems; NG12 oral lesions | straight | Was fields ['side'] + fu []. Now 9 questions: side, q_dentureLoose, q_sorePatch, q_weeks, q_usesTobacco, q_ulcerHard, q_whitePatch, q_wearAtNight, q_faceSwollen. 5 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

### dry_mouth (Dry mouth)

| id | standard used | flow | what changed | open questions for clinician |
|---|---|---|---|---|
| dry_mouth | NICE CKS Dry mouth; diabetes/dehydration red flags | straight | Was fields ['note'] + fu []. Now 8 questions: q_newMedicine, q_veryThirsty, q_hasDiabetes, q_notDrinking, q_drowsy, q_dryMouthToo, q_cheekSwollen, q_whitePatch. 6 red flags added.  | Confirm thresholds and RED/AMBER levels for this problem. |

## Open questions (whole group)

- Fixed WHEN tops out at 'a week or more'; weeks-level duration for throat, ulcer, hoarse, blocked nose uses `q_weeks`. Confirm cut-offs (3 weeks for NG12 items, 4 weeks for watery eye, 10 days for sinus).
- Fever threshold used is 100.4 F (38 C) and 102 F for high fever. Confirm.
- Stroke, sudden vision loss, status epilepticus and airway flags are RED with 'call emergency'. Confirm local emergency wording.
- Loss of smell: add a safety line about gas and smoke alarms in the UI (not a question).
- Nosebleed 'not stopping' is RED at 20 minutes. Confirm home-care advice (lean forward, pinch 10-15 minutes).
- Family history of sudden death in fainting is not asked (cap of 9). Consider a 10th slot.
- Red-flag conditions read by the doctor page only: no disease name is shown to the patient.
- Only two danger questions are asked in the core set; flags depend on later answers when the patient skips 'tell more'. Consider raising the core cap for stroke problems.
