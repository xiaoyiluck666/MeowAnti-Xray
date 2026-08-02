[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("fabric", "neoforge")]
    [string] $Loader
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$marker = if ($Loader -eq "fabric") { "fabric.dli.config" } else { "fml.modFolders" }

Get-CimInstance Win32_Process |
    Where-Object {
        $_.Name -eq "java.exe" -and
        $_.CommandLine -like "*$repoRoot*" -and
        $_.CommandLine -like "*$marker*"
    } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }

Get-CimInstance Win32_Process |
    Where-Object {
        $_.Name -eq "java.exe" -and
        $_.CommandLine -like "*$repoRoot*" -and
        $_.CommandLine -like "*gradle-wrapper.jar*" -and
        $_.CommandLine -like "*runServer*"
    } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }

Write-Host "Stopped $Loader pressure server processes."
