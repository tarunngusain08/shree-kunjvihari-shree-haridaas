#!/usr/bin/env python3
"""Generate reviewed year-wise festival occurrence JSON assets.

This script intentionally has no network behavior. Feed it a reviewed CSV
export from the Drik Panchang Vrindavan source pipeline, inspect the generated
JSON, then copy approved files into app/src/main/assets/festival_occurrences/.
"""

import argparse
import csv
import json
from collections import defaultdict
from datetime import date, datetime, timezone
from pathlib import Path


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, help="Reviewed CSV path")
    parser.add_argument("--definitions", required=True, help="festival_definitions.json")
    parser.add_argument("--output", required=True, help="Draft output directory")
    return parser.parse_args()


def load_festival_ids(path):
    with open(path, "r", encoding="utf-8") as handle:
        payload = json.load(handle)
    if payload.get("schemaVersion") != 1:
        raise SystemExit("Unsupported festival definition schemaVersion")
    return {item["id"] for item in payload.get("festivals", [])}


def main():
    args = parse_args()
    known_ids = load_festival_ids(args.definitions)
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    grouped = defaultdict(list)
    seen = set()

    with open(args.input, "r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        required = {"festivalId", "date", "source"}
        missing = required - set(reader.fieldnames or [])
        if missing:
            raise SystemExit(f"Missing CSV columns: {sorted(missing)}")

        for row in reader:
            festival_id = row["festivalId"].strip()
            if festival_id not in known_ids:
                raise SystemExit(f"Unknown festivalId: {festival_id}")

            occurrence_date = date.fromisoformat(row["date"].strip())
            key = (festival_id, occurrence_date.isoformat())
            if key in seen:
                raise SystemExit(f"Duplicate occurrence: {key}")
            seen.add(key)

            grouped[occurrence_date.year].append(
                {
                    "festivalId": festival_id,
                    "date": occurrence_date.isoformat(),
                    "source": row["source"].strip(),
                }
            )

    generated_at = datetime.now(timezone.utc).isoformat()
    for year, occurrences in sorted(grouped.items()):
        payload = {
            "schemaVersion": 1,
            "generatedAt": generated_at,
            "source": "Drik Panchang Vrindavan",
            "occurrences": sorted(occurrences, key=lambda item: item["date"]),
        }
        output_path = output_dir / f"{year}.json"
        with open(output_path, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
        print(f"Wrote {output_path}")


if __name__ == "__main__":
    main()
