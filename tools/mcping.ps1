param([Parameter(Mandatory)][string]$HostName, [int]$Port = 0, [int]$TimeoutSec = 10)
# Пинг сервера Minecraft как из списка серверов (handshake + status). Без порта — берёт SRV-запись, как игра.
# Пример: tools\mcping.ps1 stamina-coupe.tun.ply.gg
$target = $HostName
if ($Port -eq 0) {
    $srv = Resolve-DnsName "_minecraft._tcp.$HostName" -Type SRV -ErrorAction SilentlyContinue | Where-Object Type -eq 'SRV' | Select-Object -First 1
    if ($srv) { $target = $srv.NameTarget; $Port = $srv.Port } else { $Port = 25565 }
}
function VarInt([int]$v) { $b = New-Object System.Collections.Generic.List[byte]; do { $x = $v -band 0x7F; $v = $v -shr 7; if ($v -ne 0) { $x = $x -bor 0x80 }; $b.Add([byte]$x) } while ($v -ne 0); , $b.ToArray() }
function Packet([byte[]]$body) { , ((VarInt $body.Length) + $body) }
$hostBytes = [Text.Encoding]::UTF8.GetBytes($HostName)
$hs = (VarInt 0) + (VarInt 767) + (VarInt $hostBytes.Length) + $hostBytes + [byte[]]@([byte]($Port -shr 8), [byte]($Port -band 0xFF)) + (VarInt 1)
$client = New-Object Net.Sockets.TcpClient
$t0 = Get-Date
try {
    $iar = $client.BeginConnect($target, $Port, $null, $null)
    if (-not $iar.AsyncWaitHandle.WaitOne($TimeoutSec * 1000)) { "FAIL: connect timeout ($target`:$Port)"; return }
    $client.EndConnect($iar)
    $s = $client.GetStream(); $s.ReadTimeout = $TimeoutSec * 1000
    $p1 = Packet $hs; $p2 = Packet ([byte[]]@(0))
    $s.Write($p1, 0, $p1.Length); $s.Write($p2, 0, $p2.Length)
    $buf = New-Object byte[] 65536; $total = 0
    do { $n = $s.Read($buf, $total, $buf.Length - $total); $total += $n; $txt = [Text.Encoding]::UTF8.GetString($buf, 0, $total) } while ($n -gt 0 -and -not ($txt -match '\}\s*$'))
    $json = $txt.Substring($txt.IndexOf('{'))
    "OK ({0} ms): {1}" -f [int]((Get-Date) - $t0).TotalMilliseconds, $json.Substring(0, [Math]::Min(300, $json.Length))
} catch {
    "FAIL after {0} s: {1}" -f [int]((Get-Date) - $t0).TotalSeconds, $_.Exception.Message
} finally { $client.Close() }
