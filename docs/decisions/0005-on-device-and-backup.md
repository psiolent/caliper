# 0005 — On-device only; Auto Backup for safety

**Decision.** No server. Data lives in a local Room database. Backup is Android's
Auto Backup (to the user's Google Drive) plus a manual JSON export/import.

**Why.** A server is exactly the thing the user did not want to run. Auto Backup
is automatic, uses the Google account already on the phone, and needs no code or
OAuth — it covers the database and shared preferences. The manual export is a
safety net the user controls.

**Consequence.** Keep the database small. Meal photos are never persisted — they
exist only for the turn that reads them — so the 25 MB Auto Backup cap is never
approached. The manual export serializes every entity as JSON.

The settings file (holding the OpenRouter and USDA keys) is excluded from cloud
backup and device transfer via `data_extraction_rules.xml` and `backup_rules.xml`,
so keys never ride along in a Google Drive backup. Keys are also read back with
all whitespace stripped, because a pasted newline in an HTTP header throws.
