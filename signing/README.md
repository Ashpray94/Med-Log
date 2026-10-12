# Signing

Every MedLog release must be signed with the same key, or phones can't update from one version to the next.
**The key is not in this repository** (it is public). The release workflow reads it from one Actions secret,
`MEDLOG_SIGNING`:

```
storePassword=...
keyAlias=...
keyPassword=...
keystore=<the .jks file in base64, on one line>
```

**It must be the Meeting Timer key** (the one in `../Google calendar timer/android/keystore` on the PC), because the
installed MedLog is signed with it. The quickest way: on the PC, run

```
powershell -ExecutionPolicy Bypass -File scripts\make_signing_secret.ps1
```

which copies the finished value to the clipboard. Then add it: repository Settings → Secrets and variables → Actions → New repository secret → name `MEDLOG_SIGNING`,
paste the value, Save. Without it, pushes still build and test, but no release is published.

Builds made on the developer's PC keep using the shared Meeting Timer key (see `app/build.gradle.kts`), which
the Meeting Timer bridge needs. A phone can only update between builds signed with the same key.

Running the Release workflow by hand (Actions → Release → Run workflow) without the secret now stops with an error,
instead of building an APK with a one-off key that no phone could update to.
