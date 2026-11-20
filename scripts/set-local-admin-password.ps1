# Interactive local configuration only. Does not create duplicate accounts or change production.
$ErrorActionPreference='Stop'
$configPath=Join-Path (Split-Path -Parent $PSScriptRoot) '.env.local'
if(-not (Test-Path -LiteralPath $configPath)){throw 'Create .env.local first.'}
if((Get-Content -LiteralPath $configPath -Raw) -notmatch '(?m)^SPRING_PROFILES_ACTIVE=local\s*$'){throw 'Only the local profile is allowed.'}
$password=Read-Host 'New local administrator password (at least 12 characters)' -AsSecureString
$confirm=Read-Host 'Repeat password' -AsSecureString
$first=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
$second=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($confirm)
try {
 $plain=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($first)
 if($plain.Length -lt 12 -or $plain.Length -gt 128 -or $plain -cne [Runtime.InteropServices.Marshal]::PtrToStringBSTR($second)){throw 'Passwords must match and contain 12 to 128 characters.'}
 $start=[Diagnostics.ProcessStartInfo]::new()
 $start.FileName='python'
 $start.Arguments='-c "import sys,json,hashlib,secrets,base64,pathlib; d=json.load(sys.stdin); salt=secrets.token_bytes(16); h=hashlib.pbkdf2_hmac(''sha256'',d[''password''].encode(),salt,210000,32); encoded=''210000:''+base64.b64encode(salt).decode()+'':''+base64.b64encode(h).decode(); p=pathlib.Path(d[''path'']); lines=[x for x in p.read_text(encoding=''utf-8-sig'').splitlines() if not x.startswith((''SPA_AUTH_ADMIN_PASSWORD='',''SPA_AUTH_ADMIN_PASSWORD_HASH=''))]; p.write_text(''\n''.join(lines+[''SPA_AUTH_ADMIN_PASSWORD_HASH=''+encoded])+''\n'',encoding=''utf8'')"'
 $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardInput=$true
 $process=[Diagnostics.Process]::Start($start)
 $process.StandardInput.WriteLine((@{password=$plain;path=$configPath}|ConvertTo-Json -Compress))
 $process.StandardInput.Close();$process.WaitForExit()
 if($process.ExitCode -ne 0){throw 'Password configuration failed.'}
 $process.Dispose()
 Write-Output 'Updated ignored .env.local with PBKDF2-SHA256 hash. Restart your local backend, then log in with the password you entered. Existing admin account and data are retained.'
} finally {
 [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($first)
 [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($second)
 $plain=$null;$password.Dispose();$confirm.Dispose()
}
