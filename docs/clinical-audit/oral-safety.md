# Oral sugar safety guard

`DangerRules` now suppresses its oral sugar first-aid instruction when the sugar reading would select `SUGAR_AID` and a recorded fact indicates difficulty waking, loss of response, loss of consciousness, or unsafe swallowing (`hardToWake`, `lostResponse`, `lostConsciousness`, `cantSwallowWater`, or `swallowWater = false`). In these cases the result is RED, `firstAid` is null, and the existing generic emergency message is shown. The guard does not treat an unknown swallowing answer as evidence that swallowing is safe, and it does not lower the triage level.

The field `lostConsciousness` asks whether the person passed out but does not record when. Until timing is available, any recorded positive answer is treated as unsafe for oral sugar, even if it may refer to a past event. The guard only changes results where oral sugar was selected.

This safety change follows NHS guidance that a drowsy or unconscious person should not be given food or drink by mouth because of choking risk:

- [NHS: Low blood sugar (hypoglycaemia)](https://www.nhs.uk/conditions/low-blood-sugar-hypoglycaemia/)
- [NHS inform: Hypoglycaemia (low blood sugar)](https://www.nhsinform.scot/illnesses-and-conditions/blood-and-lymph/hypoglycaemia-low-blood-sugar/)

Rule version: `rules-0.3.1-unreviewed` (bumped from `rules-0.3.0-unreviewed`). This audit note and rule are AI-assisted and have not been reviewed by a licensed clinician.
