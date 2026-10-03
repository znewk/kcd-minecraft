# Перерисовать карту местности KCD: tools/map/skalitz.json + kcdmap/buildings.json → kcdmap/world.bin.gz и tools/map/out/map.png.
# Gson берётся из кеша Gradle (появляется после первой сборки мода).
$root = Resolve-Path "$PSScriptRoot\..\.."
$gson = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.google.code.gson\gson" -Recurse -Filter 'gson-2*.jar' |
    Where-Object { $_.Name -notmatch 'sources' } | Select-Object -First 1
if (-not $gson) { throw 'Gson не найден — сначала соберите мод (mod\gradlew.bat build)' }
& java -cp $gson.FullName "$PSScriptRoot\GenMap.java" $root
