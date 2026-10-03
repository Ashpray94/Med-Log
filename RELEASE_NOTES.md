MedLog VERSION fixes doctor-report integrity and superseded medicine reminders. Install over the existing app to preserve records; do not uninstall.

## What's new
- **Accurate report wording.** Symptom reports and recorded daily counts are distinguished. Each symptom shows reports recorded in the last 24 hours. Improvement check-ins and zero-count reports do not add episodes.
- **Historical alerts are dated.** Old stored frequency alerts no longer appear as a current numeric finding. Their original counts are marked for review.
- **Medicine schedules.** Unconfirmed reminders from an explicitly replaced schedule are cancelled with an audit trail. Confirmed taken and skipped doses remain intact. Cancelled reminders and stopped-plan slots do not reduce adherence percentages.
- **Current treatment is clear.** Previous duplicate medicine-list entries identify a matching currently active medicine, instead of suggesting the treatment stopped.
- **Evidence-based summaries.** Logging frequency no longer implies improvement. The app no longer treats first logging time as symptom onset after a medicine change. Additional notes carry their recording dates.
- **Fresh reports.** The doctor page updates when records change; sharing reads a fresh database snapshot. Removed, future and out-of-period records are excluded.
- **Data preserved on upgrade.** Database upgrades support shipped v4, draft v4 and repair v5 databases without deleting records. Automatic heuristic duplicate removal is disabled.

Existing uncertain or incorrectly entered records still need review. This update does not invent taken confirmations or rewrite the patient's history.

Install **MedLog-VERSION-arm64.apk** on most phones; use the armv7 APK for older phones.
