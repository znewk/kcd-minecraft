param([int]$WaitSec = 600, [int]$StaySec = 15)
# Пробный запуск версии «KCD» так же, как это делает TLauncher (NeoForge 1.21.1):
# ждёт главное меню или краш, затем закрывает игру. Лог: versions\KCD\logs\latest.log
$mc   = "$env:APPDATA\.minecraft"
$ver  = 'KCD'
$gd   = "$mc\versions\$ver"
$lib  = "$mc\libraries"
$java = "$mc\runtime\java-runtime-delta\windows\java-runtime-delta\bin\java.exe"
$j = Get-Content "$gd\$ver.json" -Raw | ConvertFrom-Json
# TLauncher подменяет authlib и patchy на свои
$repl = @{ 'com.mojang:authlib:6.0.54' = 'org.tlauncher:authlib:6.0.54.2'; 'com.mojang:patchy:2.2.10' = 'org.tlauncher:patchy:2.2.101' }
$cp = foreach ($l in $j.libraries) {
    if ($l.rules -and -not ($l.rules | Where-Object { $_.action -eq 'allow' -and (-not $_.os -or -not $_.os.name -or $_.os.name -eq 'windows') })) { continue }
    $n = $l.name; if ($repl.ContainsKey($n)) { $n = $repl[$n] }
    $p = $n.Split(':'); $path = "$lib\$($p[0].Replace('.','\'))\$($p[1])\$($p[2])\$($p[1])-$($p[2])"
    if ($p.Count -gt 3) { $path += "-$($p[3])" }
    "$path.jar"
}
$cp += "$gd\$ver.jar"
$modulePath = @(
    'cpw/mods/bootstraplauncher/2.0.2/bootstraplauncher-2.0.2.jar', 'cpw/mods/securejarhandler/3.0.8/securejarhandler-3.0.8.jar',
    'org/ow2/asm/asm-commons/9.10.1/asm-commons-9.10.1.jar', 'org/ow2/asm/asm-util/9.10.1/asm-util-9.10.1.jar',
    'org/ow2/asm/asm-analysis/9.10.1/asm-analysis-9.10.1.jar', 'org/ow2/asm/asm-tree/9.10.1/asm-tree-9.10.1.jar',
    'org/ow2/asm/asm/9.10.1/asm-9.10.1.jar', 'net/neoforged/JarJarFileSystems/0.4.1/JarJarFileSystems-0.4.1.jar'
) | ForEach-Object { "$lib\$($_.Replace('/','\'))" }
$log = "$gd\logs\latest.log"
if (Test-Path $log) { Remove-Item $log -Force }
$crashBefore = @(Get-ChildItem "$gd\crash-reports" -ErrorAction SilentlyContinue).Count
$jargs = @('-Xmx6G', '-XX:+UseG1GC', '-Dfml.ignoreInvalidMinecraftCertificates=true', '-Dfml.ignorePatchDiscrepancies=true',
    "-Djava.library.path=$gd\natives", "-Dorg.lwjgl.system.SharedLibraryExtractPath=$gd\natives",
    '-Dminecraft.launcher.brand=minecraft-launcher', '-Dminecraft.launcher.version=2.3.173',
    '-cp', ($cp -join ';'), '-DignoreList=client-extra,KCD.jar', "-DlibraryDirectory=$lib",
    '-p', ($modulePath -join ';'), '--add-modules', 'ALL-MODULE-PATH',
    '--add-opens', 'java.base/java.util.jar=cpw.mods.securejarhandler', '--add-opens', 'java.base/java.lang.invoke=cpw.mods.securejarhandler',
    '--add-exports', 'java.base/sun.security.util=cpw.mods.securejarhandler', '--add-exports', 'jdk.naming.dns/com.sun.jndi.dns=java.naming',
    "-Dlog4j.configurationFile=$mc\assets\log_configs\client-1.12.xml",
    'cpw.mods.bootstraplauncher.BootstrapLauncher',
    '--username', 'znewk', '--version', $ver, '--gameDir', $gd, '--assetsDir', "$mc\assets", '--assetIndex', '17',
    '--uuid', '7199fa50-b9f1-11eb-b83c-d45d64bce613', '--accessToken', 'null', '--userType', 'mojang', '--versionType', 'modified',
    '--width', '925', '--height', '530',
    '--fml.neoForgeVersion', '21.1.252', '--fml.fmlVersion', '4.0.44', '--fml.mcVersion', '1.21.1', '--fml.neoFormVersion', '20240808.144430',
    '--launchTarget', 'forgeclient')
$quoted = $jargs | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }
$out = "$env:TEMP\kcd-testlaunch"; New-Item -ItemType Directory -Force $out | Out-Null
$p = Start-Process -FilePath $java -ArgumentList $quoted -WorkingDirectory $gd -PassThru -RedirectStandardOutput "$out\stdout.txt" -RedirectStandardError "$out\stderr.txt"
$t0 = Get-Date; $result = 'TIMEOUT'
while (((Get-Date) - $t0).TotalSeconds -lt $WaitSec) {
    Start-Sleep 3
    if ($p.HasExited) { $result = "EXITED code=$($p.ExitCode)"; break }
    if ((Test-Path $log) -and (Select-String -Path $log -Pattern 'Sound engine started' -Quiet)) {
        Start-Sleep $StaySec; $result = if ($p.HasExited) { "EXITED code=$($p.ExitCode)" } else { 'MAIN_MENU_REACHED' }; break
    }
}
"RESULT: $result after $([int]((Get-Date) - $t0).TotalSeconds)s"
if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force }
$crashAfter = @(Get-ChildItem "$gd\crash-reports" -ErrorAction SilentlyContinue).Count
if ($crashAfter -gt $crashBefore) { "NEW CRASH REPORT: " + (Get-ChildItem "$gd\crash-reports" | Sort-Object LastWriteTime | Select-Object -Last 1).FullName }
if (Test-Path $log) {
    "--- KCD lines:"; Select-String -Path $log -Pattern 'KCD:' | ForEach-Object Line
    "--- errors:"; Select-String -Path $log -Pattern '/ERROR\]|Exception' | Select-Object -First 15 | ForEach-Object Line
}
