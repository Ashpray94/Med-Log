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

To add it: repository Settings → Secrets and variables → Actions → New repository secret → name `MEDLOG_SIGNING`,
paste the value, Save. Without it, pushes still build and test, but no release is published.

Builds made on the developer's PC keep using the shared Meeting Timer key (see `app/build.gradle.kts`), which
the Meeting Timer bridge needs. A phone can only update between builds signed with the same key.
