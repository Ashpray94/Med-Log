#!/usr/bin/env python3
"""Give every changed text file the line endings it had at BASE (default e84beb8), so edits never churn whole files."""
import subprocess, sys
base = sys.argv[1] if len(sys.argv) > 1 else "e84beb8"
files = subprocess.run(["git", "diff", "--name-only", base, "--", "*.kt", "*.kts", "*.xml", "*.md", "*.py"], capture_output=True, text=True).stdout.split()
for f in files:
    try: old = subprocess.run(["git", "show", f"{base}:{f}"], capture_output=True).stdout
    except Exception: continue
    if not old: continue
    try: cur = open(f, "rb").read()
    except FileNotFoundError: continue
    lf = cur.replace(b"\r\n", b"\n")
    want = lf.replace(b"\n", b"\r\n") if b"\r\n" in old else lf
    if want != cur: open(f, "wb").write(want); print("fixed", f)
