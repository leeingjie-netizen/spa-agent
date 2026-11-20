param([string]$JavaDirectory='D:\devlop\JDK17',[switch]$CheckAiConfiguration,[ValidateRange(0,65535)][int]$Port=0)
$ErrorActionPreference='Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
$configuration=Join-Path (Get-Location) '.env.local'
if(-not (Test-Path -LiteralPath $configuration)){ throw 'Create .env.local using .env.example first.' }
foreach($line in Get-Content -LiteralPath $configuration -Encoding UTF8){
    if($line -match '^([A-Z][A-Z0-9_]*)=(.*)$'){
        $name=$Matches[1];$value=$Matches[2].Trim()
        if($name -notmatch '^(SPRING_|SPA_|SERVER_PORT$|DASHSCOPE_API_KEY$)'){throw 'Unexpected local configuration key.'}
        if($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))){$value=$value.Substring(1,$value.Length-2)}
        if($value){[Environment]::SetEnvironmentVariable($name,$value,'Process')}
    }
}
if($env:SPRING_PROFILES_ACTIVE -ne 'local'){throw 'This launcher requires the local profile.'}
$aiKeyPresent=![string]::IsNullOrWhiteSpace($env:DASHSCOPE_API_KEY) -and $env:DASHSCOPE_API_KEY -ne 'local-unconfigured'
$commonKeyPresent=![string]::IsNullOrWhiteSpace($env:SPRING_AI_OPENAI_API_KEY) -and $env:SPRING_AI_OPENAI_API_KEY -ne 'local-unconfigured'
$chatKeyPresent=![string]::IsNullOrWhiteSpace($env:SPRING_AI_OPENAI_CHAT_API_KEY) -and $env:SPRING_AI_OPENAI_CHAT_API_KEY -ne 'local-unconfigured'
if($CheckAiConfiguration){
    [ordered]@{dashscopeEnvironmentPresent=$aiKeyPresent;openaiEnvironmentPresent=$commonKeyPresent;chatEnvironmentPresent=$chatKeyPresent;willUsePlaceholder=(!$aiKeyPresent -and !$commonKeyPresent -and !$chatKeyPresent);providerRequestMade=$false} | ConvertTo-Json
    exit 0
}
if(-not $env:DASHSCOPE_API_KEY){$env:DASHSCOPE_API_KEY='local-unconfigured'}
if(!$aiKeyPresent -and !$commonKeyPresent -and !$chatKeyPresent){Write-Warning 'AI credential is missing. Local non-AI mode only; configure DASHSCOPE_API_KEY in the process environment or ignored .env.local. No provider request is made.'}
# Prevent Spring DEBUG request/response logging from recording login credentials, bearer tokens or chat text.
$env:LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB='INFO'
$env:JAVA_HOME=$JavaDirectory
$javaExecutable=Join-Path $JavaDirectory 'bin/java.exe'
if(-not (Test-Path -LiteralPath $javaExecutable)){throw 'JDK directory must contain bin/java.exe.'}
$versionStart=[Diagnostics.ProcessStartInfo]::new()
$versionStart.FileName=$javaExecutable
$versionStart.Arguments='-version'
$versionStart.UseShellExecute=$false
$versionStart.CreateNoWindow=$true
$versionStart.RedirectStandardError=$true
$versionStart.RedirectStandardOutput=$true
$versionProcess=[Diagnostics.Process]::Start($versionStart)
$javaVersion=$versionProcess.StandardError.ReadToEnd()+$versionProcess.StandardOutput.ReadToEnd()
$versionProcess.WaitForExit()
if($versionProcess.ExitCode -ne 0){throw 'Unable to read the selected Java version.'}
$versionProcess.Dispose()
if($javaVersion -notmatch 'version "17\.') {throw 'This project launcher requires JDK 17.'}
$serverPort=18080
if($env:SERVER_PORT){$serverPort=[int]$env:SERVER_PORT}
if($Port){$serverPort=$Port;$env:SERVER_PORT=[string]$Port}
$portProbe=[Net.Sockets.TcpClient]::new()
try {
    $portTask=$portProbe.ConnectAsync('127.0.0.1',$serverPort)
    try {$null=$portTask.Wait(1000)} catch { }
    if($portProbe.Connected){throw "Port $serverPort is already in use. Reuse the existing service or stop only your owned development process."}
} finally {$portProbe.Dispose()}
. (Join-Path $PSScriptRoot 'local-runtime.ps1')
$projectRoot=(Get-Location).Path
$mavenPath=(Get-Command mvn.cmd -ErrorAction Stop).Source
$workerScript=Join-Path $PSScriptRoot 'run-local-worker.ps1'
$serverLog=Join-Path $projectRoot 'target/local-server.log'
$errorLog=Join-Path $projectRoot 'target/local-server-error.log'
New-Item -ItemType Directory -Path (Join-Path $projectRoot 'target') -Force | Out-Null
$worker=Start-Process -FilePath powershell.exe -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+$workerScript+'"'),'-ProjectRoot',('"'+$projectRoot+'"'),'-MavenCommand',('"'+$mavenPath+'"')) -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput $serverLog -RedirectStandardError $errorLog -PassThru
Write-Output "Starting local SPA-Agent. Worker PID $($worker.Id). Waiting for http://127.0.0.1:$serverPort/admin/index.html"
$deadline=(Get-Date).AddSeconds(90)
while((Get-Date) -lt $deadline){
    $worker.Refresh()
    if($worker.HasExited){throw "Local launcher exited. Inspect $serverLog and $errorLog"}
    $owners=@(Get-SpaListener $serverPort)
    if($owners.Count -eq 1){
        $ownedJava=Get-SpaProcessInfo $owners[0]
        if(!(Test-SpaProjectProcess $ownedJava $projectRoot) -or !(Test-SpaDescendant $ownedJava $worker.Id)){throw 'Listener does not belong to this launch. No process was stopped.'}
        try{$response=Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$serverPort/admin/index.html" -TimeoutSec 2}catch{$response=$null}
        if($response -and $response.StatusCode -eq 200){
            $record=[ordered]@{projectRoot=$projectRoot;port=$serverPort;url="http://127.0.0.1:$serverPort/admin/index.html";javaPid=$ownedJava.ProcessId;javaStartedUtc=$ownedJava.CreationDate.ToUniversalTime().ToString('o');workerPid=$worker.Id;startedAt=(Get-Date -Format o);resourceDirectory=(Join-Path $projectRoot 'target/classes/static/admin');log=$serverLog}
            $record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $projectRoot 'target/local-runtime.json') -Encoding UTF8
            Write-Output "READY: $($record.url) | Java PID $($record.javaPid) | local profile / JDK17"
            Write-Output 'Service runs in background. Closing this PowerShell does NOT stop it. Use ./scripts/status-local.ps1 and ./scripts/stop-local.ps1 (-WhatIf previews the stop).'
            exit 0
        }
    }
    Start-Sleep -Milliseconds 500
}
throw "Startup is not ready. Check ./scripts/status-local.ps1 and $serverLog before retrying. No process was automatically terminated."
