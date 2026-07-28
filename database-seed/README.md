# SQL Server development seed

This package gives every developer the same starting snapshot of
`VietnameseCourseQA20DB`. It is not real-time synchronization: data starts to
diverge after each developer restores the backup locally.

## Snapshot

- File: `VietnameseCourseQA20DB_seed_20260718_v1.bak`
- SQL Server: 2022 (compatibility level 160)
- SHA-256: `14B03099F1C6E072F47AB573EE0830C95E1E8589BC09C646559A59E739C43E82`
- Size: 5,128,192 bytes
- Distribution: private team storage; the `.bak` file is intentionally ignored by Git

The backup contains all current users, courses, Cloudinary documents, chunks,
embeddings, chat history, test sets, and benchmark results. Treat it as private
team data and do not publish a public download link.

## Restore with the script

Install SQL Server 2022, SSMS, and `sqlcmd`. Download the backup to any local
folder, stop Spring Boot, then run PowerShell:

```powershell
$env:SQLCMDPASSWORD = "<your-local-sa-password>"
./database-seed/restore-dev.ps1 `
  -BackupFile "C:\Downloads\VietnameseCourseQA20DB_seed_20260718_v1.bak"
Remove-Item Env:SQLCMDPASSWORD
```

The script verifies SHA-256, discovers the destination instance's DATA folder,
replaces the existing `VietnameseCourseQA20DB`, and prints the restored counts.
Use `-ServerInstance ".\SQLEXPRESS"` when the local instance is SQL Express.

Warning: restoring replaces the developer's existing database of the same name.

## Restore with SSMS

1. Open `Databases > Restore Database > Device` and select the `.bak` file.
2. Under `Files`, enable `Relocate all files to folder`.
3. Under `Options`, enable `Close existing connections` and, when replacing an
   existing local database, `Overwrite the existing database`.
4. Restore as `VietnameseCourseQA20DB`.

Expected minimum counts are recorded in `seed-manifest.json`: 26 tables, 10
users, 7 documents, 532 chunks, 280 embeddings, and 552 evaluation questions.

## Spring Boot local connection

Each developer keeps their own SQL Server password in an untracked `.env`:

```properties
SPRING_DATASOURCE_URL=jdbc:sqlserver://localhost:1433;databaseName=VietnameseCourseQA20DB;encrypt=true;trustServerCertificate=true
SPRING_DATASOURCE_USERNAME=sa
SPRING_DATASOURCE_PASSWORD=<your-local-sa-password>
```

For SQL Express, update the JDBC host/instance to match the local setup.

## Updating the shared snapshot

- Never overwrite an existing snapshot; publish `v2`, `v3`, and so on.
- Commit schema changes as migrations, not as binary backups.
- Keep reusable demo rows in idempotent seed SQL.
- Do not merge one developer's temporary chat or test data into the shared seed.
- A new `.bak` is required when the team intentionally wants a new common data
  baseline.
