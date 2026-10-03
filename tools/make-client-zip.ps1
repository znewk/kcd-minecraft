param([string]$Out = "$PSScriptRoot\..\dist")
# Собирает архив клиентской сборки для друзей: versions\KCD\ без логов, сохранений и кеша.
# Друг распаковывает архив в %appdata%\.minecraft и выбирает версию «KCD» в TLauncher.
$src = "$env:APPDATA\.minecraft\versions\KCD"
$version = (Get-Content "$PSScriptRoot\..\mod\gradle.properties" | Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=', ''
New-Item -ItemType Directory -Force $Out | Out-Null
$stage = Join-Path $env:TEMP "kcd-client-stage"
Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
$dst = "$stage\versions\KCD"; New-Item -ItemType Directory -Force $dst | Out-Null
$skip = 'logs', 'crash-reports', 'saves', 'natives', 'downloads', 'server-resource-packs', 'screenshots', 'servers.dat_old'
Get-ChildItem $src | Where-Object { $skip -notcontains $_.Name } | ForEach-Object { Copy-Item $_.FullName $dst -Recurse -Force }
# личное: музыка игрока, ключ playit, последний адрес, ключи миров друзей — у каждого своё
foreach ($p in 'kcd\music_pack', 'kcd\playit', 'kcd\last_server.txt', 'kcd\keys.json') { if (Test-Path "$dst\$p") { Remove-Item "$dst\$p" -Recurse -Force } }
$zip = Join-Path (Resolve-Path $Out) "KCD-client-v$version.zip"
Remove-Item $zip -Force -ErrorAction SilentlyContinue
Compress-Archive -Path "$stage\versions" -DestinationPath $zip -CompressionLevel Optimal
"{0} ({1:N1} MB)" -f $zip, ((Get-Item $zip).Length / 1MB)
