param(
    [string]$DatabaseHost = '127.0.0.1',
    [int]$DatabasePort = 3306,
    [string]$DatabaseName = 'spa',
    [string]$DatabaseUser = 'root'
)
$ErrorActionPreference = 'Stop'
# Only the currently verified local database is allowed. Credentials via MYSQL_PWD.
if ($DatabaseHost -notin @('localhost','127.0.0.1') -or $DatabasePort -ne 3306 -or $DatabaseName -ne 'spa') {
    throw 'Only the verified local development database 127.0.0.1:3306/spa is allowed.'
}
if (-not $env:MYSQL_PWD) { throw 'Set MYSQL_PWD in the current process before running; do not pass passwords on the command line.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$backupDirectory = Join-Path $projectRoot ('backups/' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ'))
New-Item -ItemType Directory -Path $backupDirectory | Out-Null
$connection = @('--protocol=TCP', '-h', $DatabaseHost, '-P', "$DatabasePort", '-u', $DatabaseUser, '--default-character-set=utf8mb4')
$identity = & mysql @connection --batch --skip-column-names $DatabaseName -e 'SELECT DATABASE(),@@hostname,@@port,@@version,CURRENT_USER()'
if ($LASTEXITCODE -ne 0) { throw 'Database identity check failed.' }
$tables = @(& mysql @connection --batch --skip-column-names $DatabaseName -e 'SHOW TABLES')
if ($LASTEXITCODE -ne 0) { throw 'Table inventory failed.' }
$counts = [ordered]@{}
foreach ($tableName in $tables) {
    if ($tableName -notmatch '^[a-zA-Z0-9_]+$') { throw 'Unexpected table identifier.' }
    $count = & mysql @connection --batch --skip-column-names $DatabaseName -e "SELECT COUNT(*) FROM ``$tableName``"
    if ($LASTEXITCODE -ne 0) { throw 'Row count failed.' }
    $counts[$tableName] = [long]$count
}
$dumpPath = Join-Path $backupDirectory 'spa.sql'
& mysqldump @connection --single-transaction --skip-extended-insert --complete-insert --hex-blob --no-tablespaces --set-gtid-purged=OFF --skip-add-drop-table "--result-file=$dumpPath" $DatabaseName
if ($LASTEXITCODE -ne 0) { throw 'mysqldump failed; backup is not verified.' }
$dumpCounts = @{}
foreach ($line in [IO.File]::ReadLines($dumpPath)) {
    if ($line -match '^INSERT INTO `([^`]+)`') {
        $tableName = $Matches[1]
        $dumpCounts[$tableName] = 1 + $dumpCounts[$tableName]
    }
}
foreach ($tableName in $tables) {
    $afterCount = & mysql @connection --batch --skip-column-names $DatabaseName -e "SELECT COUNT(*) FROM ``$tableName``"
    if ($LASTEXITCODE -ne 0 -or [long]$afterCount -ne $counts[$tableName] -or [long]$dumpCounts[$tableName] -ne $counts[$tableName]) {
        throw "Backup row count mismatch for $tableName; stop migrations."
    }
}
$manifest = [ordered]@{
    identity = $identity
    timestampUtc = [DateTime]::UtcNow.ToString('o')
    sha256 = (Get-FileHash -LiteralPath $dumpPath -Algorithm SHA256).Hash
    counts = $counts
    verification = 'mysqldump exit 0; individual INSERT counts match before/after SELECT counts; restore drill not performed'
}
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $backupDirectory 'manifest.json') -Encoding UTF8
Write-Output "Verified local backup: $backupDirectory"
Write-Output ($manifest | ConvertTo-Json -Depth 5)
