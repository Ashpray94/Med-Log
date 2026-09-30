# Substance patch dimension tags

This note records dimension tags added for field and question IDs introduced by
`app/src/main/assets/clinical/audit/substances.json`. It is a tagging audit only;
it does not approve the clinical content. The catalogue remains unreviewed.

| Field ID | Dimension(s) | Match to `standard.json` |
|---|---|---|
| `sputumAmount` | `amount` | How much came out. |
| `sputumConsistency` | `consistency` | Watery, sticky or frothy texture. |
| `sputumSmell` | `smell` | Whether it smells unusually bad. |
| `sputumWhen` | `timing` | Time of day it is worst. |
| `coughBreathTalk` | `danger` | The field is marked dangerous; a talk test does not establish breathlessness at rest. |
| `coughBreathWalk` | `exertion_tolerance` | Breathlessness during a short walk. |
| `coughWheeze` | `wheeze` | Whistling or noisy breathing. |
| `vomitAmount` | `amount` | How much came up, including inability to keep anything down. |
| `vomitColour` | `colour` | Colour of the vomit. |
| `vomitShade` | `shade` | Whether the colour is pale or dark. |
| `vomitFaecal` | `smell`, `danger` | Stool-like smell; this field is marked dangerous. |
| `stoolShape` | `consistency` | Hard, lumpy, smooth, soft, mushy or watery form. |
| `stoolColour` | `colour` | Colour of the stool. |
| `stoolShade` | `shade` | Whether the colour is pale or dark. |
| `stoolBloodWhere` | `blood` | Where blood appears in or around the stool. |
| `stoolBloodPlace` | `blood` | Where blood appears in or around the stool. |
| `stoolSmell` | `smell` | Whether the stool smells unusually bad. |
| `urineColour` | `colour` | Colour of the urine. |
| `urineAmount` | `amount` | How much urine comes each time. |
| `urineSmell` | `smell` | Whether the urine smells strongly or badly. |
| `dischargeAmount` | `amount` | How much fluid comes out. |
| `fluidColour` | `colour` | Colour of the fluid. |
| `fluidShade` | `shade` | Whether the colour is pale or dark. |
| `fluidConsistency` | `consistency` | Watery, thick or pus-like texture. |
| `fluidSmell` | `smell` | Whether the fluid smells unusually bad. |
| `fluidBlood` | `blood` | Whether blood is present in the fluid. |
| `bleedVolume` | `amount`, `danger` | Amount of blood lost; this field is marked dangerous. |
| `bleedColour` | `colour` | Colour of the blood. |

Question IDs from the patch receive the matching dimension tags for what each
question asks, including `q_dryWet` (`cough`) and `q_urineBurn`
(`pain_passing`). Dry/wet cough describes the cough rather than the output texture
used by the standard's `consistency` definition. Vomit amount does not explicitly
ask about drinking, so it is not tagged `fluids_intake`. A talk test does not
establish breathlessness at rest. `q_stoolPain` maps to `pain_passing`. Danger
dimensions are retained for danger-marked questions.

`stoolFloats` and `q_stoolFloats` remain untagged: the standard defines stool
consistency by texture and form, and has no dimension for floating. No dimension
was added merely to increase completeness coverage.
