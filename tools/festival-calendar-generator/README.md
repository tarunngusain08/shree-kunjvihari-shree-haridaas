# Festival Calendar Generator

This dev-only tool prepares reviewed festival occurrence assets for the Android
app. It does not run inside the app and does not add runtime network access.

The intended source is a Drik Panchang Vrindavan date pipeline followed by
manual review. Generated output must be reviewed before it is copied into
`app/src/main/assets/festival_occurrences/`.

Input CSV columns:

```text
festivalId,year,date,month,paksha,tithi,location,sourceUrl,verified,reviewNotes
RADHASHTAMI,2026,2026-09-19,BHADRAPAD,SHUKLA,8,Vrindavan,https://www.drikpanchang.com/panchang/month-panchang.html?geoname-id=1253079&date=19/09/2026,true,Drik Panchang Vrindavan daily Panchang verified.
```

Google snippets must only be used for discovery. Bundled rows must be verified
against Drik Panchang for Vrindavan (`geoname-id=1253079`) and must match the
festival definition month, paksha, and tithi.

Usage:

```bash
python3 tools/festival-calendar-generator/generate_occurrences.py \
  --input reviewed-occurrences.csv \
  --definitions app/src/main/assets/festival_definitions.json \
  --output build/festival-occurrence-draft \
  --report build/festival-occurrence-report.json \
  --expected-start-year 2026 \
  --expected-end-year 2031
```

The script writes schema-versioned `YYYY.json` files. Review the generated files
before moving them into app assets.
