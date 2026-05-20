#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KANAMA_ROOT="${KANAMA_ROOT:-"$ROOT_DIR/../kanama"}"

usage() {
  cat <<'EOF'
usage: scripts/android_smoke_all.sh /path/to/godot [/path/to/output-dir]

Runs the experimental Kanama Android smoke for all Android-enabled demo ports.

Required environment:
  ANDROID_HOME or ANDROID_SDK_ROOT

Optional environment:
  KANAMA_ROOT=/path/to/kanama
  ADB=/path/to/adb
  KANAMA_ANDROID_LAUNCH_WAIT=30
EOF
}

if [[ $# -lt 1 || $# -gt 2 ]]; then
  usage
  exit 2
fi

GODOT_BIN="$1"
OUTPUT_DIR="${2:-/tmp/kanama-android-smokes}"
SMOKE_SCRIPT="$KANAMA_ROOT/scripts/android_smoke.sh"

if [[ ! -x "$GODOT_BIN" ]]; then
  echo "[android_smoke_all] Godot binary is not executable: $GODOT_BIN" >&2
  exit 2
fi
if [[ ! -x "$SMOKE_SCRIPT" ]]; then
  echo "[android_smoke_all] Kanama Android smoke script is not executable: $SMOKE_SCRIPT" >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

demos=(
  "dodge|godot-demo-2d-dodge-the-creeps|net.multigesture.kanama.dodge"
  "platformer3d|Starter-Kit-3D-Platformer|net.multigesture.kanama.platformer3d"
  "match3|Starter-Kit-Match3|net.multigesture.kanama.match3"
  "squash3d|godot-demo-3d-squash-the-creeps|net.multigesture.kanama.squash3d"
  "fps|Starter-Kit-FPS|net.multigesture.kanama.fps"
  "racing|Starter-Kit-Racing|net.multigesture.kanama.racing"
  "charactercontroller|godot-4-3d-character-controller-tutorial|net.multigesture.kanama.charactercontroller"
  "thirdperson|godot-4-3d-third-person-controller|net.multigesture.kanama.thirdperson"
)

for demo in "${demos[@]}"; do
  IFS="|" read -r slug folder package_name <<<"$demo"
  demo_dir="$ROOT_DIR/$folder"
  apk_path="$OUTPUT_DIR/$slug.apk"
  log_path="$OUTPUT_DIR/$slug.log"
  screenshot_path="$OUTPUT_DIR/$slug.png"

  echo "[android_smoke_all] start: $folder"
  KANAMA_ANDROID_LOG="$log_path" \
    KANAMA_ANDROID_SCREENSHOT="$screenshot_path" \
    "$SMOKE_SCRIPT" "$GODOT_BIN" "$demo_dir" "$package_name" "$apk_path"
  echo "[android_smoke_all] pass: $folder"
done

echo "[android_smoke_all] PASS"
