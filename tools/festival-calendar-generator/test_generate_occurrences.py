import csv
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("generate_occurrences.py")


class GenerateOccurrencesTest(unittest.TestCase):
    def test_valid_reviewed_csv_generates_year_file_and_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            definitions = self.write_definitions(tmp_path)
            reviewed_csv = self.write_csv(tmp_path, [self.valid_row()])
            output = tmp_path / "out"
            report = tmp_path / "report.json"

            result = self.run_generator(definitions, reviewed_csv, output, report)

            self.assertEqual(0, result.returncode, result.stderr)
            payload = json.loads((output / "2026.json").read_text(encoding="utf-8"))
            occurrence = payload["occurrences"][0]
            self.assertEqual("RADHASHTAMI", occurrence["festivalId"])
            self.assertEqual("2026-09-19", occurrence["date"])
            self.assertTrue(occurrence["verified"])
            self.assertEqual("BHADRAPAD", occurrence["verifiedMonth"])

            report_payload = json.loads(report.read_text(encoding="utf-8"))
            self.assertEqual(1, report_payload["occurrenceCount"])

    def test_rejects_unverified_rows(self):
        row = self.valid_row()
        row["verified"] = "false"

        result = self.run_with_single_row(row)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("not verified", result.stderr)

    def test_rejects_non_vrindavan_rows(self):
        row = self.valid_row()
        row["location"] = "New Delhi"

        result = self.run_with_single_row(row)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("non-Vrindavan", result.stderr)

    def test_rejects_tithi_metadata_mismatch(self):
        row = self.valid_row()
        row["month"] = "ASHADH"

        result = self.run_with_single_row(row)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("tithi metadata mismatch", result.stderr)

    def test_rejects_duplicates(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            definitions = self.write_definitions(tmp_path)
            reviewed_csv = self.write_csv(tmp_path, [self.valid_row(), self.valid_row()])

            result = self.run_generator(
                definitions = definitions,
                reviewed_csv = reviewed_csv,
                output = tmp_path / "out",
                report = tmp_path / "report.json",
            )

            self.assertNotEqual(0, result.returncode)
            self.assertIn("duplicate occurrence", result.stderr)

    def run_with_single_row(self, row):
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            definitions = self.write_definitions(tmp_path)
            reviewed_csv = self.write_csv(tmp_path, [row])
            return self.run_generator(
                definitions = definitions,
                reviewed_csv = reviewed_csv,
                output = tmp_path / "out",
                report = tmp_path / "report.json",
            )

    def run_generator(self, definitions, reviewed_csv, output, report):
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--input",
                str(reviewed_csv),
                "--definitions",
                str(definitions),
                "--output",
                str(output),
                "--report",
                str(report),
                "--expected-start-year",
                "2026",
                "--expected-end-year",
                "2026",
            ],
            capture_output = True,
            text = True,
            check = False,
        )

    def write_definitions(self, tmp_path):
        path = tmp_path / "festival_definitions.json"
        path.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "festivals": [
                        {
                            "id": "RADHASHTAMI",
                            "name": "श्रीराधाष्टमी महोत्सव",
                            "month": "BHADRAPAD",
                            "paksha": "SHUKLA",
                            "tithi": 8,
                            "specialType": None,
                        }
                    ],
                },
                ensure_ascii = False,
            ),
            encoding = "utf-8",
        )
        return path

    def write_csv(self, tmp_path, rows):
        path = tmp_path / "reviewed.csv"
        with path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(
                handle,
                fieldnames=[
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
                ],
            )
            writer.writeheader()
            writer.writerows(rows)
        return path

    def valid_row(self):
        return {
            "festivalId": "RADHASHTAMI",
            "year": "2026",
            "date": "2026-09-19",
            "month": "BHADRAPAD",
            "paksha": "SHUKLA",
            "tithi": "8",
            "location": "Vrindavan",
            "sourceUrl": (
                "https://www.drikpanchang.com/panchang/month-panchang.html"
                "?geoname-id=1253079&date=19/09/2026"
            ),
            "verified": "true",
            "reviewNotes": "Drik Panchang Vrindavan daily Panchang verified.",
        }


if __name__ == "__main__":
    unittest.main()
