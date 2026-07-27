#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_FILE="${TMPDIR:-/tmp}/sql-analyzer-runide-ui.log"
PID_FILE="${TMPDIR:-/tmp}/sql-analyzer-runide-ui.pid"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "UI smoke test currently supports macOS only." >&2
  exit 2
fi

if ! python3 - <<'PY'
import subprocess
import sys

try:
    subprocess.run(
        ["osascript", "-e", 'tell application "System Events" to get name of every process whose background only is false'],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        timeout=5,
        check=True,
    )
except Exception:
    sys.exit(1)
PY
then
  echo "No accessible macOS GUI session is available. Run this from a logged-in desktop session with Accessibility permission." >&2
  exit 2
fi

rm -f "$LOG_FILE" "$PID_FILE"
(
  cd "$ROOT_DIR"
  "$ROOT_DIR/../gradlew" runIde --no-daemon --console=plain >"$LOG_FILE" 2>&1
) &
echo $! > "$PID_FILE"

cleanup() {
  if [[ -f "$PID_FILE" ]]; then
    parent_pid="$(cat "$PID_FILE")"
    kill "$parent_pid" 2>/dev/null || true
    pkill -TERM -P "$parent_pid" 2>/dev/null || true
  fi
  # runIde forks the IntelliJ JVM; always clean only this plugin's isolated sandbox.
  pkill -TERM -f "$ROOT_DIR/build/idea-sandbox" 2>/dev/null || true
  sleep 1
  pkill -KILL -f "$ROOT_DIR/build/idea-sandbox" 2>/dev/null || true
  rm -f "$PID_FILE"
}
trap cleanup EXIT

for _ in $(seq 1 60); do
  if pgrep -f "$ROOT_DIR/build/idea-sandbox" >/dev/null 2>&1; then
    break
  fi
  if grep -q "Unable to detect graphics environment\|BUILD FAILED" "$LOG_FILE" 2>/dev/null; then
    cat "$LOG_FILE"
    exit 1
  fi
  sleep 2
done

if ! pgrep -f "$ROOT_DIR/build/idea-sandbox" >/dev/null 2>&1; then
  cat "$LOG_FILE"
  echo "Timed out waiting for runIde process." >&2
  exit 1
fi

osascript "$ROOT_DIR/scripts/run-ide-ui-smoke.applescript"
echo "IDE UI smoke test passed."
