[CmdletBinding(SupportsShouldProcess=$true)]
param([int]$ProcessId=0,[int]$Port=18080)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'local-runtime.ps1')
$manifest=Join-Path $projectRoot 'target/local-runtime.json'
$expectedStart=$null
if(!$ProcessId){
    if(!(Test-Path -LiteralPath $manifest)){throw 'No managed launch record. Run status-local.ps1 first; stopping a legacy process requires its explicit -ProcessId and -Port.'}
    $record=Get-Content -LiteralPath $manifest -Raw -Encoding UTF8 | ConvertFrom-Json
    if([IO.Path]::GetFullPath($record.projectRoot) -ne [IO.Path]::GetFullPath($projectRoot)){throw 'Launch record belongs to another project'}
    $ProcessId=[int]$record.javaPid;$Port=[int]$record.port;$expectedStart=$record.javaStartedUtc
}
$runtimeProcess=Get-SpaProcessInfo $ProcessId
if(!(Test-SpaProjectProcess $runtimeProcess $projectRoot)){throw 'Refusing to stop: process is not this SPA-Agent application'}
$createdUtc=$runtimeProcess.CreationDate.ToUniversalTime().ToString('o')
if($expectedStart -and $expectedStart -ne $createdUtc){throw 'Refusing to stop: PID was reused'}
if($ProcessId -notin @(Get-SpaListener $Port)){throw 'Refusing to stop: listener does not match recorded process'}
if($PSCmdlet.ShouldProcess("SPA-Agent PID $ProcessId, port $Port, started $createdUtc",'Stop only the verified local Java application')){
    $fresh=Get-SpaProcessInfo $ProcessId
    if($fresh.CreationDate -ne $runtimeProcess.CreationDate -or !(Test-SpaProjectProcess $fresh $projectRoot)){throw 'Process identity changed'}
    $ownedHandle=Get-Process -Id $ProcessId -ErrorAction Stop
    Stop-Process -Id $ProcessId -ErrorAction Stop
    if(!$ownedHandle.WaitForExit(10000)){throw 'The verified process has not exited yet; inspect status before retrying'}
    $releaseDeadline=(Get-Date).AddSeconds(10)
    while(@(Get-SpaListener $Port).Count -and (Get-Date) -lt $releaseDeadline){Start-Sleep -Milliseconds 200}
    if(@(Get-SpaListener $Port).Count){throw 'The Java process exited but the port is occupied; no other process was stopped'}
    if(Test-Path -LiteralPath $manifest){
        $lastRecord=Get-Content -LiteralPath $manifest -Raw -Encoding UTF8 | ConvertFrom-Json
        if($lastRecord.javaPid -eq $ProcessId -and $lastRecord.javaStartedUtc -eq $createdUtc){
            $lastRecord | Add-Member -NotePropertyName stoppedAt -NotePropertyValue (Get-Date -Format o) -Force
            $lastRecord | ConvertTo-Json | Set-Content -LiteralPath $manifest -Encoding UTF8
        }
    }
    Write-Output "Stopped local SPA-Agent PID $ProcessId. No database, Redis or unrelated process was stopped."
}
