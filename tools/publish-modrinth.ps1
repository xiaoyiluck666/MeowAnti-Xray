#requires -Version 5.1
<#
.SYNOPSIS
Builds, verifies, and optionally uploads Meow Anti-Xray release jars to Modrinth.

.DESCRIPTION
Default mode is a dry run. It builds the Fabric and NeoForge release jars,
checks jar metadata, checks whether the target Modrinth versions already exist,
and prints the upload plan.

Use -Upload to create the two Modrinth versions. The token is read from
MODRINTH_TOKEN or MODRINTH_API_TOKEN in Process, User, or Machine environment
scope. The token is never written to disk by this script.
#>

[CmdletBinding()]
param(
    [switch] $Upload,
    [switch] $SkipBuild,
    [string] $Version,
    [string[]] $GameVersions = @("26.1", "26.1.1", "26.1.2"),
    [string] $ProjectId = "8pl8obwY",
    [string] $ProjectSlug = "meowanti-xray",
    [string] $FabricApiProjectId = "P7dR8mSH",
    [string] $DefaultJavaHome = "C:\Program Files\Java\jdk-25.0.2"
)

$ErrorActionPreference = "Stop"
$UserAgent = "MeowAntiXrayReleaseHelper/1.0 (xiaoyiluck666)"

function Get-RepoRoot {
    $scriptPath = Split-Path -Parent $PSCommandPath
    return (Resolve-Path (Join-Path $scriptPath "..")).Path
}

function Read-GradleProperties {
    param([string] $Path)

    $properties = @{}
    Get-Content -Path $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith("#")) {
            return
        }
        $parts = $line.Split("=", 2)
        if ($parts.Length -eq 2) {
            $properties[$parts[0].Trim()] = $parts[1].Trim()
        }
    }
    return $properties
}

function Get-EnvSecret {
    param([string[]] $Names)

    foreach ($name in $Names) {
        foreach ($scope in @("Process", "User", "Machine")) {
            $value = [Environment]::GetEnvironmentVariable($name, $scope)
            if (-not [string]::IsNullOrWhiteSpace($value)) {
                return [pscustomobject]@{
                    Name = $name
                    Scope = $scope
                    Value = $value
                }
            }
        }
    }
    return $null
}

function Invoke-GradleRelease {
    param([string] $Root, [string] $JavaHome)

    if (Test-Path (Join-Path $JavaHome "bin\java.exe")) {
        $env:JAVA_HOME = $JavaHome
        $env:Path = "$JavaHome\bin;$env:Path"
        Write-Host "Using JAVA_HOME=$JavaHome"
    }

    Push-Location $Root
    try {
        & .\gradlew.bat releaseAllLoaders --console=plain
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle releaseAllLoaders failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

function Read-ZipEntryText {
    param(
        [string] $JarPath,
        [string] $EntryName
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $JarPath))
    try {
        $entry = $zip.GetEntry($EntryName)
        if ($null -eq $entry) {
            throw "Missing zip entry '$EntryName' in $JarPath."
        }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try {
            return $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }
    } finally {
        $zip.Dispose()
    }
}

function Assert-FabricJar {
    param(
        [string] $JarPath,
        [string] $ExpectedVersion
    )

    $json = Read-ZipEntryText -JarPath $JarPath -EntryName "fabric.mod.json" | ConvertFrom-Json
    $entrypoints = @($json.entrypoints.main)
    if ($json.id -ne "meowantixray") {
        throw "Fabric metadata id mismatch: '$($json.id)'."
    }
    if ($json.version -ne $ExpectedVersion) {
        throw "Fabric metadata version mismatch: '$($json.version)' != '$ExpectedVersion'."
    }
    if ($entrypoints -notcontains "com.meowantixray.fabric.MeowAntiXrayFabricMod") {
        throw "Fabric metadata entrypoint mismatch: $($entrypoints -join ', ')."
    }
    return [pscustomobject]@{
        Loader = "fabric"
        Id = $json.id
        Version = $json.version
        Entrypoint = ($entrypoints -join ",")
        File = (Split-Path -Leaf $JarPath)
    }
}

function Assert-NeoForgeJar {
    param(
        [string] $JarPath,
        [string] $ExpectedVersion
    )

    $toml = Read-ZipEntryText -JarPath $JarPath -EntryName "META-INF/neoforge.mods.toml"
    $modIdMatches = [regex]::Matches($toml, '(?m)^\s*modId\s*=\s*"([^"]+)"')
    $versionMatch = [regex]::Match($toml, '(?m)^\s*version\s*=\s*"([^"]+)"')
    $minecraftRangeMatch = [regex]::Match($toml, '(?ms)^\s*\[\[dependencies\.meowantixray\]\].*?modId\s*=\s*"minecraft".*?versionRange\s*=\s*"([^"]+)"')

    if ($modIdMatches.Count -eq 0 -or $modIdMatches[0].Groups[1].Value -ne "meowantixray") {
        throw "NeoForge metadata modId mismatch."
    }
    if (-not $versionMatch.Success -or $versionMatch.Groups[1].Value -ne $ExpectedVersion) {
        throw "NeoForge metadata version mismatch: '$($versionMatch.Groups[1].Value)' != '$ExpectedVersion'."
    }
    if (-not $minecraftRangeMatch.Success) {
        throw "NeoForge metadata minecraft dependency range was not found."
    }

    return [pscustomobject]@{
        Loader = "neoforge"
        Id = $modIdMatches[0].Groups[1].Value
        Version = $versionMatch.Groups[1].Value
        MinecraftRange = $minecraftRangeMatch.Groups[1].Value
        File = (Split-Path -Leaf $JarPath)
    }
}

function Get-ModrinthVersions {
    param([string] $Slug)

    $headers = @{ "User-Agent" = $UserAgent }
    return @(Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$Slug/version" -Headers $headers)
}

function New-VersionPayload {
    param(
        [string] $Name,
        [string] $VersionNumber,
        [string] $Loader,
        [string] $Changelog,
        [object[]] $Dependencies
    )

    return [ordered]@{
        project_id = $ProjectId
        name = $Name
        version_number = $VersionNumber
        changelog = $Changelog
        dependencies = $Dependencies
        game_versions = $GameVersions
        version_type = "release"
        loaders = @($Loader)
        featured = $false
        status = "listed"
        requested_status = "listed"
        file_parts = @("file")
        primary_file = "file"
    }
}

function Invoke-ModrinthUpload {
    param(
        [object] $Payload,
        [string] $JarPath,
        [string] $Token
    )

    $tempData = [System.IO.Path]::GetTempFileName()
    try {
        $Payload | ConvertTo-Json -Depth 20 | Set-Content -Path $tempData -Encoding UTF8
        $authHeader = "Authorization: $Token"
        $uaHeader = "User-Agent: $UserAgent"
        $response = & curl.exe --fail-with-body -sS -X POST "https://api.modrinth.com/v2/version" `
            -H $authHeader `
            -H $uaHeader `
            -F "data=<$tempData;type=application/json" `
            -F "file=@$JarPath;type=application/java-archive"
        if ($LASTEXITCODE -ne 0) {
            throw "curl upload failed with exit code $LASTEXITCODE."
        }
        return ($response | ConvertFrom-Json)
    } finally {
        Remove-Item -LiteralPath $tempData -Force -ErrorAction SilentlyContinue
    }
}

$root = Get-RepoRoot
$properties = Read-GradleProperties -Path (Join-Path $root "gradle.properties")
if ([string]::IsNullOrWhiteSpace($Version)) {
    $Version = $properties["mod_version"]
}
if ([string]::IsNullOrWhiteSpace($Version)) {
    throw "Version was not provided and mod_version was not found in gradle.properties."
}

$archivesBaseName = $properties["archives_base_name"]
if ([string]::IsNullOrWhiteSpace($archivesBaseName)) {
    $archivesBaseName = "meowantixray"
}

if (-not $SkipBuild) {
    Invoke-GradleRelease -Root $root -JavaHome $DefaultJavaHome
}

$releaseDir = Join-Path $root "build\release"
$fabricJar = Join-Path $releaseDir "$archivesBaseName-fabric-$Version.jar"
$neoforgeJar = Join-Path $releaseDir "$archivesBaseName-neoforge-$Version.jar"
foreach ($jar in @($fabricJar, $neoforgeJar)) {
    if (-not (Test-Path $jar)) {
        throw "Release jar not found: $jar"
    }
}

$fabricMetadata = Assert-FabricJar -JarPath $fabricJar -ExpectedVersion $Version
$neoforgeMetadata = Assert-NeoForgeJar -JarPath $neoforgeJar -ExpectedVersion $Version
Write-Host "Jar metadata checks passed:"
@($fabricMetadata, $neoforgeMetadata) | Format-Table -AutoSize | Out-String | Write-Host

$zhPath = Join-Path $root "changelogs\$Version.zh-CN.md"
$enPath = Join-Path $root "changelogs\$Version.en-US.md"
foreach ($path in @($zhPath, $enPath)) {
    if (-not (Test-Path $path)) {
        throw "Missing changelog file: $path"
    }
}
$changelog = ((Get-Content -Path $zhPath -Raw).Trim() + "`n`n---`n`n" + (Get-Content -Path $enPath -Raw).Trim())

$fabricVersionNumber = "$Version+fabric"
$neoforgeVersionNumber = "$Version+neoforge"
$existingVersions = Get-ModrinthVersions -Slug $ProjectSlug
$existingNumbers = @($existingVersions | ForEach-Object { $_.version_number })
$targetVersionNumbers = @($fabricVersionNumber, $neoforgeVersionNumber)
$duplicates = @($targetVersionNumbers | Where-Object { $existingNumbers -contains $_ })

$fabricPayload = New-VersionPayload `
    -Name "Meow Anti-Xray $Version (Fabric)" `
    -VersionNumber $fabricVersionNumber `
    -Loader "fabric" `
    -Changelog $changelog `
    -Dependencies @(@{ project_id = $FabricApiProjectId; dependency_type = "required" })
$neoforgePayload = New-VersionPayload `
    -Name "Meow Anti-Xray $Version (NeoForge)" `
    -VersionNumber $neoforgeVersionNumber `
    -Loader "neoforge" `
    -Changelog $changelog `
    -Dependencies @()

Write-Host "Modrinth release plan:"
@(
    [pscustomobject]@{ Version = $fabricVersionNumber; Loader = "fabric"; Jar = (Split-Path -Leaf $fabricJar); Dependencies = "Fabric API $FabricApiProjectId" },
    [pscustomobject]@{ Version = $neoforgeVersionNumber; Loader = "neoforge"; Jar = (Split-Path -Leaf $neoforgeJar); Dependencies = "" }
) | Format-Table -AutoSize | Out-String | Write-Host

if ($duplicates.Count -gt 0) {
    $message = "Modrinth already has version(s): $($duplicates -join ', ')"
    if ($Upload) {
        throw $message
    }
    Write-Warning "$message. Dry run completed; upload would be blocked."
    exit 0
}

if (-not $Upload) {
    Write-Host "Dry run completed. Re-run with -Upload to publish."
    exit 0
}

$secret = Get-EnvSecret -Names @("MODRINTH_TOKEN", "MODRINTH_API_TOKEN")
if ($null -eq $secret) {
    throw "Set MODRINTH_TOKEN or MODRINTH_API_TOKEN before using -Upload."
}
Write-Host "Using Modrinth token from $($secret.Name) ($($secret.Scope) scope)."

$fabricResult = Invoke-ModrinthUpload -Payload $fabricPayload -JarPath (Resolve-Path $fabricJar).Path -Token $secret.Value
$neoforgeResult = Invoke-ModrinthUpload -Payload $neoforgePayload -JarPath (Resolve-Path $neoforgeJar).Path -Token $secret.Value

Write-Host "Uploaded Modrinth versions:"
@($fabricResult, $neoforgeResult) | ForEach-Object {
    [pscustomobject]@{
        Name = $_.name
        Version = $_.version_number
        Id = $_.id
        Status = $_.status
        Loaders = ($_.loaders -join ",")
        GameVersions = ($_.game_versions -join ",")
        Files = ($_.files.filename -join ",")
    }
} | Format-Table -AutoSize | Out-String | Write-Host
