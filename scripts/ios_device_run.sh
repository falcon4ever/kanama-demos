#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KANAMA_ROOT="${KANAMA_ROOT:-"$ROOT_DIR/../kanama"}"
XCODE_DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"

usage() {
  cat <<'EOF'
usage: scripts/ios_device_run.sh /path/to/godot /path/to/demo bundle.id AppName [/path/to/output-dir]

Installs the Kanama iOS addon into one demo, exports the Godot iOS Xcode
project, builds it with Xcode, installs it on a connected physical iOS device,
and launches it.

Required environment:
  KANAMA_IOS_DEVICE=<device udid>
  KANAMA_IOS_TEAM=<Apple development team id>

Optional environment:
  KANAMA_ROOT=/path/to/kanama
  DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
  KANAMA_IOS_RUN_STAGE=all|build|launch
      all    (default) build, install and launch.
      build  install the addon, export and build the app; stop before touching the device.
             Lets a caller build several demos concurrently (one KANAMA_ROOT each) and
             serialize the device steps afterwards.
      launch install and launch the app a previous `build` stage left in the output dir.
  KANAMA_IOS_RUN_GRADLE_ARGS="..."  extra arguments for the installIosAddon Gradle call
      (e.g. --no-daemon -Pkotlin.compiler.execution.strategy=in-process for concurrent builds).
EOF
}

if [[ $# -lt 4 || $# -gt 5 ]]; then
  usage
  exit 2
fi

GODOT_BIN="$1"
DEMO_DIR="$2"
BUNDLE_ID="$3"
APP_NAME="$4"
OUTPUT_DIR="${5:-/tmp/kanama-ios-demos/$APP_NAME}"
DEVICE_ID="${KANAMA_IOS_DEVICE:-}"
DEVELOPMENT_TEAM="${KANAMA_IOS_TEAM:-}"

if [[ -z "$DEVICE_ID" ]]; then
  echo "[ios_device_run] KANAMA_IOS_DEVICE is required." >&2
  exit 2
fi
if [[ -z "$DEVELOPMENT_TEAM" ]]; then
  echo "[ios_device_run] KANAMA_IOS_TEAM is required." >&2
  exit 2
fi
if [[ ! -x "$GODOT_BIN" ]]; then
  echo "[ios_device_run] Godot binary is not executable: $GODOT_BIN" >&2
  exit 2
fi
if [[ ! -x "$KANAMA_ROOT/gradlew" ]]; then
  echo "[ios_device_run] Kanama Gradle wrapper is not executable: $KANAMA_ROOT/gradlew" >&2
  exit 2
fi
if [[ ! -d "$KANAMA_ROOT/ios-runtime" || ! -f "$KANAMA_ROOT/ios/include/kanama_ios.h" ]]; then
  echo "[ios_device_run] KANAMA_ROOT does not look like the Kanama runtime repo: $KANAMA_ROOT" >&2
  exit 2
fi
if [[ ! -d "$DEMO_DIR" ]]; then
  echo "[ios_device_run] Demo directory does not exist: $DEMO_DIR" >&2
  exit 2
fi

DEMO_DIR="$(cd "$DEMO_DIR" && pwd)"
OUTPUT_DIR="$(mkdir -p "$OUTPUT_DIR" && cd "$OUTPUT_DIR" && pwd)"
RUN_STAGE="${KANAMA_IOS_RUN_STAGE:-all}"
case "$RUN_STAGE" in
  all|build|launch) ;;
  *)
    echo "[ios_device_run] KANAMA_IOS_RUN_STAGE must be all, build or launch (got: $RUN_STAGE)." >&2
    exit 2
    ;;
esac
IPA_PATH="$OUTPUT_DIR/$APP_NAME.ipa"
XCODE_PROJECT="$OUTPUT_DIR/$APP_NAME.xcodeproj"
DERIVED_DATA_DIR="$OUTPUT_DIR/DerivedData"
APP_PATH="$DERIVED_DATA_DIR/Build/Products/Debug-iphoneos/$APP_NAME.app"

if [[ "$RUN_STAGE" != "launch" ]]; then
echo "[ios_device_run] installing Kanama iOS addon: $DEMO_DIR"
(
  cd "$KANAMA_ROOT"
  # shellcheck disable=SC2086 # KANAMA_IOS_RUN_GRADLE_ARGS is deliberately word-split.
  DEVELOPER_DIR="$XCODE_DEVELOPER_DIR" ./gradlew \
    ${KANAMA_IOS_RUN_GRADLE_ARGS:-} \
    installIosAddon \
    "-PkanamaIosProjectDir=$DEMO_DIR" \
    "-PkanamaIosProjectScriptsDir=$DEMO_DIR/kotlin-src" \
    "-PkanamaXcodeDeveloperDir=$XCODE_DEVELOPER_DIR"
)

echo "[ios_device_run] exporting Godot iOS project: $IPA_PATH"
"$GODOT_BIN" --headless --path "$DEMO_DIR" --export-debug iOS "$IPA_PATH"

if [[ ! -d "$XCODE_PROJECT" ]]; then
  echo "[ios_device_run] Expected Xcode project was not produced: $XCODE_PROJECT" >&2
  exit 1
fi

echo "[ios_device_run] building for device: $DEVICE_ID"
DEVELOPER_DIR="$XCODE_DEVELOPER_DIR" xcodebuild \
  -allowProvisioningUpdates \
  -project "$XCODE_PROJECT" \
  -scheme "$APP_NAME" \
  -configuration Debug \
  -sdk iphoneos \
  -destination "id=$DEVICE_ID" \
  -derivedDataPath "$DERIVED_DATA_DIR" \
  CODE_SIGNING_ALLOWED=YES \
  CODE_SIGN_STYLE=Automatic \
  DEVELOPMENT_TEAM="$DEVELOPMENT_TEAM" \
  build

if [[ ! -d "$APP_PATH" ]]; then
  echo "[ios_device_run] Expected built app was not produced: $APP_PATH" >&2
  exit 1
fi
fi  # RUN_STAGE != launch

if [[ "$RUN_STAGE" == "build" ]]; then
  echo "[ios_device_run] built app (stage=build, device untouched): $APP_PATH"
  echo "[ios_device_run] PASS"
  exit 0
fi
if [[ ! -d "$APP_PATH" ]]; then
  echo "[ios_device_run] stage=launch but no built app at: $APP_PATH (run the build stage first)" >&2
  exit 1
fi

echo "[ios_device_run] installing app: $BUNDLE_ID"
DEVELOPER_DIR="$XCODE_DEVELOPER_DIR" xcrun devicectl device install app --device "$DEVICE_ID" "$APP_PATH"

echo "[ios_device_run] launching app: $BUNDLE_ID"
DEVELOPER_DIR="$XCODE_DEVELOPER_DIR" xcrun devicectl device process launch \
  --device "$DEVICE_ID" \
  --terminate-existing \
  "$BUNDLE_ID"

echo "[ios_device_run] PASS"
