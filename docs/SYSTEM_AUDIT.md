# System audit — 2026-09-30

Status: source integration in progress. See the continuation below for current changes and checks. Initial findings are retained as baseline evidence.

## Verified baseline

- PR #3 head: 4a9671999407ddfe61bb0d03bb8cfcb925cf0ea8. GitHub Actions run 36713334018 passed test/build steps and produced dev/prod artifacts. This does not verify devices or database upgrades.
- Owner identified 11dc03c. GitHub tag v2.14.1 resolves to that commit; Gradle declares versionName 2.14.1 and versionCode 2141. Source is present in this repository, contrary to the handoff assumption.
- v2.14.1 is not an ancestor of PR #3. A merge probe found 35 conflicted paths; probe was aborted without retaining changes.
- Open original checkout has pre-existing edits; all investigation uses D:/Code/Mockup Pro/MedLog-audit, branch codex/medlog-system-audit.

## Blocking compatibility and regression findings

1. **Two incompatible version-4 Room schemas.** v2.14.1 has uid/updatedAt columns and indices for notes/medicines/doses, and profile.updatedAt. PR #3 removes these and adds sync_meta while still declaring database version 4. An upgrade can fail Room identity validation. Use a new schema version and explicit, tested migrations for both existing version-4 layouts; never destructively recreate patient data.
2. **Existing sync safety is replaced.** v2.14.1 includes per-patient mirror databases, stable medicine references, persisted sent/received checkpoints, catch-up requests, grouped-note UIDs, duplicate reconciliation and Bluetooth fallback. PR #3 replaces it with a different protocol. The handoff sync TODOs cannot be implemented responsibly without reconciling the shipped protocol and paired helper phones.
3. **Existing safety and clinical features disappear.** Compared with v2.14.1, PR #3 lacks TrustedLinks, Occurrences, DoseOutcome, Limits/LimitsForm, Told, AlertReplies, AlertSnooze, and the established feedback implementation. Restore behavior and regression coverage before release; apply Rule 0 to any patient-visible restored behavior.
4. **Regression coverage is lost.** PR #3 lacks DataIntegrityTest, ClinicalRulesTest, SafetyFixesTest, LinkPolicyTest, SmsOnlyForSosTest, and both established patient-persona suites. Restore applicable tests; passing current tests alone cannot establish parity.
5. **Draft builds publish production updates.** release.yml workflow_dispatch can create a release and make_latest:true for any selected branch if signing is available. Latest successful draft run published v2.15.0. Gate publication to approved production branches and keep audit builds artifact-only before dispatching another build.

## Confirmed PR #3 sync defects

- Sync.kt:360-373 sends device-local medicineId; incoming medicines receive a new local ID at 223-224. Doses can map to the wrong medicine.
- Sync.kt:28,130-133,273-282 stores failed events in a 100-item memory queue and drops oldest events. Restart loses changes and tombstones.
- Sync.kt:137-138 discards received events while applyingRemote is true. Concurrent delivery can permanently lose records; serialize receipt rather than dropping.
- Repo.kt:167-169 restores a note with an update, but Sync.kt:179-193 leaves the recipient deletedAt unchanged. Remote note stays hidden.
- Sync.kt:80-82,106-110 requires an appointment still exist before publishing delete; publishing after physical deletion cannot send its tombstone. Stage deletion before removal.
- Sync.kt:288-300 omits note group links. Use group UID rather than local groupId.
- Sync.kt:316-358 omits photoPath/calendarEventId; applying replacement at 220 clears receiver-local photo/calendar metadata. Preserve local fields.
- Sync.kt only uses Relay.post; Nearby.kt:79-103 has no sync payload dispatch.

## Clinical and patient usability status

- Re-ran tools/lint_completeness.py: 145 problems, 144 with gaps, 861 missing dimensions, 36 waivers. Exit code 1. Three redundant-waiver warnings. Counts include missing dimension tags; they are not a count of missing questions alone.
- CI deliberately turns completeness failure into a warning. Keep the required zero-gap goal; do not add loose tags, meaningless questions or waivers to force it green.
- Source inspection and handoff identify missing pictorial choices, largest-font checks and clinical engine expressiveness. No device/TalkBack/font-scale validation was performed.
- Clinical content remains AI-assisted and not clinician-reviewed. No thresholds, codes, triage rules or catalogue were changed by this audit.

## Proposed integration sequence

Preserve 11dc03c data/sync/safety foundation, retain current CLAUDE.md Rule 0 and clinical constraints, and integrate PR #3 additions in small testable batches. This changes the handoff assumption that its replacement sync engine is the foundation; owner approval requested as required by HANDOFF.md.

1. Prevent audit builds from publishing production updates.
2. Integrate source and restore regression tests; establish versioned migration compatibility for both version-4 schemas.
3. Build/tests through Actions, with artifacts only.
4. Test actual sync failure cases against the integrated protocol, including two databases, restart, out-of-order events, pairing and offline catch-up.
5. Tag genuinely covered clinical dimensions; fill remaining content through audit patches with cited guidance, preserving optional detail and core cap.
6. Verify dev patient screens with largest text, pictorial answers and speech; retain clinician sign-off as pending.

No production release or installation was requested or performed in this audit. No merge is claimed complete.

## Continuation: implemented and verified

The owner asked us to choose the sensible integration. Preserve the shipped 2.14.1 foundation while retaining Rule 0 and draft clinical additions. History shows divergent development from 2.9.0; no evidence that removal was intentional.

Completed commits on codex/medlog-system-audit:
- f1fb9a3: draft builds cannot publish production updates.
- caa0acc: conditional choice questions retain their choices, explanation and gate.
- f5d1878: database version 5 migrates both incompatible version-4 layouts without destructive reset. Tests construct both exported schemas and preserve records, identifiers and tombstones.
- 54a640c: explicit impaired consciousness/unsafe swallowing suppresses oral-sugar first aid. Content remains unreviewed.
- 6fa4f9b: accurate existing dimension tags; no fabricated clinical coverage. Missing dimensions reduced from 861 to 819; still a release blocker.

GitHub Actions 36716244036 passed: 93 tests, zero failures/errors/skips. Schema and XML evidence downloaded outside the checkout. No device, largest-font, TalkBack or two-phone test has occurred.

In progress (not yet verified): restore paired sync, per-patient mirror databases, stable medicine references, durable row/checkpoint catch-up, connectivity retries and nearby dispatch from 2.14.1. The draft incorrectly used helper-only family chat for health records. Timestamp batch boundaries need regression coverage before claiming complete catch-up.

Still required: finish source parity and lost safety suites; connect helper screens to explicitly selected patient mirrors; restore functional, isolated live chat; appointments/tombstones and remote change notifications; bidirectional nearby checks; remaining clinical patches; all patient screens at largest text size. The old initial-status and line references above describe the original draft, not current code.

Keep the real phone on 2.14.1. Test dev first. Settings → Backup before any real-app upgrade. No production installation or completed source merge is claimed.

UI continuation: `29903e1` isolates simple patient help, connects helper selection to mirror timeline reads, prevents mirror editing through screens that still target the phone's own DB, repairs chat date indexing and functional send controls. This is an interim read-only mirror view; full helper CRUD and live chat remain unfinished. Actions run `36720071073` pending. Review `docs/DEV_ACCEPTANCE.md` before device testing.
