param([Parameter(Mandatory)][string]$Command, [string]$HostName = '127.0.0.1', [int]$Port = 25575, [string]$Password = 'kcd-local')
# Мини-клиент RCON: отправить команду серверу и вывести ответ. Пример: .\rcon.ps1 "list"
function Send-Packet($stream, [int]$id, [int]$type, [string]$body) {
    $b = [Text.Encoding]::UTF8.GetBytes($body)
    $len = 4 + 4 + $b.Length + 2
    $buf = New-Object byte[] ($len + 4)
    [BitConverter]::GetBytes([int]$len).CopyTo($buf, 0)
    [BitConverter]::GetBytes($id).CopyTo($buf, 4)
    [BitConverter]::GetBytes($type).CopyTo($buf, 8)
    $b.CopyTo($buf, 12)
    $stream.Write($buf, 0, $buf.Length)
}
function Read-Packet($stream) {
    $head = New-Object byte[] 4; $n = 0
    while ($n -lt 4) { $r = $stream.Read($head, $n, 4 - $n); if ($r -le 0) { return $null }; $n += $r }
    $len = [BitConverter]::ToInt32($head, 0)
    $data = New-Object byte[] $len; $n = 0
    while ($n -lt $len) { $r = $stream.Read($data, $n, $len - $n); if ($r -le 0) { break }; $n += $r }
    [pscustomobject]@{ Id = [BitConverter]::ToInt32($data, 0); Body = [Text.Encoding]::UTF8.GetString($data, 8, [Math]::Max(0, $len - 10)) }
}
$client = New-Object Net.Sockets.TcpClient($HostName, $Port)
$s = $client.GetStream()
Send-Packet $s 1 3 $Password
$auth = Read-Packet $s
if ($auth.Id -eq -1) { throw 'RCON: неверный пароль' }
Send-Packet $s 2 2 $Command
$resp = Read-Packet $s
$client.Close()
$resp.Body
