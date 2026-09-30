# Build Environments: Dev and Prod

MedLog has two product flavors for parallel installation and testing.

## Comparison

| Aspect | Dev | Prod |
|--------|-----|------|
| Application ID | `com.suryaprakash.medlog.dev` | `com.suryaprakash.medlog` |
| Display Name | MedLog Dev | MedLog |
| Updater | Signed `dev-latest` prerelease channel | Production latest release |
| Relay (internet messages) | Disabled | Enabled |
| Signing Key | Same as prod | Shared with Meeting Timer |
| Data Isolation | Separate database | Separate database |

## Installation

**Dev build (emulator or test device):**
```
gradle :app:installDevDebug
```
Targets x86_64 ABI for emulators; can run alongside prod.

The first updater-enabled dev APK must be installed manually once. After that, Settings → Check for updates reads `latest-dev.json` from the `dev-latest` GitHub prerelease, verifies its SHA-256 and signing certificate, and asks Android to install it. Only signed manual builds from `codex/medlog-system-audit` publish this channel. Dev updates never replace or mark the production release as latest.

**Prod build (release, CI/CD):**
```
gradle :app:assembleProdRelease
```
GitHub Actions builds this on every push to main; produces universal APK splits (arm64-v8a, armeabi-v7a).

## Known Limitations

**Meeting Timer bridge and Calendar integration:** The Meeting Timer app identifies MedLog by package name. Dev (`com.suryaprakash.medlog.dev`) does not talk to the Meeting Timer app or sync calendar events. Only prod (`com.suryaprakash.medlog`) can bridge.

Both flavors share the same signing key and can coexist on the same device without data loss.
