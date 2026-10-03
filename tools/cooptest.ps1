param([string]$World = 'kcdtest', [string]$FriendRole = 'brother:Вашек:1', [int]$HoldSec = 25)
# Кооп-тест на одном ПК: хост открывает мир для отряда (без playit), «друг» заходит на localhost:25565.
# Снимки: %TEMP%\kcd-coop-host.png, %TEMP%\kcd-coop-friend.png. Итог — в конце вывода.
$t = "$PSScriptRoot\testlaunch.ps1"
$hostLog = "$env:APPDATA\.minecraft\versions\KCD\logs\latest.log"
$friendDir = "$env:TEMP\kcd-client2"
$friendLog = "$friendDir\logs\latest.log"

$h = & $t -Single $World -JvmExtra '-Dkcd.autohost=true', '-Dkcd.playit=false', '-Dkcd.autorole=henry' `
    -ReadyPattern 'Started serving on 25565' -NoKill -WaitSec 300 -Xmx 3G
$h
$hostPid = ($h | Where-Object { $_ -like 'PID:*' }) -replace 'PID: ', ''
if (-not $hostPid) { 'HOST FAILED'; return }

$f = & $t -GameDir $friendDir -User 'Friend' -Uuid '11111111-2222-3333-4444-555555555555' -Join 'localhost:25565' `
    -ServerLog $hostLog -JvmExtra "-Dkcd.autorole=$FriendRole" -Shot "$env:TEMP\kcd-coop-friend.png" -ShotDelay 12 -NoKill -WaitSec 300 -Xmx 3G
$f
$friendPid = ($f | Where-Object { $_ -like 'PID:*' }) -replace 'PID: ', ''

# снимок хоста
Start-Sleep 3
Add-Type -AssemblyName System.Drawing
$hp = Get-Process -Id $hostPid -ErrorAction SilentlyContinue
if ($hp) {
    [KcdWin]::SetForegroundWindow($hp.MainWindowHandle) | Out-Null; Start-Sleep 2
    $r = New-Object KcdWin+RECT; [KcdWin]::GetWindowRect($hp.MainWindowHandle, [ref]$r) | Out-Null
    $bmp = New-Object Drawing.Bitmap ($r.R - $r.L), ($r.B - $r.T)
    $g = [Drawing.Graphics]::FromImage($bmp); $g.CopyFromScreen($r.L, $r.T, 0, 0, $bmp.Size); $bmp.Save("$env:TEMP\kcd-coop-host.png"); $g.Dispose(); $bmp.Dispose()
    'SCREENSHOT host'
}
Start-Sleep $HoldSec
foreach ($id in $friendPid, $hostPid) { if ($id) { Stop-Process -Id $id -Force -ErrorAction SilentlyContinue } }

'=== HOST log'
Select-String -Path $hostLog -Encoding UTF8 -Pattern 'Started serving|joined the game|left the game|KCD:|Индржих|брат|/ERROR\]|Exception' | Select-Object -Last 20 | ForEach-Object Line
'=== FRIEND log'
Select-String -Path $friendLog -Encoding UTF8 -Pattern 'Connecting to|KCD:|Индржих|брат|Disconnect|/ERROR\]|Exception' | Select-Object -Last 20 | ForEach-Object Line
