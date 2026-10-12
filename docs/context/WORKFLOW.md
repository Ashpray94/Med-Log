# Workflow

## Build and test (cloud machine)
```bash
export PATH=/opt/gradle-8.14.3/bin:$PATH
gradle --console=plain -q :app:compileDebugKotlin        # quick check
gradle --console=plain -q :app:testDebugUnitTest         # all tests (must pass before any push)
```
Failures: `app/build/test-results/testDebugUnitTest/*.xml` (search for `<failure`).

## Looking at screens (Robolectric screenshots, no emulator needed)
```bash
SHOTS_TALL=1 bash scripts/shots.sh home,meds,history        # app/build/shots/<name>-tall.png
SHOTS_ROLE=helper bash scripts/shots.sh helper,helperchat    # the helper's phone
SHOTS_WORST=1 ...   # long names, many items, every state      SHOTS_LANG=ta / hi   SHOTS_BIG=1   SHOTS_LONG=1
bash scripts/shots.sh "history@Food"                         # open History, then tap "Food"
```
Routes are listed in `app/src/test/java/com/suryaprakash/medlog/Shots.kt` (`routes`); add one for a new screen.
Read the PNG and look at it before saying a screen is right.

## Line endings and commits
- Many sources are CRLF. Edit with Python (keep `\r\n`) and run `python3 scripts/keep_eol.py` before committing.
- Test-gated commit:
```bash
gradle --console=plain -q :app:testDebugUnitTest > /tmp/t.log 2>&1 && python3 scripts/keep_eol.py >/dev/null \
  && git add -A && git commit -q -m "..." && git push -q origin main-mxp0m4
```
- No model names in commit messages. Never commit keys (`signing/` holds only a README).

## Releasing (the owner updates from inside the app)
### Production data gate — never skip
Before publishing or installing a production APK, protect the current production data. Do **one** of these and verify it:
- In the currently installed production app, use **Settings → Backup and new phone → Make a backup copy**. Copy the resulting `.medlog` file off the phone, confirm that the copied file exists and has a non-zero size, and retain the password.
- Confirm a second physical device already contains all current records by checking recent entries and totals on both devices.

Do not treat `adb install -r`, database migrations, sync, an APK, a setup JSON, or an unverified file as a backup. Never install, downgrade, uninstall, clear app data, restore, or run a production migration before this gate passes. Record the backup filename/location or the verified second device in `docs/context/STATUS.md`.

Production releases must be manual. Run `release.yml` with `publish_prod=true` and `data_safety_confirmation=BACKUP_VERIFIED` only after the gate passes. CI rejects production publication without that exact confirmation. After installation, verify the existing record totals and latest entries before doing any other work. If they differ, stop immediately and preserve both devices/files; do not attempt an unverified repair.

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
2. Rewrite `RELEASE_NOTES.md`: keep the first line and everything from `Install **MedLog-VERSION` on; replace the
   "## What's new" bullets (plain words, what the person will notice).
3. Tests, commit, push.
4. Complete the production data gate above, then run `release.yml` on ref `main-mxp0m4` with `publish_prod=true` and `data_safety_confirmation=BACKUP_VERIFIED`.
5. Wait until `https://github.com/Ashpray94/Med-Log/releases/latest/download/latest.json` shows the new version.
   If an older build is still running, cancel it so it can't become "latest" after the newer one.
Signing uses the `MEDLOG_SIGNING` secret; every release must be signed with that same key or phones can't update.

## What can't be tested here
Alarms and full-screen alerts, notifications and their buttons, SMS and calls, Bluetooth pairing and scales, speech,
widgets on a home screen, the in-app update install, PDF viewers. Say so, and add them to the phone checklist in
`STATUS.md`.
