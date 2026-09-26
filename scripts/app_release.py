#!/usr/bin/env python3
"""Puts a built release into a checkout of the public medlog-app repository, for the in-app updater.

    python3 scripts/app_release.py <folder with MedLog-<v>-arm64.apk / -armv7.apk> <version name> <version code> <output folder> [notes file] [--release]

Writes latest.json (sizes, SHA-256, download links) into the output folder. Without --release it also copies
the APKs there as MedLog-arm64.apk / MedLog-armv7.apk, for the Med-Log "downloads" branch. With --release the
links point at the GitHub release assets for that version instead. The app checks the SHA-256 and that the
file is signed with the same key as the installed MedLog before installing.
"""
import hashlib, json, os, shutil, sys

args = [a for a in sys.argv[1:] if a != "--release"]
release = "--release" in sys.argv
src, name, code, repo = args[0], args[1], int(args[2]), args[3]
notes = open(args[4], encoding="utf-8").read().strip() if len(args) > 4 else ""
base = f"https://github.com/Ashpray94/Med-Log/releases/download/v{name}/" if release else "https://raw.githubusercontent.com/Ashpray94/Med-Log/downloads/"
apk = {}
for abi, tag in (("arm64-v8a", "arm64"), ("armeabi-v7a", "armv7")):
    f = os.path.join(src, f"MedLog-{name}-{tag}.apk")
    if not os.path.exists(f):
        continue
    out = f"MedLog-{name}-{tag}.apk" if release else f"MedLog-{tag}.apk"
    if not release:
        shutil.copyfile(f, os.path.join(repo, out))
    h = hashlib.sha256(open(f, "rb").read()).hexdigest()
    apk[abi] = {"url": base + out, "sha256": h, "size": os.path.getsize(f)}
if not apk:
    sys.exit("no APKs found")
json.dump({"versionName": name, "versionCode": code, "notes": notes, "apk": apk}, open(os.path.join(repo, "latest.json"), "w"), indent=2)
if not release: open(os.path.join(repo, "README.md"), "w").write(
    f"# MedLog\n\nApp downloads only. Latest: **{name}**.\n\n"
    "- [MedLog-arm64.apk](MedLog-arm64.apk): almost every phone from 2017 on\n"
    "- [MedLog-armv7.apk](MedLog-armv7.apk): older or low-cost phones\n\n"
    "Once installed, MedLog updates itself from here: Settings → Check for updates.\n")
print("ready:", name, ", ".join(apk))
