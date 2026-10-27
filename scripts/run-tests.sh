#!/usr/bin/env bash
# Library Management System — test runner (bash, no Maven)
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
#   DB_URL      jdbc URL                    default: jdbc:postgresql://127.0.0.1:5432/library_test
#   DB_USER     database user               default: library_app
#   DB_PASSWORD database password (required)
#
# Usage:
#   ./scripts/run-tests.sh                              # run ALL tests
#   ./scripts/run-tests.sh '*TransactionIsolation*'     # run a subset (glob)
#
# The filter is a glob pattern; it is converted to a JUnit class-name regex internally.
# Exit code is non-zero if any test fails.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_SRC="$REPO_ROOT/src/main/java"
TEST_SRC="$REPO_ROOT/src/test/java"
BUILD_DIR="$REPO_ROOT/build"
MAIN_OUT="$BUILD_DIR/main"
TEST_OUT="$BUILD_DIR/test"
LIB_DIR="$REPO_ROOT/lib"

JUNIT_VERSION='1.14.4'
POSTGRES_VERSION='42.7.14'
JUNIT_JAR="$LIB_DIR/junit-platform-console-standalone-$JUNIT_VERSION.jar"
POSTGRES_JAR="$LIB_DIR/postgresql-$POSTGRES_VERSION.jar"

TEST_CLASS_FILTER="${1:-*}"

# Convert a simple glob (only * is special) to a JUnit class-name regex.
glob_to_regex() {
    local glob="$1"
    local out=''
    local i ch
    for ((i = 0; i < ${#glob}; i++)); do
        ch="${glob:i:1}"
        case "$ch" in
            '*') out+='.*' ;;
            '.') out+='\.' ;;
            '\') out+='\\' ;;
            *) out+="$ch" ;;
        esac
    done
    printf '^%s$' "$out"
}
CLASS_REGEX="$(glob_to_regex "$TEST_CLASS_FILTER")"

# --- 1. Ensure lib/ has the two required JARs --------------------------------------

mkdir -p "$LIB_DIR"
if [[ ! -f "$JUNIT_JAR" ]]; then
    echo "Downloading JUnit standalone $JUNIT_VERSION ..."
    url="https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$JUNIT_VERSION/junit-platform-console-standalone-$JUNIT_VERSION.jar"
    curl -fL "$url" -o "$JUNIT_JAR"
fi
if [[ ! -f "$POSTGRES_JAR" ]]; then
    echo "Downloading PostgreSQL JDBC $POSTGRES_VERSION ..."
    url="https://jdbc.postgresql.org/download/postgresql-$POSTGRES_VERSION.jar"
    curl -fL "$url" -o "$POSTGRES_JAR"
fi

# --- 2. Compile main sources -------------------------------------------------------

mkdir -p "$MAIN_OUT"
echo 'Compiling src/main/java ...'
mapfile -t MAIN_FILES < <(find "$MAIN_SRC" -name '*.java')
javac --release 17 -encoding UTF-8 -d "$MAIN_OUT" -cp "$POSTGRES_JAR" "${MAIN_FILES[@]}"

# --- 3. Compile test sources -------------------------------------------------------

mkdir -p "$TEST_OUT"
echo 'Compiling src/test/java ...'
mapfile -t TEST_FILES < <(find "$TEST_SRC" -name '*.java')
javac --release 17 -encoding UTF-8 -d "$TEST_OUT" -cp "$MAIN_OUT:$POSTGRES_JAR:$JUNIT_JAR" "${TEST_FILES[@]}"

# --- 4. Run JUnit Platform Console -------------------------------------------------

echo "Running JUnit tests (filter: $TEST_CLASS_FILTER) ..."
CP="$MAIN_OUT:$TEST_OUT:$POSTGRES_JAR:$JUNIT_JAR"
java -jar "$JUNIT_JAR" execute \
    -cp "$CP" \
    --scan-classpath "$TEST_OUT" \
    --disable-banner \
    -n "$CLASS_REGEX"
exit_code=$?

echo ''
if [[ $exit_code -eq 0 ]]; then
    echo 'All tests PASSED.'
else
    echo "Test run FAILED (exit $exit_code)."
fi
exit $exit_code
