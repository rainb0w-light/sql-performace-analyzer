#!/usr/bin/env bash
set -u

mode="${1:---all}"
failed=0

check_java() {
  if ! command -v java >/dev/null 2>&1; then
    echo "[FAIL] Java 21 is required"
    failed=1
    return
  fi
  version="$(java -version 2>&1 | sed -n '1p')"
  echo "[ OK ] $version"
}

check_postgres_gate() {
  if [[ -n "${SQL_ANALYZER_TEST_POSTGRES_JDBC_URL:-}" ]]; then
    if [[ "$SQL_ANALYZER_TEST_POSTGRES_JDBC_URL" != jdbc:postgresql:* ]]; then
      echo "[FAIL] SQL_ANALYZER_TEST_POSTGRES_JDBC_URL must use jdbc:postgresql:"
      failed=1
      return
    fi
    echo "[ OK ] external PostgreSQL test URL is configured"
    return
  fi
  if ! command -v docker >/dev/null 2>&1; then
    echo "[FAIL] Docker CLI is not installed (required for Testcontainers)"
    failed=1
    return
  fi
  if ! docker info >/dev/null 2>&1; then
    echo "[FAIL] Docker CLI exists, but no Docker daemon is reachable"
    failed=1
    return
  fi
  echo "[ OK ] Docker daemon is reachable"
}

check_ui_gate() {
  if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "[FAIL] runIde UI smoke currently requires macOS"
    failed=1
    return
  fi
  if ! command -v osascript >/dev/null 2>&1; then
    echo "[FAIL] osascript is unavailable"
    failed=1
    return
  fi
  if ! osascript -e 'tell application "System Events" to get name of every process whose background only is false' >/dev/null 2>&1; then
    echo "[FAIL] no accessible GUI session or Accessibility permission"
    failed=1
    return
  fi
  echo "[ OK ] macOS GUI session is accessible"
}

echo "SQL Performance Analyzer acceptance preflight ($mode)"
check_java
case "$mode" in
  --postgres) check_postgres_gate ;;
  --ui) check_ui_gate ;;
  --all) check_postgres_gate; check_ui_gate ;;
  *) echo "usage: $0 [--all|--postgres|--ui]"; exit 2 ;;
esac

if [[ "$failed" -ne 0 ]]; then
  echo "Preflight failed; install/start the missing environment before running the gate."
  exit 1
fi
echo "Preflight passed."
