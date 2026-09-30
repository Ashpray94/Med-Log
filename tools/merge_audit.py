#!/usr/bin/env python3
"""
Merge clinical audit patches into catalogue.json.
Usage: python3 tools/merge_audit.py [--check]
"""
import json
import sys
from pathlib import Path

def load_catalogue():
    """Load the main catalogue."""
    path = Path("app/src/main/assets/clinical/catalogue.json")
    with open(path) as f:
        return json.load(f)

def load_patches():
    """Load all patch files from app/src/main/assets/clinical/audit/*.json"""
    audit_dir = Path("app/src/main/assets/clinical/audit")
    patches = []
    if audit_dir.exists():
        for patch_file in sorted(audit_dir.glob("*.json")):
            with open(patch_file) as f:
                patches.append(json.load(f))
    return patches

def merge_patches(cat, patches):
    """Merge all patches into the catalogue."""
    errors = []

    # Build set of valid problem ids
    valid_problems = {p["id"] for p in cat["problems"]}

    for patch in patches:
        # Merge fields
        if "fields" in patch:
            for fid, field in patch["fields"].items():
                cat["fields"][fid] = field

        # Merge questions
        if "questions" in patch:
            for qid, question in patch["questions"].items():
                cat["questions"][qid] = question

        # Merge problems (replace fields and fu lists)
        if "problems" in patch:
            for pid, prob_patch in patch["problems"].items():
                if pid not in valid_problems:
                    errors.append(f"Problem '{pid}' not found in catalogue")
                    continue
                prob = next((p for p in cat["problems"] if p["id"] == pid), None)
                if prob:
                    if "fields" in prob_patch:
                        prob["fields"] = prob_patch["fields"]
                    if "fu" in prob_patch:
                        prob["fu"] = prob_patch["fu"]

        # Append red flags
        if "redFlags" in patch:
            existing_ids = {rf["id"] for rf in cat.get("redFlags", [])}
            for rf in patch["redFlags"]:
                # Replace existing or append
                rf_list = cat.get("redFlags", [])
                existing = next((r for r in rf_list if r["id"] == rf["id"]), None)
                if existing:
                    rf_list.remove(existing)
                rf_list.append(rf)
                if "redFlags" not in cat:
                    cat["redFlags"] = rf_list

    return errors

def validate_catalogue(cat):
    """Validate the catalogue structure."""
    errors = []
    valid_fields = set(cat["fields"].keys())
    valid_questions = set(cat["questions"].keys())

    # For each problem, validate fields and fu
    for prob in cat["problems"]:
        problem_id = prob["id"]
        problem_fields = prob.get("fields", [])

        # Check each field exists
        for field_id in problem_fields:
            if field_id not in valid_fields:
                errors.append(f"Problem '{problem_id}': field '{field_id}' not found in fields")

        # Check each followup question exists
        for fu_id in prob.get("fu", []):
            if fu_id not in valid_questions:
                errors.append(f"Problem '{problem_id}': followup question '{fu_id}' not found in questions")

        # Check question.field is in fields
        for fu_id in prob.get("fu", []):
            q = cat["questions"].get(fu_id)
            if q and q["field"] not in valid_fields:
                errors.append(f"Question '{fu_id}' references unknown field '{q['field']}'")

    # Validate gates
    for qid, q in cat["questions"].items():
        if "gate" in q and q["gate"]:
            gate_field = q["gate"].get("field")
            if gate_field and gate_field not in valid_fields:
                errors.append(f"Question '{qid}' gate references unknown field '{gate_field}'")

    for fid, f in cat["fields"].items():
        if "gate" in f and f["gate"]:
            gate_field = f["gate"].get("field")
            if gate_field and gate_field not in valid_fields:
                errors.append(f"Field '{fid}' gate references unknown field '{gate_field}'")

    # Validate red flags
    for rf in cat.get("redFlags", []):
        for cond in rf.get("all", []):
            cond_field = cond.get("field")
            if cond_field and cond_field not in valid_fields:
                errors.append(f"RedFlag '{rf['id']}' condition references unknown field '{cond_field}'")

    return errors

def main():
    check_mode = "--check" in sys.argv

    try:
        cat = load_catalogue()
    except Exception as e:
        print(f"Error loading catalogue: {e}", file=sys.stderr)
        sys.exit(1)

    try:
        patches = load_patches()
    except Exception as e:
        print(f"Error loading patches: {e}", file=sys.stderr)
        sys.exit(1)

    # Apply patches
    merge_errors = merge_patches(cat, patches)

    # Validate
    val_errors = validate_catalogue(cat)
    all_errors = merge_errors + val_errors

    if all_errors:
        for err in all_errors:
            print(f"ERROR: {err}", file=sys.stderr)
        sys.exit(1)

    # Success
    if patches or not check_mode:
        if not check_mode and patches:
            # Write back
            path = Path("app/src/main/assets/clinical/catalogue.json")
            # Bump version
            parts = cat["version"].split(".")
            if len(parts) >= 3:
                # e.g., "0.1.0" -> "0.1.1"
                patch_ver = int(parts[2])
                parts[2] = str(patch_ver + 1)
                cat["version"] = ".".join(parts)

            with open(path, "w") as f:
                json.dump(cat, f, indent=1, ensure_ascii=False)
                f.write("\n")
            print(f"Merged {len(patches)} patch(es), version now {cat['version']}")
        elif check_mode:
            print("Catalogue is valid")
    else:
        print("No patches to merge, catalogue is valid")

if __name__ == "__main__":
    main()
