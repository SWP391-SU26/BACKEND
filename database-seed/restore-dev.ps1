param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,

    [string]$ServerInstance = "localhost,1433",
    [string]$DatabaseName = "VietnameseCourseQA20DB",
    [string]$Username = "sa",
    [string]$ExpectedSha256 = "14B03099F1C6E072F47AB573EE0830C95E1E8589BC09C646559A59E739C43E82"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command sqlcmd -ErrorAction SilentlyContinue)) {
    throw "sqlcmd is required. Install SQL Server command-line utilities first."
}
if (-not (Test-Path -LiteralPath $BackupFile -PathType Leaf)) {
    throw "Backup file not found: $BackupFile"
}
if ($DatabaseName -notmatch '^[A-Za-z0-9_]+$') {
    throw "DatabaseName may contain only letters, numbers, and underscores."
}
if (-not $env:SQLCMDPASSWORD) {
    throw "Set SQLCMDPASSWORD to the local SQL Server password before running this script."
}

$resolvedBackup = (Resolve-Path -LiteralPath $BackupFile).Path
$actualHash = (Get-FileHash -LiteralPath $resolvedBackup -Algorithm SHA256).Hash
if ($ExpectedSha256 -and $actualHash -ne $ExpectedSha256.ToUpperInvariant()) {
    throw "SHA-256 mismatch. Expected $ExpectedSha256 but found $actualHash."
}

$sqlcmdArgs = @("-S", $ServerInstance, "-U", $Username, "-C", "-b")
$dataPath = (& sqlcmd @sqlcmdArgs -d master -h -1 -W -Q "SET NOCOUNT ON; SELECT CONVERT(nvarchar(4000), SERVERPROPERTY('InstanceDefaultDataPath'));" | Out-String).Trim()
if (-not $dataPath) {
    throw "Could not determine the SQL Server data directory."
}

$escapedBackup = $resolvedBackup.Replace("'", "''")
$escapedDataPath = $dataPath.Replace("'", "''").TrimEnd("\")
$dataFile = "$escapedDataPath\$DatabaseName.mdf"
$logFile = "$escapedDataPath\${DatabaseName}_log.ldf"

$restoreSql = @"
RESTORE VERIFYONLY FROM DISK = N'$escapedBackup' WITH CHECKSUM;
IF DB_ID(N'$DatabaseName') IS NOT NULL
BEGIN
    ALTER DATABASE [$DatabaseName] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE [$DatabaseName];
END;
RESTORE DATABASE [$DatabaseName]
FROM DISK = N'$escapedBackup'
WITH MOVE N'VietnameseCourseQA20DB' TO N'$dataFile',
     MOVE N'VietnameseCourseQA20DB_log' TO N'$logFile',
     RECOVERY,
     CHECKSUM,
     STATS = 10;
ALTER DATABASE [$DatabaseName] SET MULTI_USER;
"@

Write-Host "Restoring $DatabaseName on $ServerInstance..."
& sqlcmd @sqlcmdArgs -d master -Q $restoreSql

$validationSql = @"
SET NOCOUNT ON;
SELECT
    (SELECT COUNT(*) FROM sys.tables) AS tables,
    (SELECT COUNT(*) FROM users) AS users,
    (SELECT COUNT(*) FROM courses) AS courses,
    (SELECT COUNT(*) FROM course_documents) AS documents,
    (SELECT COUNT(*) FROM document_chunks) AS chunks,
    (SELECT COUNT(*) FROM chunk_embeddings) AS embeddings,
    (SELECT COUNT(*) FROM evaluation_questions) AS questions;
"@

Write-Host "Restore complete. Snapshot counts:"
& sqlcmd @sqlcmdArgs -d $DatabaseName -W -s "|" -Q $validationSql
