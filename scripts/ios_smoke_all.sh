#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KANAMA_ROOT="${KANAMA_ROOT:-"$ROOT_DIR/../kanama"}"

usage() {
  cat <<'EOF'
usage: scripts/ios_smoke_all.sh /path/to/godot [/path/to/output-dir]

Runs the experimental Kanama iOS smoke (probe + full export) for all
iOS-enabled demo ports on a connected physical iOS device.

Required environment:
  KANAMA_IOS_DEVICE=<device udid>
  KANAMA_IOS_TEAM=<Apple development team id>

Optional environment:
  KANAMA_ROOT=/path/to/kanama
  DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
  KANAMA_IOS_SKIP_PROBES=1   Skip ios_visual_smoke.sh probe validation.
EOF
}

if [[ $# -lt 1 || $# -gt 2 ]]; then
  usage
  exit 2
fi

GODOT_BIN="$1"
OUTPUT_DIR="${2:-/tmp/kanama-ios-smokes}"
SMOKE_SCRIPT="$KANAMA_ROOT/scripts/ios_visual_smoke.sh"
DEVICE_RUN_SCRIPT="$ROOT_DIR/scripts/ios_device_run.sh"

if [[ ! -x "$GODOT_BIN" ]]; then
  echo "[ios_smoke_all] Godot binary is not executable: $GODOT_BIN" >&2
  exit 2
fi
if [[ ! -x "$SMOKE_SCRIPT" ]]; then
  echo "[ios_smoke_all] Kanama iOS smoke script is not executable: $SMOKE_SCRIPT" >&2
  exit 2
fi
if [[ ! -x "$DEVICE_RUN_SCRIPT" ]]; then
  echo "[ios_smoke_all] ios_device_run.sh is not executable: $DEVICE_RUN_SCRIPT" >&2
  exit 2
fi
if [[ -z "${KANAMA_IOS_DEVICE:-}" ]]; then
  echo "[ios_smoke_all] KANAMA_IOS_DEVICE is required (physical device UDID)." >&2
  exit 2
fi
if [[ -z "${KANAMA_IOS_TEAM:-}" ]]; then
  echo "[ios_smoke_all] KANAMA_IOS_TEAM is required (Apple development team id)." >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

# slug|folder|bundle_id|app_name|probe_flag
demos=(
  "bunnymark|Bunnymark|net.multigesture.kanama.bunnymark|KanamaBunnymark|--kanama-bunnymark-probe"
  "match3|Starter-Kit-Match3|net.multigesture.kanama.match3|KanamaMatch3|--kanama-match3-probe"
  "platformer3d|Starter-Kit-3D-Platformer|net.multigesture.kanama.platformer3d|KanamaPlatformer3d|--kanama-platformer3d-probe"
  "dodge|godot-demo-2d-dodge-the-creeps|net.multigesture.kanama.dodge|KanamaDodge|--kanama-dodge-probe"
  "squash|godot-demo-3d-squash-the-creeps|net.multigesture.kanama.squash3d|KanamaSquash|--kanama-squash-probe"
  # "fps|Starter-Kit-FPS|net.multigesture.kanama.fps|KanamaFPS|--kanama-fps-probe"  # Blocked: missing iOS wrapper types (AnimatedSprite3D, RayCast3D, OS, etc.)
  "racing|Starter-Kit-Racing|net.multigesture.kanama.racing|KanamaRacing|--kanama-racing-probe"
  "character|godot-4-3d-character-controller-tutorial|net.multigesture.kanama.charactercontroller|KanamaCharacterController|--kanama-character-probe"
  "thirdperson|godot-4-3d-third-person-controller|net.multigesture.kanama.thirdperson|KanamaThirdPerson|--kanama-thirdperson-probe"
)

for demo in "${demos[@]}"; do
  IFS="|" read -r slug folder bundle_id app_name probe_flag <<<"$demo"
  demo_dir="$ROOT_DIR/$folder"

  echo "[ios_smoke_all] start: $folder"

  if [[ "${KANAMA_IOS_SKIP_PROBES:-0}" != "1" ]]; then
    echo "[ios_smoke_all] probe: $folder ($probe_flag)"
    "$SMOKE_SCRIPT" \
      --godot "$GODOT_BIN" \
      --physical-device \
      "$probe_flag"
  fi

  echo "[ios_smoke_all] device run: $folder"
  "$DEVICE_RUN_SCRIPT" "$GODOT_BIN" "$demo_dir" "$bundle_id" "$app_name" "$OUTPUT_DIR/$slug"
  echo "[ios_smoke_all] pass: $folder"
done

echo "[ios_smoke_all] PASS"