# Festival Calendar Generator

This dev-only tool prepares reviewed festival occurrence assets for the Android
app. It does not run inside the app and does not add runtime network access.

The intended source is a Drik Panchang Vrindavan date pipeline followed by
manual review. Generated output must be reviewed before it is copied into
`app/src/main/assets/festival_occurrences/`.

Input CSV columns:

```text
festivalId,date,source
RADHASHTAMI,2026-09-20,Drik Panchang Vrindavan
```

Usage:

```bash
python3 tools/festival-calendar-generator/generate_occurrences.py \
  --input reviewed-occurrences.csv \
  --definitions app/src/main/assets/festival_definitions.json \
  --output build/festival-occurrence-draft
```

The script writes schema-versioned `YYYY.json` files. Review the generated files
before moving them into app assets.
