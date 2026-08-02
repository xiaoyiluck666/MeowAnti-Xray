[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("fabric", "neoforge")]
    [string] $Loader,

    [ValidateRange(0, 1024)]
    [int] $QueueSize = 16
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$settings = if ($Loader -eq "fabric") {
        @{
        ConfigPath = Join-Path $repoRoot "run\config\meowantixray.yml"
        Port = 25565
        Task = ":runServer"
    }
} else {
    @{
        ConfigPath = Join-Path $repoRoot "neoforge\run\config\meowantixray.yml"
        Port = 25566
        Task = ":neoforge:runServer"
    }
}

$existing = Get-NetTCPConnection -LocalPort $settings.Port -State Listen -ErrorAction SilentlyContinue
if ($null -ne $existing) {
    throw "Port $($settings.Port) is already in use."
}

$config = [IO.File]::ReadAllText($settings.ConfigPath)
$matches = [regex]::Matches($config, '(?m)^(\s*)async-queue-size:\s*\d+(\s*)$')
if ($matches.Count -ne 1) {
    throw "Could not locate exactly one async-queue-size in $($settings.ConfigPath)"
}
$updated = [regex]::Replace(
    $config,
    '(?m)^(\s*async-queue-size:\s*)\d+(\s*)$',
    { param($match) $match.Groups[1].Value + $QueueSize + $match.Groups[2].Value },
    1
)
if ($updated -ne $config) {
    [IO.File]::WriteAllText($settings.ConfigPath, $updated, [Text.UTF8Encoding]::new($false))
}

$resultsDirectory = Join-Path $repoRoot "build\pressure-results"
New-Item -ItemType Directory -Path $resultsDirectory -Force | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$stdoutPath = Join-Path $resultsDirectory "server-$timestamp-$Loader-q$QueueSize.stdout.log"
$stderrPath = Join-Path $resultsDirectory "server-$timestamp-$Loader-q$QueueSize.stderr.log"
$process = Start-Process `
    -FilePath "cmd.exe" `
    -ArgumentList "/c .\gradlew.bat $($settings.Task) --console=plain" `
    -WorkingDirectory $repoRoot `
    -RedirectStandardOutput $stdoutPath `
    -RedirectStandardError $stderrPath `
    -WindowStyle Hidden `
    -PassThru

[pscustomobject]@{
    loader = $Loader
    queueSize = $QueueSize
    wrapperPid = $process.Id
    port = $settings.Port
    stdout = $stdoutPath
    stderr = $stderrPath
} | ConvertTo-Json
