#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ]; then
  cat >&2 <<'EOF'
usage: scripts/desktop_smoke_all.sh /path/to/godot

Runs desktop headless smoke validation for Kanama demo ports with opt-in smoke
quit scripts.
EOF
  exit 2
fi

GODOT_BIN="$1"
HOST_UNAME="$(uname -s)"

case "$HOST_UNAME" in
  MINGW*|MSYS*|CYGWIN*)
    if command -v cygpath >/dev/null 2>&1; then
      GODOT_BIN="$(cygpath -u "$GODOT_BIN")"
    fi
    ;;
esac

if [ ! -x "$GODOT_BIN" ]; then
  echo "[desktop_smoke_all] Godot binary is not executable: $GODOT_BIN" >&2
  exit 2
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${KANAMA_DESKTOP_SMOKE_LOG_DIR:-/tmp/kanama-desktop-smokes}"
DEMO_TIMEOUT_SECONDS="${KANAMA_DESKTOP_SMOKE_TIMEOUT_SECONDS:-180}"
mkdir -p "$LOG_DIR"

case "$HOST_UNAME" in
  Linux)
    export XDG_DATA_HOME="${XDG_DATA_HOME:-/tmp/kanama-godot-state-linux}"
    mkdir -p "$XDG_DATA_HOME"
    ;;
esac

assert_no_hard_log_errors() {
  local folder="$1"
  local phase="$2"
  local log_file="$3"

  if [ ! -f "$log_file" ]; then
    return
  fi

  local error_pattern='Condition "!is_inside_tree\(\)" is true|Failed loading resource:|Failed loading scene:|Parse Error:|Cannot open file|Unable to open file'
  if grep -Eq "$error_pattern" "$log_file"; then
    echo "[desktop_smoke_all] Godot logged hard errors during $phase: $folder" >&2
    echo "[desktop_smoke_all] log: $log_file" >&2
    grep -En "$error_pattern" "$log_file" >&2 || true
    exit 1
  fi
}

run_smoke() {
  local folder="$1"
  shift
  local project_path="$ROOT_DIR/$folder"
  local log_slug="${folder//[^A-Za-z0-9._-]/_}"
  local log_file="$LOG_DIR/$log_slug.log"
  local import_log_file="$LOG_DIR/$log_slug.import.log"
  local project_path_for_godot="$project_path"
  local log_file_for_godot="$log_file"
  local import_log_file_for_godot="$import_log_file"
  if [[ "$HOST_UNAME" == MINGW* || "$HOST_UNAME" == MSYS* || "$HOST_UNAME" == CYGWIN* ]]; then
    if command -v cygpath >/dev/null 2>&1; then
      project_path_for_godot="$(cygpath -m "$project_path")"
      log_file_for_godot="$(cygpath -m "$log_file")"
      import_log_file_for_godot="$(cygpath -m "$import_log_file")"
    fi
  fi
  echo "[desktop_smoke_all] start: $folder"
  if [[ "${KANAMA_DESKTOP_SMOKE_SKIP_IMPORT:-0}" != "1" ]]; then
    local import_command=(
      "$GODOT_BIN"
      --headless
      --import
      --path "$project_path_for_godot"
      --log-file "$import_log_file_for_godot"
    )
    if command -v timeout >/dev/null 2>&1; then
      timeout "$DEMO_TIMEOUT_SECONDS" "${import_command[@]}"
    else
      "${import_command[@]}"
    fi
    assert_no_hard_log_errors "$folder" "import" "$import_log_file"
  fi
  local command=(
    "$GODOT_BIN"
    --headless
    --rendering-driver opengl3
    --rendering-method gl_compatibility
    --path "$project_path_for_godot"
    --log-file "$log_file_for_godot"
    --verbose
  )
  if command -v timeout >/dev/null 2>&1; then
    env "$@" timeout "$DEMO_TIMEOUT_SECONDS" "${command[@]}"
  else
    env "$@" "${command[@]}"
  fi
  assert_no_hard_log_errors "$folder" "runtime" "$log_file"
  echo "[desktop_smoke_all] pass: $folder"
}

run_smoke "Starter-Kit-3D-Platformer" KANAMA_DEMO_SMOKE_QUIT=1
run_smoke "Starter-Kit-Match3" KANAMA_DEMO_SMOKE_QUIT=1
run_smoke "Starter-Kit-FPS" KANAMA_FPS_SMOKE=1
run_smoke "Starter-Kit-Racing" KANAMA_RACING_SMOKE=1
run_smoke "Starter-Kit-City-Builder" KANAMA_CITY_BUILDER_SMOKE=1
run_smoke "godot-demo-2d-dodge-the-creeps" KANAMA_DEMO_SMOKE_QUIT=1
run_smoke "godot-demo-3d-squash-the-creeps" KANAMA_DEMO_SMOKE_QUIT=1
run_smoke "godot-4-3d-character-controller-tutorial" KANAMA_DEMO_SMOKE_QUIT=1
run_smoke "godot-4-3d-third-person-controller" KANAMA_DEMO_SMOKE_QUIT=1

echo "[desktop_smoke_all] PASS"
