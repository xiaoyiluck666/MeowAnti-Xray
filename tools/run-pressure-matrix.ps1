[CmdletBinding()]
param(
    [ValidateSet("fabric", "neoforge")]
    [string[]] $Loaders = @("fabric", "neoforge"),

    [int[]] $QueueSizes = @(0, 16, 32, 64),

    [ValidateRange(1, 200)]
    [int] $Clients = 8,

    [ValidateRange(1, 3600)]
    [int] $DurationSeconds = 90,

    [switch] $AllowDirty
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$resultsDirectory = Join-Path $repoRoot "build\pressure-results"
New-Item -ItemType Directory -Path $resultsDirectory -Force | Out-Null

function Get-PropertiesFile {
    param([string] $Path)

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*([^#][^=]*?)\s*=\s*(.*?)\s*$') {
            $values[$Matches[1].Trim()] = $Matches[2].Trim()
        }
    }
    return $values
}

function Set-QueueSize {
    param(
        [string] $Path,
        [int] $QueueSize
    )

    $text = [IO.File]::ReadAllText($Path)
    $matches = [regex]::Matches($text, '(?m)^(\s*)async-queue-size:\s*\d+(\s*)$')
    if ($matches.Count -ne 1) {
        throw "Could not locate exactly one async-queue-size in $Path"
    }
    $updated = [regex]::Replace(
        $text,
        '(?m)^(\s*async-queue-size:\s*)\d+(\s*)$',
        { param($match) $match.Groups[1].Value + $QueueSize + $match.Groups[2].Value },
        1
    )
    if ($updated -ne $text) {
        [IO.File]::WriteAllText($Path, $updated, [Text.UTF8Encoding]::new($false))
    }
}

function Send-RconPacket {
    param(
        [Net.Sockets.NetworkStream] $Stream,
        [int] $Id,
        [int] $Type,
        [string] $Body
    )

    $bodyBytes = [Text.Encoding]::UTF8.GetBytes($Body)
    $length = 4 + 4 + $bodyBytes.Length + 2
    $buffer = [byte[]]::new(4 + $length)
    [BitConverter]::GetBytes([int] $length).CopyTo($buffer, 0)
    [BitConverter]::GetBytes([int] $Id).CopyTo($buffer, 4)
    [BitConverter]::GetBytes([int] $Type).CopyTo($buffer, 8)
    $bodyBytes.CopyTo($buffer, 12)
    $buffer[12 + $bodyBytes.Length] = 0
    $buffer[13 + $bodyBytes.Length] = 0
    $Stream.Write($buffer, 0, $buffer.Length)
}

function Read-RconBytes {
    param(
        [Net.Sockets.NetworkStream] $Stream,
        [byte[]] $Buffer,
        [int] $Offset,
        [int] $Count
    )

    $read = 0
    while ($read -lt $Count) {
        $current = $Stream.Read($Buffer, $Offset + $read, $Count - $read)
        if ($current -le 0) {
            throw "RCON connection closed"
        }
        $read += $current
    }
}

function Receive-RconPacket {
    param([Net.Sockets.NetworkStream] $Stream)

    $lengthBytes = [byte[]]::new(4)
    Read-RconBytes $Stream $lengthBytes 0 4
    $length = [BitConverter]::ToInt32($lengthBytes, 0)
    $payload = [byte[]]::new($length)
    Read-RconBytes $Stream $payload 0 $length
    return [pscustomobject]@{
        Id = [BitConverter]::ToInt32($payload, 0)
        Type = [BitConverter]::ToInt32($payload, 4)
        Body = [Text.Encoding]::UTF8.GetString($payload, 8, $length - 10)
    }
}

function Invoke-RconCommand {
    param(
        [string] $Host,
        [int] $Port,
        [string] $Password,
        [string] $Command
    )

    $client = [Net.Sockets.TcpClient]::new()
    try {
        $client.Connect($Host, $Port)
        $stream = $client.GetStream()
        Send-RconPacket $stream 1 3 $Password
        $auth = Receive-RconPacket $stream
        if ($auth.Id -eq -1) {
            throw "RCON authentication failed"
        }
        Send-RconPacket $stream 2 2 $Command
        return (Receive-RconPacket $stream).Body
    } finally {
        $client.Close()
    }
}

function Start-TestServer {
    param(
        [string] $Loader,
        [string] $LogBase
    )

    $stdoutPath = "$LogBase.stdout.log"
    $stderrPath = "$LogBase.stderr.log"
    Remove-Item $stdoutPath, $stderrPath -Force -ErrorAction SilentlyContinue
    $arguments = if ($Loader -eq "fabric") {
        "runServer --console=plain"
    } else {
        ":neoforge:runServer --console=plain"
    }
    $process = Start-Process `
        -FilePath "cmd.exe" `
        -ArgumentList "/c .\gradlew.bat $arguments" `
        -WorkingDirectory $repoRoot `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath `
        -WindowStyle Hidden `
        -PassThru

    $deadline = (Get-Date).AddMinutes(4)
    do {
        Start-Sleep -Seconds 3
        $content = if (Test-Path -LiteralPath $stdoutPath) {
            [IO.File]::ReadAllText($stdoutPath)
        } else {
            ""
        }
        if ($content -match "Done" -and $content -match "Meow Anti-Xray startup complete") {
            return $process
        }
        if ($process.HasExited -and $process.ExitCode -ne 0) {
            throw "$Loader server exited with code $($process.ExitCode). See $stdoutPath and $stderrPath"
        }
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for $Loader server startup. See $stdoutPath and $stderrPath"
}

function Stop-TestServer {
    param(
        [string] $Loader,
        [int] $RconPort,
        [string] $RconPassword,
        [System.Diagnostics.Process] $Wrapper
    )

    try {
        Invoke-RconCommand "127.0.0.1" $RconPort $RconPassword "stop" | Out-Null
    } catch {
        Write-Warning "RCON stop failed for ${Loader}: $($_.Exception.Message)"
    }

    $deadline = (Get-Date).AddMinutes(2)
    while (-not $Wrapper.HasExited -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
    }
    if (-not $Wrapper.HasExited) {
        Get-CimInstance Win32_Process |
            Where-Object {
                $_.Name -eq "java.exe" -and
                $_.CommandLine -like "*$repoRoot*" -and
                $_.CommandLine -like "*runServer*"
            } |
            ForEach-Object {
                Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
            }
        Stop-Process -Id $Wrapper.Id -Force -ErrorAction SilentlyContinue
    }
}

$settings = @{
    fabric = @{
        ServerRoot = Join-Path $repoRoot "run"
        ConfigPath = Join-Path $repoRoot "run\config\meowantixray.yml"
        PropertiesPath = Join-Path $repoRoot "run\server.properties"
        RconPort = 25575
    }
    neoforge = @{
        ServerRoot = Join-Path $repoRoot "neoforge\run"
        ConfigPath = Join-Path $repoRoot "neoforge\run\config\meowantixray.yml"
        PropertiesPath = Join-Path $repoRoot "neoforge\run\server.properties"
        RconPort = 25576
    }
}

foreach ($loader in $Loaders) {
    $setting = $settings[$loader]
    $properties = Get-PropertiesFile $setting.PropertiesPath
    $password = $properties["rcon.password"]
    if ([string]::IsNullOrWhiteSpace($password)) {
        throw "Missing rcon.password for $loader"
    }

    foreach ($queueSize in $QueueSizes) {
        Write-Host "=== $loader queue=$queueSize clients=$Clients duration=${DurationSeconds}s ==="
        Set-QueueSize $setting.ConfigPath $queueSize
        $server = $null
        try {
            $logBase = Join-Path $resultsDirectory "server-$loader-q$queueSize"
            $server = Start-TestServer $loader $logBase
            & powershell -NoProfile -ExecutionPolicy Bypass -File `
                (Join-Path $repoRoot "tools\capture-pressure-sample.ps1") `
                -Loader $loader `
                -Clients $Clients `
                -DurationSeconds $DurationSeconds `
                -ExpectedQueueSize $queueSize `
                -RconPassword $password
            if ($LASTEXITCODE -ne 0) {
                throw "Pressure collector failed with exit code $LASTEXITCODE"
            }
        } finally {
            if ($null -ne $server) {
                Stop-TestServer $loader $setting.RconPort $password $server
            }
        }
    }
}

Write-Host "Pressure matrix complete."
