#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:---local}"

run_local() {
  echo "[1/4] backend tests and bootJar"
  (cd "$ROOT_DIR" && ./gradlew clean test bootJar --no-daemon --console=plain)

  echo "[2/4] IDEA plugin contract tests and packaging"
  (cd "$ROOT_DIR/idea-plugin" && ../gradlew clean check buildPlugin verifyPluginStructure --no-daemon --console=plain)

  echo "[3/4] legacy architecture scan"
  # Portable (no rg dependency): -I skips binaries, explicit paths avoid build/ and IDE caches.
  # NOTE: com.h2database was removed from the ban list by docs/cloud-code-next-goal.md §3:
  # H2 is now a sanctioned management database (dual-database neutral persistence layer),
  # guarded instead by the Repository Contract Tests and Schema parity tests.
  # JPA/Hibernate auto-DDL (JpaRepository) and mybatis-spring-boot-starter remain banned.
  if grep -RnIE 'OpenTUI|opentui|WebSocketMessageController|JpaRepository|spring-ai|mybatis-spring-boot-starter|SQLAnalysisOrchestrator' \
      "$ROOT_DIR/src" "$ROOT_DIR/idea-plugin/src" "$ROOT_DIR/idea-plugin/build.gradle" \
      "$ROOT_DIR/idea-plugin/settings.gradle" "$ROOT_DIR/docker" \
      "$ROOT_DIR/settings.gradle" "$ROOT_DIR/build.gradle"; then
    echo "legacy architecture references found" >&2
    return 1
  fi

  echo "[4/4] file and script checks"
  (cd "$ROOT_DIR" && git diff --check)
  bash -n "$ROOT_DIR/scripts/preflight.sh" "$ROOT_DIR/scripts/acceptance.sh" \
    "$ROOT_DIR/idea-plugin/scripts/run-ide-ui-smoke.sh"
  if command -v osacompile >/dev/null 2>&1; then
    osacompile -o "${TMPDIR:-/tmp}/sql-analyzer-run-ide-ui-smoke.scpt" \
      "$ROOT_DIR/idea-plugin/scripts/run-ide-ui-smoke.applescript"
  fi
  echo "local acceptance passed"
}

run_external() {
  echo "[external] environment preflight"
  (cd "$ROOT_DIR" && bash scripts/preflight.sh --all)
  echo "[external] PostgreSQL integration"
  (cd "$ROOT_DIR" && RUN_POSTGRES_INTEGRATION_TESTS=true ./gradlew test --no-daemon --console=plain)
  echo "[external] IntelliJ UI smoke"
  (cd "$ROOT_DIR/idea-plugin" && ../gradlew uiSmoke --no-daemon --console=plain)
  echo "external acceptance passed"
}

case "$MODE" in
  --local) run_local ;;
  --external) run_external ;;
  --all) run_local; run_external ;;
  *)
    echo "usage: scripts/acceptance.sh [--local|--external|--all]" >&2
    exit 2
    ;;
esac
