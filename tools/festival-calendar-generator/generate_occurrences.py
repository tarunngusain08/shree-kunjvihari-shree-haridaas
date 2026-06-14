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
from urllib.parse import parse_qs, urlparse


REQUIRED_COLUMNS = {
    "festivalId",
    "year",
    "date",
    "month",
    "paksha",
    "tithi",
    "location",
    "sourceUrl",
    "verified",
    "reviewNotes",
}
APPROVED_LOCATION_VALUES = {"vrindavan", "vrindavan, india"}
APPROVED_SOURCE_HOSTS = {"drikpanchang.com", "www.drikpanchang.com"}
VRINDAVAN_GEONAME_ID = "1253079"


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, help="Reviewed CSV path")
    parser.add_argument("--definitions", required=True, help="festival_definitions.json")
    parser.add_argument("--output", required=True, help="Draft output directory")
    parser.add_argument("--report", help="Optional validation report JSON path")
    parser.add_argument("--expected-start-year", type=int, help="First expected year")
    parser.add_argument("--expected-end-year", type=int, help="Last expected year")
    return parser.parse_args()


def load_festival_definitions(path):
    with open(path, "r", encoding="utf-8") as handle:
        payload = json.load(handle)
    if payload.get("schemaVersion") != 1:
        raise SystemExit("Unsupported festival definition schemaVersion")
    return {item["id"]: item for item in payload.get("festivals", [])}


def require_verified(row, line_number):
    if row["verified"].strip().lower() != "true":
        raise SystemExit(f"Line {line_number}: occurrence is not verified")

    location = row["location"].strip().lower()
    if location not in APPROVED_LOCATION_VALUES:
        raise SystemExit(f"Line {line_number}: non-Vrindavan location: {row['location']}")

    parsed_url = urlparse(row["sourceUrl"].strip())
    if parsed_url.scheme != "https" or parsed_url.netloc not in APPROVED_SOURCE_HOSTS:
        raise SystemExit(f"Line {line_number}: sourceUrl must be an HTTPS Drik Panchang URL")

    geoname_ids = parse_qs(parsed_url.query).get("geoname-id", [])
    if geoname_ids != [VRINDAVAN_GEONAME_ID]:
        raise SystemExit(
            f"Line {line_number}: sourceUrl must include geoname-id={VRINDAVAN_GEONAME_ID}"
        )


def require_definition_match(row, definition, line_number):
    expected = {
        "month": definition.get("month"),
        "paksha": definition.get("paksha"),
        "tithi": str(definition.get("tithi")),
    }
    actual = {
        "month": row["month"].strip(),
        "paksha": row["paksha"].strip(),
        "tithi": row["tithi"].strip(),
    }
    if actual != expected:
        raise SystemExit(
            f"Line {line_number}: tithi metadata mismatch for {row['festivalId']}: "
            f"expected {expected}, got {actual}"
        )


def build_report(definitions, grouped, expected_start_year, expected_end_year, errors):
    years = sorted(grouped)
    if expected_start_year is None:
        expected_start_year = years[0] if years else None
    if expected_end_year is None:
        expected_end_year = years[-1] if years else None

    missing = []
    if expected_start_year is not None and expected_end_year is not None:
        seen = {
            (occurrence["festivalId"], int(occurrence["date"][:4]))
            for occurrences in grouped.values()
            for occurrence in occurrences
        }
        for year in range(expected_start_year, expected_end_year + 1):
            for festival_id in sorted(definitions):
                if (festival_id, year) not in seen:
                    definition = definitions[festival_id]
                    missing.append(
                        {
                            "festivalId": festival_id,
                            "year": year,
                            "month": definition.get("month"),
                            "paksha": definition.get("paksha"),
                            "tithi": definition.get("tithi"),
                        }
                    )

    return {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "source": "Drik Panchang Vrindavan",
        "expectedStartYear": expected_start_year,
        "expectedEndYear": expected_end_year,
        "yearFiles": years,
        "occurrenceCount": sum(len(items) for items in grouped.values()),
        "missingFestivalYears": missing,
        "errors": errors,
    }


def main():
    args = parse_args()
    definitions = load_festival_definitions(args.definitions)
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    grouped = defaultdict(list)
    seen = set()
    errors = []

    with open(args.input, "r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        missing = REQUIRED_COLUMNS - set(reader.fieldnames or [])
        if missing:
            raise SystemExit(f"Missing CSV columns: {sorted(missing)}")

        for line_number, row in enumerate(reader, start=2):
            festival_id = row["festivalId"].strip()
            if festival_id not in definitions:
                raise SystemExit(f"Line {line_number}: unknown festivalId: {festival_id}")

            require_verified(row, line_number)
            require_definition_match(row, definitions[festival_id], line_number)

            occurrence_date = date.fromisoformat(row["date"].strip())
            occurrence_year = int(row["year"].strip())
            if occurrence_year != occurrence_date.year:
                raise SystemExit(
                    f"Line {line_number}: year {occurrence_year} does not match {occurrence_date}"
                )

            key = (festival_id, occurrence_date.isoformat())
            if key in seen:
                raise SystemExit(f"Line {line_number}: duplicate occurrence: {key}")
            seen.add(key)

            grouped[occurrence_date.year].append(
                {
                    "festivalId": festival_id,
                    "date": occurrence_date.isoformat(),
                    "source": "Drik Panchang Vrindavan",
                    "location": "Vrindavan",
                    "sourceUrl": row["sourceUrl"].strip(),
                    "verified": True,
                    "verifiedMonth": row["month"].strip(),
                    "verifiedPaksha": row["paksha"].strip(),
                    "verifiedTithi": int(row["tithi"].strip()),
                    "reviewNotes": row["reviewNotes"].strip(),
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

    if args.report:
        report = build_report(
            definitions=definitions,
            grouped=grouped,
            expected_start_year=args.expected_start_year,
            expected_end_year=args.expected_end_year,
            errors=errors,
        )
        report_path = Path(args.report)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        with open(report_path, "w", encoding="utf-8") as handle:
            json.dump(report, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
        print(f"Wrote {report_path}")


if __name__ == "__main__":
    main()
