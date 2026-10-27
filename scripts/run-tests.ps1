# Library Management System — test runner (PowerShell, no Maven)
#
# Mirrors the standalone-JUnit workflow described in README §3:
#   1. Download junit-platform-console-standalone and postgresql JDBC into ./lib (once).
#   2. Compile src/main/java -> build/main
#   3. Compile src/test/java  -> build/test
#   4. Run every JUnit test via junit-platform-console-standalone --scan-classpath.
#
# Prerequisites:
#   - JDK 17+ on PATH (javac + java)
#   - PostgreSQL 16 reachable at the URL/credentials below
#   - library_test database initialized with src/main/resources/db/schema.sql
#
# Environment variables (override on the command line):
#   $env:DB_URL      = 'jdbc:postgresql://127.0.0.1:5432/library_test'
#   $env:DB_USER     = 'library_app'
#   $env:DB_PASSWORD = '<your-password>'
#
# Usage:
#   .\scripts\run-tests.ps1                     # run ALL tests
#   .\scripts\run-tests.ps1 -TestClassFilter '*TransactionIsolation*'   # run a subset (glob)
#
# TestClassFilter is a glob pattern; it is converted to a JUnit class-name regex internally.
# Exit code is non-zero if any test fails.

[CmdletBinding()]
param(
    [string]$TestClassFilter = '*',
    [string]$LibDir          = (Join-Path $PSScriptRoot '..\lib'),
    [string]$BuildDir        = (Join-Path $PSScriptRoot '..\build'),
    [string]$JunitVersion    = '1.14.4',
    [string]$PostgresVersion = '42.7.14'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$MainSrc  = Join-Path $RepoRoot 'src\main\java'
$TestSrc  = Join-Path $RepoRoot 'src\test\java'
$MainOut  = Join-Path $BuildDir 'main'
$TestOut  = Join-Path $BuildDir 'test'

# --- 1. Ensure lib/ has the two required JARs --------------------------------------

$JunitJar    = Join-Path $LibDir "junit-platform-console-standalone-$JunitVersion.jar"
$PostgresJar = Join-Path $LibDir "postgresql-$PostgresVersion.jar"

if (-not (Test-Path $LibDir)) { New-Item -ItemType Directory -Path $LibDir | Out-Null }

if (-not (Test-Path $JunitJar)) {
    Write-Host "Downloading JUnit standalone $JunitVersion ..."
    $url = "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$JunitVersion/junit-platform-console-standalone-$JunitVersion.jar"
    Invoke-WebRequest -Uri $url -OutFile $JunitJar
}
if (-not (Test-Path $PostgresJar)) {
    Write-Host "Downloading PostgreSQL JDBC $PostgresVersion ..."
    $url = "https://jdbc.postgresql.org/download/postgresql-$PostgresVersion.jar"
    Invoke-WebRequest -Uri $url -OutFile $PostgresJar
}

# --- 2. Compile main sources -------------------------------------------------------

if (-not (Test-Path $MainOut)) { New-Item -ItemType Directory -Path $MainOut -Force | Out-Null }
Write-Host 'Compiling src/main/java ...'
$mainFiles = Get-ChildItem -Path $MainSrc -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }
& javac --release 17 -encoding UTF-8 -d $MainOut -cp "$PostgresJar" $mainFiles
if ($LASTEXITCODE -ne 0) { throw "Main compilation failed (exit $LASTEXITCODE)" }

# --- 3. Compile test sources -------------------------------------------------------

if (-not (Test-Path $TestOut)) { New-Item -ItemType Directory -Path $TestOut -Force | Out-Null }
Write-Host 'Compiling src/test/java ...'
$testFiles = Get-ChildItem -Path $TestSrc -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }
& javac --release 17 -encoding UTF-8 -d $TestOut -cp "$MainOut;$PostgresJar;$JunitJar" $testFiles
if ($LASTEXITCODE -ne 0) { throw "Test compilation failed (exit $LASTEXITCODE)" }

# --- 4. Run JUnit Platform Console -------------------------------------------------

Write-Host "Running JUnit tests (filter: $TestClassFilter) ..."
$classRegex = '^' + [regex]::Escape($TestClassFilter).Replace('\*', '.*') + '$'
$cp = "$MainOut;$TestOut;$PostgresJar;$JunitJar"
& java -jar $JunitJar execute -cp $cp --scan-classpath $TestOut `
    --disable-banner `
    -n $classRegex
$exitCode = $LASTEXITCODE

if ($exitCode -eq 0) {
    Write-Host ''
    Write-Host 'All tests PASSED.' -ForegroundColor Green
} else {
    Write-Host ''
    Write-Host "Test run FAILED (exit $exitCode)." -ForegroundColor Red
}
exit $exitCode
