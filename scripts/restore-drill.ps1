param([Parameter(Mandatory=$true)][string]$BackupDirectory)
$ErrorActionPreference='Stop'
if(-not $env:MYSQL_PWD){throw 'Set MYSQL_PWD in the current process.'}
$projectRoot=Split-Path -Parent $PSScriptRoot
$backupRoot=[IO.Path]::GetFullPath((Join-Path $projectRoot 'backups'))
$source=[IO.Path]::GetFullPath($BackupDirectory)
if(-not $source.StartsWith($backupRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Use a verified backup inside this workspace backups directory.'}
$dump=Join-Path $source 'spa.sql';$manifest=Get-Content -Encoding UTF8 -Raw (Join-Path $source 'manifest.json')|ConvertFrom-Json
if((Get-FileHash -LiteralPath $dump -Algorithm SHA256).Hash -ne $manifest.sha256){throw 'Backup checksum mismatch.'}
$connection=@('--protocol=TCP','-h','127.0.0.1','-P','3306','-u','root','--default-character-set=utf8mb4')
$database='spa_restore_'+[DateTime]::UtcNow.ToString('yyyyMMddHHmmss')
$existing=& mysql @connection --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$database'"
if($LASTEXITCODE -ne 0 -or [int]$existing -ne 0){throw 'Restore database already exists or identity query failed. No reuse or deletion is allowed.'}
$plan=Join-Path $projectRoot "db/verification/$database.sql"
"-- Isolated local restore drill. Current spa database is never the import target.`nCREATE DATABASE ``$database`` CHARACTER SET utf8mb4;" | Set-Content -Encoding UTF8 -LiteralPath $plan
$OutputEncoding=[Text.UTF8Encoding]::new()
Get-Content -Encoding UTF8 -Raw -LiteralPath $plan | & mysql @connection
if($LASTEXITCODE -ne 0){throw 'Failed to create isolated restore schema.'}
Get-Content -Encoding UTF8 -LiteralPath $dump | & mysql @connection $database
if($LASTEXITCODE -ne 0){throw 'Restore failed; preserve the isolated schema for diagnosis.'}
foreach($entry in $manifest.counts.PSObject.Properties){
 if($entry.Name -notmatch '^[a-zA-Z0-9_]+$'){throw 'Invalid backup table identifier.'}
 $count=& mysql @connection --batch --skip-column-names $database -e "SELECT COUNT(*) FROM ``$($entry.Name)``"
 if($LASTEXITCODE -ne 0 -or [long]$count -ne [long]$entry.Value){throw "Restored count mismatch: $($entry.Name)"}
}
$restoredDump=Join-Path $source 'restored.sql'
& mysqldump @connection --single-transaction --skip-extended-insert --complete-insert --hex-blob --no-tablespaces --set-gtid-purged=OFF --skip-add-drop-table "--result-file=$restoredDump" $database
if($LASTEXITCODE -ne 0){throw 'Restored verification export failed.'}
$originalRows=@([IO.File]::ReadLines($dump)|Where-Object {$_ -match '^INSERT INTO'})
$restoredRows=@([IO.File]::ReadLines($restoredDump)|Where-Object {$_ -match '^INSERT INTO'})
if(@(Compare-Object $originalRows $restoredRows).Count){throw 'Restored row contents differ.'}
$report=@{database=$database;backup=$source;tables=@($manifest.counts.PSObject.Properties).Count;rows=$restoredRows.Count;verifiedUtc=[DateTime]::UtcNow.ToString('o');result='PASS: counts and all exported INSERT row contents match; schema retained'}
$report|ConvertTo-Json|Set-Content -Encoding UTF8 (Join-Path $source 'restore-drill.json')
Write-Output ($report|ConvertTo-Json)
