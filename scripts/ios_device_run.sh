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
  KANAMA_IOS_CONSOLE_SECONDS=N  after launching, wait for the runtime's first [kanama][ios] line
      (bounded by KANAMA_IOS_LAUNCH_TIMEOUT, default 120 s), then stream the device console for N more seconds
      (devicectl --console) into <output-dir>/console.log and FAIL when it shows a crash
      signature (KANAMA_IOS_CONSOLE_FAIL_PATTERN, default: app terminated by a signal / FATAL)
      or when no `[kanama][ios]` line arrived in the window. 0 (default): launch-only, as before.
      Stopping the stream terminates the app (devicectl sends SIGTERM); the verdict is taken from
      the log as it stood before that.
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

CONSOLE_SECONDS="${KANAMA_IOS_CONSOLE_SECONDS:-0}"
if [[ ! "$CONSOLE_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "[ios_device_run] KANAMA_IOS_CONSOLE_SECONDS must be a non-negative integer (got: $CONSOLE_SECONDS)." >&2
  exit 2
fi
CONSOLE_FAIL_PATTERN="${KANAMA_IOS_CONSOLE_FAIL_PATTERN:-App terminated due to signal|FATAL}"

echo "[ios_device_run] launching app: $BUNDLE_ID"
if [[ "$CONSOLE_SECONDS" -eq 0 ]]; then
  DEVELOPER_DIR="$XCODE_DEVELOPER_DIR" xcrun devicectl device process launch \
    --device "$DEVICE_ID" \
    --terminate-existing \
    "$BUNDLE_ID"
else
  # Task 105: `--console` streams the device's stdout/stderr to the host and blocks until the app
  # exits (the demos never self-quit), so run it in the background, watch the log for the window,
  # then stop the stream; the app keeps running. A crash inside the window shows up as devicectl's
  # "App terminated due to signal N" (or the runtime's own FATAL line) and fails the step; a
  # window with no `[kanama][ios]` line at all means the runtime never came up.
  CONSOLE_LOG="$OUTPUT_DIR/console.log"
  : >"$CONSOLE_LOG"
  DEVELOPER_DIR="$XCODE_DEVELOPER_DIR" xcrun devicectl device process launch \
    --device "$DEVICE_ID" \
    --terminate-existing \
    --console \
    "$BUNDLE_ID" \
    >>"$CONSOLE_LOG" 2>&1 &
  console_pid="$!"
  # Phase 1: wait for the runtime's first `[kanama][ios]` line, bounded by
  # KANAMA_IOS_LAUNCH_TIMEOUT (default 120 s, as the starter smoke does). A Forward+/Metal demo
  # on an iPhone 12 can sit 30+ s in renderer setup before the extension prints anything (FPS did,
  # on the first validation run), so the launch phase is not part of the watch window.
  # Phase 2: keep watching for CONSOLE_SECONDS after that first line. Either phase ends early on a
  # crash signature or when the stream ends (the app exited).
  launch_timeout="${KANAMA_IOS_LAUNCH_TIMEOUT:-120}"
  waited=0
  launched_at=""
  while :; do
    sleep 2
    waited=$((waited + 2))
    if grep -q -E "$CONSOLE_FAIL_PATTERN" "$CONSOLE_LOG" 2>/dev/null; then
      break
    fi
    if ! kill -0 "$console_pid" 2>/dev/null; then
      break  # the stream ended on its own: the app exited
    fi
    if [[ -z "$launched_at" ]] && grep -q '\[kanama\]\[ios\]' "$CONSOLE_LOG" 2>/dev/null; then
      launched_at="$waited"
      echo "[ios_device_run] console: runtime reported after ${launched_at}s; watching ${CONSOLE_SECONDS}s more"
    fi
    if [[ -z "$launched_at" ]]; then
      [[ "$waited" -ge "$launch_timeout" ]] && break
    else
      [[ $((waited - launched_at)) -ge "$CONSOLE_SECONDS" ]] && break
    fi
  done
  # Judge the log as it stood BEFORE the stream is stopped: stopping devicectl terminates the
  # app with SIGTERM and it then reports "App terminated due to signal 15." — our own doing, not
  # a crash (the first validation run failed every demo on exactly that line).
  console_lines="$(wc -l <"$CONSOLE_LOG" | tr -d ' ')"
  VERDICT_LOG="$OUTPUT_DIR/console.window.log"
  head -n "$console_lines" "$CONSOLE_LOG" >"$VERDICT_LOG"
  kill "$console_pid" >/dev/null 2>&1 || true
  wait "$console_pid" >/dev/null 2>&1 || true
  if grep -q -E "$CONSOLE_FAIL_PATTERN" "$VERDICT_LOG"; then
    echo "[ios_device_run] console: crash signature within ${waited}s (${console_lines} lines): $CONSOLE_LOG"
    grep -n -E "$CONSOLE_FAIL_PATTERN" "$VERDICT_LOG" | head -5 | sed 's/^/[ios_device_run]   /'
    echo "[ios_device_run] FAIL"
    exit 1
  fi
  if ! grep -q '\[kanama\]\[ios\]' "$VERDICT_LOG"; then
    echo "[ios_device_run] console: no [kanama][ios] line in ${waited}s (${console_lines} lines; launch timeout ${launch_timeout}s) — the runtime never reported: $CONSOLE_LOG"
    echo "[ios_device_run] FAIL"
    exit 1
  fi
  echo "[ios_device_run] console: ${console_lines} lines in ${waited}s, no crash signature: $CONSOLE_LOG"
fi

echo "[ios_device_run] PASS"
