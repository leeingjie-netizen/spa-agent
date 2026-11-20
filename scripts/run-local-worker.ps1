param([Parameter(Mandatory)][string]$ProjectRoot,[Parameter(Mandatory)][string]$MavenCommand)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $ProjectRoot
& $MavenCommand spring-boot:run
exit $LASTEXITCODE
