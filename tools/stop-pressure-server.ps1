[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("fabric", "neoforge")]
    [string] $Loader
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$serverProcesses = Get-CimInstance Win32_Process |
    Where-Object {
        $_.Name -in @("java.exe", "cmd.exe") -and
        $_.CommandLine -like "*$repoRoot*" -and (
            $_.CommandLine -like "*runServer*" -or
            $_.CommandLine -like "*fabric.dli.config*" -or
            $_.CommandLine -like "*fml.modFolders*"
        )
    }
$parentIds = @($serverProcesses | Select-Object -ExpandProperty ParentProcessId -Unique)
$serverProcesses | ForEach-Object {
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
}

foreach ($parentId in $parentIds) {
    $parent = Get-CimInstance Win32_Process -Filter "ProcessId = $parentId" -ErrorAction SilentlyContinue
    if ($null -ne $parent -and $parent.Name -eq "java.exe" -and $parent.CommandLine -like "*GradleDaemon*") {
        Stop-Process -Id $parentId -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Stopped $Loader pressure server processes."
