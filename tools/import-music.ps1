param([Parameter(Mandatory)][string]$Source,
      [string]$GameDir = "$env:APPDATA\.minecraft\versions\KCD",
      [string]$Map = "$PSScriptRoot\music-map.json")
# Импорт ЛИЧНОЙ музыки игрока в игру KCD (на этом компьютере).
# Берёт mp3/ogg/wav/flac из папки -Source, раскладывает по ситуациям по таблице music-map.json,
# переводит в .ogg и собирает пакет kcd\music_pack — он подменяет свободные треки мода.
# Пакет не входит ни в репозиторий, ни в архив для друзей. Нужен ffmpeg (winget install Gyan.FFmpeg).
$ErrorActionPreference = 'Stop'
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) { throw 'Не найден ffmpeg. Установите: winget install Gyan.FFmpeg' }

function Norm([string]$s) { ($s.ToLower() -replace '^\s*\d+[\s._-]*', '' -replace '[^\p{L}\p{N}]', '') }

$mapping = Get-Content $Map -Raw -Encoding UTF8 | ConvertFrom-Json
$pack = Join-Path $GameDir 'kcd\music_pack'
if (Test-Path $pack) { Remove-Item $pack -Recurse -Force }
$soundsDir = Join-Path $pack 'assets\kcd\sounds\personal'
New-Item -ItemType Directory -Force $soundsDir | Out-Null

$files = Get-ChildItem $Source -File | Where-Object { $_.Extension -match '^\.(mp3|ogg|wav|flac|m4a)$' }
$moods = @{}
$used = @{}
foreach ($prop in $mapping.PSObject.Properties) {
    if ($prop.Name.StartsWith('_')) { continue }
    $list = New-Object System.Collections.Generic.List[string]
    foreach ($title in $prop.Value) {
        $nt = Norm $title
        $f = $files | Where-Object { (Norm $_.BaseName) -eq $nt } | Select-Object -First 1
        if (-not $f) { $f = $files | Where-Object { (Norm $_.BaseName).Contains($nt) } | Select-Object -First 1 }
        if (-not $f) { Write-Host "  нет файла для «$title» ($($prop.Name))" -ForegroundColor DarkYellow; continue }
        $slug = (Norm $f.BaseName)
        if (-not $used.ContainsKey($slug)) {
            $ogg = Join-Path $soundsDir "$slug.ogg"
            & ffmpeg -y -loglevel error -i $f.FullName -vn -ac 2 -ar 44100 -c:a libvorbis -q:a 4 $ogg
            $used[$slug] = $f.Name
        }
        $list.Add("kcd:personal/$slug")
    }
    if ($list.Count -gt 0) { $moods[$prop.Name] = $list }
}

# sounds.json: ситуации с личной музыкой полностью заменяют свободные треки мода
$json = [ordered]@{}
foreach ($m in $moods.Keys | Sort-Object) {
    $json["mood.$m"] = [ordered]@{ replace = $true; sounds = @($moods[$m] | ForEach-Object { [ordered]@{ name = $_; stream = $true } }) }
}
$utf8 = New-Object Text.UTF8Encoding $false
[IO.File]::WriteAllText((Join-Path $pack 'assets\kcd\sounds.json'), ($json | ConvertTo-Json -Depth 6), $utf8)
[IO.File]::WriteAllText((Join-Path $pack 'pack.mcmeta'), '{ "pack": { "pack_format": 34, "description": "KCD: личная музыка" } }', $utf8)

Write-Host ""
Write-Host "Готово: $($used.Count) треков в $pack" -ForegroundColor Green
foreach ($m in $moods.Keys | Sort-Object) { Write-Host ("  {0,-14} {1}" -f $m, $moods[$m].Count) }
$unused = $files | Where-Object { -not $used.ContainsKey((Norm $_.BaseName)) }
if ($unused) { Write-Host "Не размечены (не играют): $(($unused | ForEach-Object Name) -join ', ')" -ForegroundColor DarkYellow }
Write-Host "Перезапустите игру — музыка подхватится."
