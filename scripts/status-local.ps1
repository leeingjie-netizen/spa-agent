param([int[]]$Ports=@(8080,18080))
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'local-runtime.ps1')
$results=@()
foreach($serverPort in $Ports){
    $owners=@(Get-SpaListener $serverPort)
    if(!$owners.Count){$results += [pscustomobject]@{port=$serverPort;listening=$false};continue}
    foreach($ownerId in $owners){
        $runtimeProcess=Get-SpaProcessInfo $ownerId
        $results += [pscustomobject]@{port=$serverPort;listening=$true;javaPid=$ownerId;createdUtc=$runtimeProcess.CreationDate.ToUniversalTime().ToString('o');projectProcess=(Test-SpaProjectProcess $runtimeProcess $projectRoot);executable=$runtimeProcess.ExecutablePath;parentPid=$runtimeProcess.ParentProcessId}
    }
}
[pscustomobject]@{projectRoot=$projectRoot;checkedAt=(Get-Date -Format o);listeners=$results;managedRecordPresent=(Test-Path -LiteralPath (Join-Path $projectRoot 'target/local-runtime.json'))} | ConvertTo-Json -Depth 5
