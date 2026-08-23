# start-server.ps1
# Convenience script to build & run the Go backend from the REPOSITORY ROOT.
#
# Why this is needed:
#   `go.mod` lives under `server-go/`, not at the repo root. Running
#   `go build ./cmd/server` from the repository root fails with
#   "cannot find main module, but found .git/config ...". You must change
#   into `server-go\` before calling `go build` / `go run`.
#
# Usage (PowerShell):
#   From repository root:  powershell -ExecutionPolicy Bypass -File .\start-server.ps1
#   Or shorter (current shell):  .\start-server.ps1

$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ServerDir = Join-Path $RepoRoot 'server-go'
$ExePath   = Join-Path $ServerDir 'server.exe'

if (-not (Test-Path -LiteralPath $ServerDir)) {
    Write-Error "Server directory not found: $ServerDir"
    exit 1
}
if (-not (Test-Path -LiteralPath (Join-Path $ServerDir 'go.mod'))) {
    Write-Error "go.mod missing inside server-go directory — please restore it."
    exit 1
}

Write-Host "[1/2] Building server module..." -ForegroundColor Cyan
Push-Location $ServerDir
try {
    & go build -o server.exe ./cmd/server
    if ($LASTEXITCODE -ne 0) {
        Write-Error "go build failed (exit=$LASTEXITCODE)."
        exit $LASTEXITCODE
    }
    Write-Host "Build OK -> $ExePath" -ForegroundColor Green
} finally {
    Pop-Location
}

Write-Host "[2/2] Starting server..." -ForegroundColor Cyan
Write-Host "Running from: $ServerDir" -ForegroundColor DarkGray
Write-Host "Press Ctrl+C to stop." -ForegroundColor DarkGray
Push-Location $ServerDir
try {
    & .\server.exe
} finally {
    Pop-Location
}
