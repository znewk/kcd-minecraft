param([int]$WaitSec = 600, [int]$StaySec = 15, [string]$Join = '', [string]$Single = '', [string]$User = 'znewk',
      [string]$Uuid = '7199fa50-b9f1-11eb-b83c-d45d64bce613', [string]$Shot = '', [int]$ShotDelay = 12, [string]$Xmx = '4G',
      [string]$GameDir = '', [string[]]$JvmExtra = @(), [string]$ReadyPattern = '', [switch]$NoKill,
      [string]$JoinedPattern = 'logged in with entity id', [string]$ServerLog = "$PSScriptRoot\..\server\console.txt")
# Пробный запуск версии «KCD» так же, как это делает TLauncher (NeoForge 1.21.1).
# Ждёт: главное меню / вход в мир (-Single) / заход на сервер (-Join) / строку в логе (-ReadyPattern),
# делает снимок окна (-Shot), затем закрывает игру (или оставляет с -NoKill и печатает PID).
$mc   = "$env:APPDATA\.minecraft"
$ver  = 'KCD'
$vd   = "$mc\versions\$ver"
$gd   = if ($GameDir) { $GameDir } else { $vd }
$lib  = "$mc\libraries"
$java = "$mc\runtime\java-runtime-delta\windows\java-runtime-delta\bin\javaw.exe"
$j = Get-Content "$vd\$ver.json" -Raw | ConvertFrom-Json
# TLauncher подменяет authlib и patchy на свои
$repl = @{ 'com.mojang:authlib:6.0.54' = 'org.tlauncher:authlib:6.0.54.2'; 'com.mojang:patchy:2.2.10' = 'org.tlauncher:patchy:2.2.101' }
$cp = foreach ($l in $j.libraries) {
    if ($l.rules -and -not ($l.rules | Where-Object { $_.action -eq 'allow' -and (-not $_.os -or -not $_.os.name -or $_.os.name -eq 'windows') })) { continue }
    $n = $l.name; if ($repl.ContainsKey($n)) { $n = $repl[$n] }
    $p = $n.Split(':'); $path = "$lib\$($p[0].Replace('.','\'))\$($p[1])\$($p[2])\$($p[1])-$($p[2])"
    if ($p.Count -gt 3) { $path += "-$($p[3])" }
    "$path.jar"
}
$cp += "$vd\$ver.jar"
$modulePath = @(
    'cpw/mods/bootstraplauncher/2.0.2/bootstraplauncher-2.0.2.jar', 'cpw/mods/securejarhandler/3.0.8/securejarhandler-3.0.8.jar',
    'org/ow2/asm/asm-commons/9.10.1/asm-commons-9.10.1.jar', 'org/ow2/asm/asm-util/9.10.1/asm-util-9.10.1.jar',
    'org/ow2/asm/asm-analysis/9.10.1/asm-analysis-9.10.1.jar', 'org/ow2/asm/asm-tree/9.10.1/asm-tree-9.10.1.jar',
    'org/ow2/asm/asm/9.10.1/asm-9.10.1.jar', 'net/neoforged/JarJarFileSystems/0.4.1/JarJarFileSystems-0.4.1.jar'
) | ForEach-Object { "$lib\$($_.Replace('/','\'))" }
$log = "$gd\logs\latest.log"
if (Test-Path $log) { Remove-Item $log -Force }
$crashBefore = @(Get-ChildItem "$gd\crash-reports" -ErrorAction SilentlyContinue).Count
$jargs = @("-Xmx$Xmx", '-XX:+UseG1GC', '-Dfml.ignoreInvalidMinecraftCertificates=true', '-Dfml.ignorePatchDiscrepancies=true',
    "-Djava.library.path=$vd\natives", "-Dorg.lwjgl.system.SharedLibraryExtractPath=$vd\natives",
    '-Dminecraft.launcher.brand=minecraft-launcher', '-Dminecraft.launcher.version=2.3.173') + $JvmExtra + @(
    '-cp', ($cp -join ';'), '-DignoreList=client-extra,KCD.jar', "-DlibraryDirectory=$lib",
    '-p', ($modulePath -join ';'), '--add-modules', 'ALL-MODULE-PATH',
    '--add-opens', 'java.base/java.util.jar=cpw.mods.securejarhandler', '--add-opens', 'java.base/java.lang.invoke=cpw.mods.securejarhandler',
    '--add-exports', 'java.base/sun.security.util=cpw.mods.securejarhandler', '--add-exports', 'jdk.naming.dns/com.sun.jndi.dns=java.naming',
    "-Dlog4j.configurationFile=$mc\assets\log_configs\client-1.12.xml",
    'cpw.mods.bootstraplauncher.BootstrapLauncher',
    '--username', $User, '--version', $ver, '--gameDir', $gd, '--assetsDir', "$mc\assets", '--assetIndex', '17',
    '--uuid', $Uuid, '--accessToken', 'null', '--userType', 'mojang', '--versionType', 'modified',
    '--width', '925', '--height', '530',
    '--fml.neoForgeVersion', '21.1.252', '--fml.fmlVersion', '4.0.44', '--fml.mcVersion', '1.21.1', '--fml.neoFormVersion', '20240808.144430',
    '--launchTarget', 'forgeclient')
if ($Join) { $jargs += @('--quickPlayMultiplayer', $Join) }
if ($Single) { $jargs += @('--quickPlaySingleplayer', $Single) }
$quoted = $jargs | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }
$out = "$env:TEMP\kcd-testlaunch-$User"; New-Item -ItemType Directory -Force $out | Out-Null
$p = Start-Process -FilePath $java -ArgumentList $quoted -WorkingDirectory $gd -PassThru -RedirectStandardOutput "$out\stdout.txt" -RedirectStandardError "$out\stderr.txt"
$t0 = Get-Date; $result = 'TIMEOUT'
$joinedBefore = if ($Join -and (Test-Path $ServerLog)) { @(Select-String -Path $ServerLog -Pattern "$User\[.*$JoinedPattern").Count } else { 0 }
while (((Get-Date) - $t0).TotalSeconds -lt $WaitSec) {
    Start-Sleep 3
    if ($p.HasExited) { $result = "EXITED code=$($p.ExitCode)"; break }
    if ($Join) {
        if ((Test-Path $ServerLog) -and @(Select-String -Path $ServerLog -Pattern "$User\[.*$JoinedPattern").Count -gt $joinedBefore) { $result = 'JOINED_SERVER'; break }
    } elseif ($ReadyPattern) {
        if ((Test-Path $log) -and (Select-String -Path $log -Pattern $ReadyPattern -Quiet)) { $result = 'READY'; break }
    } elseif ($Single) {
        if ((Test-Path $log) -and (Select-String -Path $log -Pattern "$User\[.*logged in with entity id" -Quiet)) { $result = 'IN_WORLD'; break }
    } elseif ((Test-Path $log) -and (Select-String -Path $log -Pattern 'Sound engine started' -Quiet)) {
        $result = 'MAIN_MENU_REACHED'; break
    }
}
if ($result -in 'MAIN_MENU_REACHED', 'JOINED_SERVER', 'READY', 'IN_WORLD') {
    if ($Shot) {
        Start-Sleep $ShotDelay
        Add-Type -AssemblyName System.Drawing
        Add-Type @'
using System; using System.Runtime.InteropServices;
public static class KcdWin { [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h); }
'@ -ErrorAction SilentlyContinue
        $p.Refresh(); $h = $p.MainWindowHandle
        [KcdWin]::SetForegroundWindow($h) | Out-Null; Start-Sleep 1
        $r = New-Object KcdWin+RECT; [KcdWin]::GetWindowRect($h, [ref]$r) | Out-Null
        $bmp = New-Object Drawing.Bitmap ($r.R - $r.L), ($r.B - $r.T)
        $gfx = [Drawing.Graphics]::FromImage($bmp); $gfx.CopyFromScreen($r.L, $r.T, 0, 0, $bmp.Size); $bmp.Save($Shot); $gfx.Dispose(); $bmp.Dispose()
        "SCREENSHOT: $Shot"
    }
    if (-not $NoKill) {
        $remain = $StaySec - $(if ($Shot) { $ShotDelay } else { 0 })
        if ($remain -gt 0) { Start-Sleep $remain }
    }
    if ($p.HasExited) { $result = "EXITED code=$($p.ExitCode) (after $result)" }
}
"RESULT: $result after $([int]((Get-Date) - $t0).TotalSeconds)s"
if ($NoKill -and -not $p.HasExited) { "PID: $($p.Id)"; return }
if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force }
$crashAfter = @(Get-ChildItem "$gd\crash-reports" -ErrorAction SilentlyContinue).Count
if ($crashAfter -gt $crashBefore) { "NEW CRASH REPORT: " + (Get-ChildItem "$gd\crash-reports" | Sort-Object LastWriteTime | Select-Object -Last 1).FullName }
if (Test-Path $log) {
    "--- KCD lines:"; Select-String -Path $log -Pattern 'KCD:' | ForEach-Object Line
    "--- errors:"; Select-String -Path $log -Pattern '/ERROR\]|Exception' | Select-Object -First 15 | ForEach-Object Line
}
