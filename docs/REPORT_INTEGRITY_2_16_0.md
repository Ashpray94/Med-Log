# Reporting integrity hotfix — 2.16.0

Built from the latest stable shipped source, v2.14.1 (11dc03c), rather than the older default branch or the unfinished system-audit branch.

## Findings and changes

- Stored triage strings were reused across a selected period and could look like a current 24-hour finding. Historical alerts now have dates; legacy numeric frequency strings are marked for review. A separate rolling 24-hour count reports the number of recorded symptom reports.
- Symptom logging includes check-ins, sparse observations and cumulative daily counts. Reports distinguish record counts from recorded daily totals. Explicit improvement follow-ups and zero-count reports are excluded from occurrence totals. Sparse logging no longer creates an improvement claim.
- Latest symptom flags use the latest occurrence, and additional notes retain dates. First logging time is no longer used to infer onset after a medicine change.
- Explicit schedule changes cancel affected unconfirmed slots, including obsolete same-day times, with a synced CANCELLED audit state. Earlier history and confirmed taken/skipped actions remain. Cancellation uses a compare-and-set query so it cannot overwrite a concurrently confirmed dose. Reinstated future schedules can restore cancelled slots.
- Cancelled slots and stopped-plan slots are excluded from adherence denominators. Skip reasons are counted only for skipped doses and cleared on taking. Concurrent take requests consume one pill.
- An inactive medicine-list entry with an exactly matching active name/strength/form/amount is labelled as a previous entry with matching active treatment. No medicine is silently merged, reactivated or marked taken.
- Report input excludes removed, future and out-of-period records and uses the latest copy for each stable identity. Sync applies each batch transactionally and rejects blank identities instead of merging unrelated records.
- Doctor-page data is refreshed on database invalidation. Export reads fresh data. Nutrition is transactionally read, excludes future/cancelled records, and describes logged nutrition rather than claiming complete actual intake.
- Automatic proximity-based duplicate removal is disabled. Exact historical sync-copy reconciliation remains. No fabricated dose confirmations or retrospective patient-history rewriting is performed.

## Upgrade coverage

Database version 6 migrates shipped version 4, incompatible draft version 4, and repair version 5. Fixture tests run Room schema validation and check preservation of profile, symptom, medicine, dose, appointment, stable identity, timestamp and removal state. The encrypted production database continues to use the existing key. No destructive migration fallback is enabled.

## Verification

235 regression tests passed locally (0 failures, 0 errors, 0 skipped), including 15 new reporting/schedule/sync/nutrition tests and 3 database fixture upgrades. Existing stable tests are retained. The only existing assertion updated is the exact rules-version string, from rules-0.2.0-unreviewed to rules-0.2.1-unreviewed; clinical thresholds are unchanged.

Debug installations use com.suryaprakash.medlog.dev and disable the internet relay, allowing isolated testing beside production. Production release remains com.suryaprakash.medlog. CI builds tests plus the minified release with the existing signing secret; publication requires an explicit production input during manual runs.

Screenshots alone cannot prove whether every historical record is correct. Existing uncertain counts, duplicated list entries and unconfirmed doses remain reviewable evidence. Their real-world values cannot be recovered by guessing.
