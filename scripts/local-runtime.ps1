# Shared, read-only project/process identity checks. Never print environment secrets.
function Get-SpaProcessInfo([int]$JavaId) {
    Get-CimInstance Win32_Process -Filter "ProcessId=$JavaId" -ErrorAction Stop
}
function Test-SpaProjectProcess($RuntimeProcess,[string]$ProjectRoot) {
    if(!$RuntimeProcess -or $RuntimeProcess.Name -notin @('java.exe','javaw.exe')){return $false}
    $rootPath=[IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\')
    $command=[string]$RuntimeProcess.CommandLine
    $matchesMain=$command -match 'com\.lee\.spa\.SpaAgentApplication'
    $matchesJar=$command -match 'spa-agent[^\s]*\.jar'
    if(!$matchesMain -and !$matchesJar){return $false}
    $classpath=$command
    $argMatch=[regex]::Match($command,'@("[^"]+"|[^\s]+)')
    if($argMatch.Success){
        $argFile=$argMatch.Groups[1].Value.Trim('"')
        if(!(Test-Path -LiteralPath $argFile)){return $false}
        $classpath += [IO.File]::ReadAllText($argFile).Replace('\\','\')
    }
    $classes=Join-Path $rootPath 'target\classes'
    $archive=Join-Path $rootPath 'target\spa-agent-0.0.1-SNAPSHOT.jar'
    return $classpath.IndexOf($classes,[StringComparison]::OrdinalIgnoreCase) -ge 0 -or $classpath.IndexOf($archive,[StringComparison]::OrdinalIgnoreCase) -ge 0
}
function Get-SpaListener([int]$Port) {
    @(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object {$_.LocalPort -eq $Port} | Select-Object -ExpandProperty OwningProcess -Unique)
}
function Test-SpaDescendant($RuntimeProcess,[int]$AncestorId) {
    $current=$RuntimeProcess
    for($depth=0;$depth -lt 10 -and $current;$depth++){
        if($current.ProcessId -eq $AncestorId){return $true}
        $parent=Get-SpaProcessInfo $current.ParentProcessId
        if(!$parent -or $parent.CreationDate -gt $current.CreationDate){return $false}
        $current=$parent
    }
    return $false
}
