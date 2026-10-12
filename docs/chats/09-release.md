# Chat: release a new version

## Paste this
```
Read CLAUDE.md and docs/context/WORKFLOW.md (Releasing) and docs/context/STATUS.md.
Release what's on main-mxp0m4 as <next version>. Before publishing or installing production, complete and document
the production data gate in WORKFLOW.md: a verified full backup copied off the phone, or all current records verified
on a second physical device. Stop if this is not done. Run all tests first. Write RELEASE_NOTES.md from the changes
since the last release, in plain words for the person using the phone. Bump the version, commit, push, run
release.yml with publish_prod=true and data_safety_confirmation=BACKUP_VERIFIED, cancel any older release run still going, and wait until latest.json shows the new version. Then
update STATUS.md (released version, and the phone checks for this release) and tell me what to check.
```
