param([string]$Archive='target/spa-agent-0.0.1-SNAPSHOT.jar')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$artifact=(Resolve-Path -LiteralPath $Archive).Path
$zip=[IO.Compression.ZipFile]::OpenRead($artifact)
try {
    $entries=@($zip.Entries | ForEach-Object {$_.FullName})
    $unexpected=@($entries | Where-Object {$_ -match '(?i)^BOOT-INF/classes/(?:.*/)?(?:application[^/]*\.(?:ya?ml|properties)|\.env[^/]*|[^/]+\.(?:pem|key))$'})
    if($unexpected.Count){throw ('Local configuration or key files found in release archive: '+($unexpected -join ', '))}
    foreach($required in @('BOOT-INF/classes/com/lee/spa/SpaAgentApplication.class','BOOT-INF/classes/com/lee/spa/commerce/FinanceLedgerController.class','BOOT-INF/classes/static/admin/index.html','BOOT-INF/classes/static/admin/analytics.js','BOOT-INF/classes/static/admin/finance.js','BOOT-INF/classes/static/admin/order-records.js','BOOT-INF/classes/com/lee/spa/commerce/AdminOrderQueryController.class')) {
        if($required -notin $entries){throw "Missing runtime file: $required"}
    }
} finally {$zip.Dispose()}
$report=[ordered]@{archive=$artifact;sha256=(Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash;entries=$entries.Count;localConfigurationExcluded=$true;runtimeAssetsPresent=$true;deployed=$false}
$report | ConvertTo-Json | Set-Content -LiteralPath 'target/release-archive-verification.json' -Encoding UTF8
$report | ConvertTo-Json
