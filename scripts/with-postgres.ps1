# Library Management System - run a command with a local PostgreSQL lifecycle.
#
# Starts PostgreSQL only when it is not already accepting connections, runs the
# supplied action, then stops PostgreSQL again if this script started it. If the
# database was already running before the action, it is left untouched.
#
# Usage:
#   .\scripts\with-postgres.ps1 { .\scripts\run-tests.ps1 }
#   .\scripts\with-postgres.ps1 { & "$env:CATALINA_HOME\bin\catalina.bat" run }
#
# Override the local paths with environment variables when needed:
#   $env:PG_BIN  = 'C:\path\to\pgsql\bin'
#   $env:PGDATA  = 'C:\path\to\pgdata'

[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0)]
    [scriptblock]$Action,

    [string]$PgBin = $(if ($env:PG_BIN) { $env:PG_BIN } else { Join-Path $env:USERPROFILE 'dev\pgsql-16.15\bin' }),

    [string]$PgData = $(if ($env:PGDATA) { $env:PGDATA } else { Join-Path $env:USERPROFILE 'dev\pgdata-library' }),

    [string]$PgHost = '127.0.0.1',
    [int]$PgPort = 5432,
    [int]$StartupTimeoutSeconds = 30,
    [int]$ShutdownTimeoutSeconds = 30
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3

$postgresExe  = Join-Path $PgBin 'postgres.exe'
$pgCtlExe     = Join-Path $PgBin 'pg_ctl.exe'
$pgIsReadyExe = Join-Path $PgBin 'pg_isready.exe'

foreach ($path in @($postgresExe, $pgCtlExe, $pgIsReadyExe)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "PostgreSQL binary not found: $path. Set PG_BIN to the PostgreSQL bin directory."
    }
}
if (-not (Test-Path -LiteralPath $PgData)) {
    throw "PostgreSQL data directory not found: $PgData. Set PGDATA to the cluster directory."
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$logDir   = Join-Path $repoRoot 'build\logs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$outLog = Join-Path $logDir 'postgres.out.log'
$errLog = Join-Path $logDir 'postgres.err.log'

function Test-PostgresReady {
    & $pgIsReadyExe -h $PgHost -p $PgPort -q *> $null
    return ($LASTEXITCODE -eq 0)
}

$startedHere = $false
$actionExit  = 0

try {
    if (Test-PostgresReady) {
        Write-Host "PostgreSQL is already running on ${PgHost}:${PgPort}; leaving it untouched."
    }
    else {
        Write-Host "Starting PostgreSQL from $PgData ..."
        $process = Start-Process -FilePath $postgresExe `
            -ArgumentList @('-D', $PgData) `
            -RedirectStandardOutput $outLog `
            -RedirectStandardError $errLog `
            -WindowStyle Hidden `
            -PassThru

        $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
        while ((Get-Date) -lt $deadline) {
            if (Test-PostgresReady) {
                $startedHere = $true
                break
            }
            if ($process.HasExited) {
                $details = (Get-Content -LiteralPath $errLog -Tail 20 -ErrorAction SilentlyContinue) -join [Environment]::NewLine
                throw "PostgreSQL exited during startup (exit code $($process.ExitCode)).`n$details"
            }
            Start-Sleep -Milliseconds 300
        }

        if (-not $startedHere) {
            & $pgCtlExe stop -D $PgData -m immediate -w -t 10 *> $null
            throw "PostgreSQL did not become ready on ${PgHost}:${PgPort} within $StartupTimeoutSeconds seconds. See $errLog."
        }

        Write-Host "PostgreSQL is ready on ${PgHost}:${PgPort}."
    }

    $global:LASTEXITCODE = 0
    & $Action
    $actionExit = $LASTEXITCODE
}
finally {
    if ($startedHere) {
        Write-Host 'Stopping PostgreSQL ...'
        & $pgCtlExe stop -D $PgData -m fast -w -t $ShutdownTimeoutSeconds
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "pg_ctl stop failed (exit $LASTEXITCODE). See the PostgreSQL log in $PgData."
        }
        else {
            Write-Host 'PostgreSQL stopped.'
        }
    }
}

exit $actionExit
