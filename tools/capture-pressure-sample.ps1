[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("fabric", "neoforge")]
    [string] $Loader,

    [ValidateRange(1, 200)]
    [int] $Clients = 8,

    [ValidateRange(1, 3600)]
    [int] $DurationSeconds = 90,

    [ValidateRange(0, 1024)]
    [int] $ExpectedQueueSize = 16,

    [string] $OutputDirectory,

    [string] $RconPassword = $env:MEOW_PRESSURE_RCON_PASSWORD,

    [switch] $AllowDirty
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repoRoot "build\pressure-results"
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)

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

function Get-JavaVersionText {
    $output = & java -version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "java -version failed with exit code $LASTEXITCODE"
    }
    return ($output -join "`n")
}

$gitStatus = (& git -C $repoRoot status --porcelain=v1 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) {
    throw "Could not read git status: $gitStatus"
}
if (-not $AllowDirty -and -not [string]::IsNullOrWhiteSpace($gitStatus)) {
    throw "Pressure baselines require a clean worktree. Commit changes or pass -AllowDirty for a non-release smoke sample."
}

$loaderSettings = if ($Loader -eq "fabric") {
    @{
        ServerRoot = Join-Path $repoRoot "run"
        MinecraftProfile = "26.3"
        DefaultPort = 25565
        DefaultRconPort = 25575
    }
} else {
    @{
        ServerRoot = Join-Path $repoRoot "neoforge\run"
        MinecraftProfile = "26.3-neoforge"
        DefaultPort = 25566
        DefaultRconPort = 25576
    }
}

$serverPropertiesPath = Join-Path $loaderSettings.ServerRoot "server.properties"
$configPath = Join-Path $loaderSettings.ServerRoot "config\meowantixray.yml"
foreach ($requiredPath in @($serverPropertiesPath, $configPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required local pressure-test file is missing: $requiredPath"
    }
}

$serverProperties = Get-PropertiesFile -Path $serverPropertiesPath
$projectProperties = Get-PropertiesFile -Path (Join-Path $repoRoot "gradle.properties")
$serverPort = if ($serverProperties.ContainsKey("server-port")) { [int] $serverProperties["server-port"] } else { $loaderSettings.DefaultPort }
$rconPort = if ($serverProperties.ContainsKey("rcon.port")) { [int] $serverProperties["rcon.port"] } else { $loaderSettings.DefaultRconPort }
if ([string]::IsNullOrWhiteSpace($RconPassword) -and $serverProperties.ContainsKey("rcon.password")) {
    $RconPassword = $serverProperties["rcon.password"]
}
if ([string]::IsNullOrWhiteSpace($RconPassword)) {
    throw "Set -RconPassword or MEOW_PRESSURE_RCON_PASSWORD, or configure rcon.password in the local server.properties."
}

$configText = Get-Content -LiteralPath $configPath -Raw
$queueMatch = [regex]::Match($configText, '(?m)^\s{2}async-queue-size:\s*(\d+)\s*$')
if (-not $queueMatch.Success) {
    throw "Could not find the global async-queue-size in $configPath"
}
$configuredQueueSize = [int] $queueMatch.Groups[1].Value
if ($configuredQueueSize -ne $ExpectedQueueSize) {
    throw "Expected async-queue-size=$ExpectedQueueSize but the local $Loader config contains $configuredQueueSize."
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$sampleName = "$timestamp-$Loader-q$ExpectedQueueSize-c$Clients"
$runnerJsonPath = Join-Path $OutputDirectory "$sampleName.runner.json"
$sampleJsonPath = Join-Path $OutputDirectory "$sampleName.json"
$logPath = Join-Path $OutputDirectory "$sampleName.log"
$runnerPath = Join-Path $repoRoot "tools\mc-2612-load-runner.js"

$runnerArguments = @(
    $runnerPath,
    "--mc-version", $loaderSettings.MinecraftProfile,
    "--clients", $Clients,
    "--duration", $DurationSeconds,
    "--connect-delay", 100,
    "--port", $serverPort,
    "--rcon-teleport",
    "--rcon-port", $rconPort,
    "--rcon-password", $RconPassword,
    "--gamemode", "spectator",
    "--teleport-interval", 2500,
    "--teleport-step", 192,
    "--teleport-y", 180,
    "--json-output", $runnerJsonPath
)

Push-Location $repoRoot
try {
    & node @runnerArguments 2>&1 | Tee-Object -FilePath $logPath
    $runnerExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $runnerJsonPath -PathType Leaf)) {
    throw "The runner did not produce structured output. See $logPath"
}
$runnerResult = Get-Content -LiteralPath $runnerJsonPath -Raw | ConvertFrom-Json
$activeQueueMarker = "queueSize=$ExpectedQueueSize"
$rconConfigProperty = if ($null -ne $runnerResult.rcon) { $runnerResult.rcon.PSObject.Properties["config"] } else { $null }
$activeQueueConfirmed = $null -ne $rconConfigProperty -and $rconConfigProperty.Value -like "*$activeQueueMarker*"
$profileText = if ($null -ne $runnerResult.rcon) { $runnerResult.rcon.profile } else { "" }
$profileMatch = [regex]::Match($profileText, 'inFlight=(\d+), availablePermits=(\d+), pressure=([^,]+), syncFallbackRatio=([0-9.]+)%')
$runtimeProfile = [ordered]@{
    inFlight = if ($profileMatch.Success) { [int] $profileMatch.Groups[1].Value } else { $null }
    availablePermits = if ($profileMatch.Success) { [int] $profileMatch.Groups[2].Value } else { $null }
    pressure = if ($profileMatch.Success) { $profileMatch.Groups[3].Value } else { $null }
    syncFallbackRatio = if ($profileMatch.Success) { [double] $profileMatch.Groups[4].Value } else { $null }
}

$commit = (& git -C $repoRoot rev-parse HEAD).Trim()
$cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
$computer = Get-CimInstance Win32_ComputerSystem
$serverJava = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -eq "java.exe" -and $_.CommandLine -like "*$($loaderSettings.ServerRoot)*"
} | Select-Object -First 1

$sample = [ordered]@{
    schemaVersion = 1
    capturedAt = (Get-Date).ToUniversalTime().ToString("o")
    git = [ordered]@{
        commit = $commit
        dirty = -not [string]::IsNullOrWhiteSpace($gitStatus)
    }
    target = [ordered]@{
        loader = $Loader
        modVersion = $projectProperties["mod_version"]
        minecraftVersion = $projectProperties["minecraft_version"]
        loaderVersion = if ($Loader -eq "fabric") { $projectProperties["loader_version"] } else { $projectProperties["neoforge_version"] }
        serverPort = $serverPort
        rconPort = $rconPort
        asyncQueueSize = $configuredQueueSize
        asyncWorkerThreads = ([regex]::Match($configText, '(?m)^\s{2}async-worker-threads:\s*(\d+)\s*$').Groups[1].Value -as [int])
        configSha256 = (Get-FileHash -LiteralPath $configPath -Algorithm SHA256).Hash.ToLowerInvariant()
        viewDistance = if ($serverProperties.ContainsKey("view-distance")) { [int] $serverProperties["view-distance"] } else { $null }
        simulationDistance = if ($serverProperties.ContainsKey("simulation-distance")) { [int] $serverProperties["simulation-distance"] } else { $null }
    }
    environment = [ordered]@{
        os = [Environment]::OSVersion.VersionString
        java = Get-JavaVersionText
        cpu = $cpu.Name
        logicalProcessors = $cpu.NumberOfLogicalProcessors
        totalMemoryBytes = [long] $computer.TotalPhysicalMemory
        serverCommandLine = if ($null -ne $serverJava) { $serverJava.CommandLine } else { $null }
    }
    runner = $runnerResult
    runtimeProfile = $runtimeProfile
    validation = [ordered]@{
        runnerExitCode = $runnerExitCode
        activeQueueConfirmed = $activeQueueConfirmed
    }
}

$sample | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $sampleJsonPath -Encoding utf8
Write-Host "Pressure sample: $sampleJsonPath"
Write-Host "Runner log:     $logPath"

if (-not $activeQueueConfirmed) {
    throw "RCON config output did not confirm $activeQueueMarker. Structured output was retained."
}
if ($runnerExitCode -ne 0) {
    throw "Pressure runner failed with exit code $runnerExitCode. Structured output was retained."
}
