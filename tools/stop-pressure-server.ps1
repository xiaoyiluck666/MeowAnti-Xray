[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("fabric", "neoforge")]
    [string] $Loader
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
Get-CimInstance Win32_Process |
    Where-Object {
        $_.Name -in @("java.exe", "cmd.exe") -and
        $_.CommandLine -like "*$repoRoot*" -and (
            $_.CommandLine -like "*runServer*" -or
            $_.CommandLine -like "*fabric.dli.config*" -or
            $_.CommandLine -like "*fml.modFolders*"
        )
    } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }

Write-Host "Stopped $Loader pressure server processes."
