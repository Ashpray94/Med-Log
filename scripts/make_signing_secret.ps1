# Makes the value for the GitHub secret MEDLOG_SIGNING from the Meeting Timer key on this PC,
# and puts it on the clipboard. Nothing is uploaded; you paste it into GitHub yourself.
#
#   powershell -ExecutionPolicy Bypass -File scripts\make_signing_secret.ps1
#
# It must be the same key the installed MedLog was signed with, or phones can't update.
$dir = Join-Path $PSScriptRoot "..\..\Google calendar timer\android\keystore"
$props = Join-Path $dir "keystore.properties"
if (-not (Test-Path $props)) { Write-Error "Not found: $props"; exit 1 }
$p = @{}
Get-Content $props | Where-Object { $_ -match '^\s*([^#=]+?)\s*=\s*(.*)$' } | ForEach-Object { $p[$Matches[1]] = $Matches[2] }
$store = if ($p["storeFile"]) { $p["storeFile"] } else { "meeting-timer.jks" }
if (-not [System.IO.Path]::IsPathRooted($store)) { $store = Join-Path $dir $store }
if (-not (Test-Path $store)) { Write-Error "Not found: $store"; exit 1 }
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path $store)))
$value = "storePassword=$($p['storePassword'])`nkeyAlias=$($p['keyAlias'])`nkeyPassword=$($p['keyPassword'])`nkeystore=$b64"
Set-Clipboard -Value $value
Write-Host "Copied. In GitHub: Ashpray94/Med-Log > Settings > Secrets and variables > Actions > New repository secret"
Write-Host "Name: MEDLOG_SIGNING   Secret: paste (Ctrl+V)   then Add secret."
