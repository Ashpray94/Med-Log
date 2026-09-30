#!/usr/bin/env python3
"""
Lint script to verify clinical catalogue completeness.
Checks that all required dimensions for each problem are covered by fields, questions, or waivers.
"""

import json
import sys
from pathlib import Path


def load_json(path):
    """Load and return JSON from file, or return None if file doesn't exist."""
    if not path.exists():
        return None
    try:
        with open(path, 'r') as f:
            return json.load(f)
    except (json.JSONDecodeError, IOError) as e:
        print(f"Error reading {path}: {e}", file=sys.stderr)
        return None


def main():
    # Determine base path
    script_dir = Path(__file__).resolve().parent
    repo_root = script_dir.parent
    assets_dir = repo_root / "app" / "src" / "main" / "assets" / "clinical"

    # Load JSON files
    catalogue = load_json(assets_dir / "catalogue.json")
    standard = load_json(assets_dir / "standard.json")
    archetypes = load_json(assets_dir / "archetypes.json")
    dims_mapping = load_json(assets_dir / "dims.json")

    # Check for missing files
    missing_files = []
    if catalogue is None:
        missing_files.append("catalogue.json")
    if standard is None:
        missing_files.append("standard.json")
    if archetypes is None:
        missing_files.append("archetypes.json")
    if dims_mapping is None:
        missing_files.append("dims.json")

    if missing_files:
        print(f"Missing required files: {', '.join(missing_files)}", file=sys.stderr)
        return 2

    # Parse command-line arguments
    json_output = "--json" in sys.argv
    show_waivers = "--waivers" in sys.argv

    # Extract data from loaded JSON
    problems = {p["id"]: p for p in catalogue.get("problems", [])}
    fields = catalogue.get("fields", {})
    questions = catalogue.get("questions", {})
    red_flags = catalogue.get("redFlags", [])

    standard_dims = set(standard.get("universal", []))
    archetype_defs = standard.get("archetypes", {})
    all_dimensions = standard.get("dimensions", {})

    archetype_problems = archetypes.get("problems", {})
    waived_dims = archetypes.get("waive", {})

    fields_dims = dims_mapping.get("fields", {})
    questions_dims = dims_mapping.get("questions", {})

    # Validation and computation
    problems_checked = 0
    problems_with_gaps = 0
    total_missing_dims = 0
    waivers_count = 0
    gaps_by_problem = {}
    errors = []
    warnings = []

    for problem_id, problem_data in problems.items():
        problems_checked += 1

        # Compute required dimensions
        required = set(standard_dims)

        # Get archetypes for this problem
        archetypes_list = archetype_problems.get(problem_id, [])

        if not archetypes_list:
            errors.append(f"Problem '{problem_id}' has no archetypes")
            continue

        for archetype_name in archetypes_list:
            if archetype_name not in archetype_defs:
                errors.append(f"Unknown archetype '{archetype_name}' for problem '{problem_id}'")
                continue
            archetype_dims = archetype_defs[archetype_name].get("dimensions", [])
            required.update(archetype_dims)

        # Compute covered dimensions
        covered = set(standard.get("implicit", []))  # asked of every problem by the fixed questions (Interview.kt)

        # Add dims from fields in problem.fields
        for field_id in problem_data.get("fields", []):
            covered.update(fields_dims.get(field_id, []))

        # Add dims from follow-up questions and their associated fields
        for question_id in problem_data.get("fu", []):
            covered.update(questions_dims.get(question_id, []))
            if question_id in questions:
                field_id = questions[question_id].get("field")
                if field_id:
                    covered.update(fields_dims.get(field_id, []))

        # Add "danger" if any red flag applies
        for red_flag in red_flags:
            if problem_id in red_flag.get("problems", []):
                covered.add("danger")
                break

        # Apply waivers
        problem_waivers = waived_dims.get(problem_id, {})
        waived = set()

        for dim, reason in problem_waivers.items():
            if not reason or not reason.strip():
                errors.append(f"Waiver for problem '{problem_id}' dimension '{dim}' has empty reason")
            else:
                waived.add(dim)
                waivers_count += 1

        # Check for waivers for dims that are already covered
        for dim in waived:
            if dim in covered:
                warnings.append(f"Problem '{problem_id}': dimension '{dim}' is waived but already covered")

        # Compute missing
        missing = required - covered - waived

        if missing:
            problems_with_gaps += 1
            gaps_by_problem[problem_id] = {
                "archetypes": archetypes_list,
                "missing": sorted(missing)
            }
            total_missing_dims += len(missing)

    # Print errors and warnings
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    for warning in warnings:
        print(f"WARNING: {warning}", file=sys.stderr)

    # Print waivers if requested
    if show_waivers:
        for problem_id in sorted(waived_dims.keys()):
            for dim, reason in sorted(waived_dims[problem_id].items()):
                print(f"waiver {problem_id} {dim}: {reason}")

    # Print gaps or JSON output
    if json_output:
        output_dict = {pid: info["missing"] for pid, info in gaps_by_problem.items()}
        print(json.dumps(output_dict))
    else:
        for problem_id in sorted(gaps_by_problem.keys()):
            info = gaps_by_problem[problem_id]
            missing_str = ", ".join(info["missing"])
            archetypes_str = ", ".join(info["archetypes"])
            print(f"{problem_id} [{archetypes_str}]: missing {missing_str}")

    # Print summary
    if not json_output:
        print(f"Problems checked: {problems_checked}, with gaps: {problems_with_gaps}, total missing dims: {total_missing_dims}, waivers: {waivers_count}")

    # Determine exit code
    if errors or problems_with_gaps:
        if not json_output:
            pass  # Already printed gaps
        return 1
    else:
        if not json_output:
            print("Completeness OK")
        return 0


if __name__ == "__main__":
    sys.exit(main())
