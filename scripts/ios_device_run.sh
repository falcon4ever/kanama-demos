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
  KANAMA_IOS_SMOKE_QUIT=1  launch with the demo's smoke switch (KANAMA_DEMO_SMOKE_QUIT=1 in the app's
      environment, via devicectl --environment-variables) so its kotlin-src/SmokeQuit.kt runs on the phone —
      spawn / damage / free / quit — inside the console window; the step then also REQUIRES the smoke's
      completion line (KANAMA_IOS_SMOKE_COMPLETE_PATTERN, default: [kanama:smoke] SmokeQuit complete).
      Ignored for demos without kotlin-src/SmokeQuit.kt. Needs KANAMA_IOS_CONSOLE_SECONDS > 0. (task 111)
  KANAMA_IOS_CRASH_REPORTS=1  (default when KANAMA_IOS_CONSOLE_SECONDS > 0) snapshot the device's crash
      logs before the launch and again after the window; a NEW <AppName>-*.ips fails the step even when the
      crash left no signature in the console, and the report is copied to <output-dir>/crashes/. (task 111)
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
# The iOS runtime sources live in `ios-runtime/` up to kanama 130e6b35 and in `src/iosMain/` from
# task 104 step 3 on (one KMP module); accept either, the C header is the constant.
if [[ ! -f "$KANAMA_ROOT/ios/include/kanama_ios.h" ]] ||
   [[ ! -d "$KANAMA_ROOT/ios-runtime" && ! -d "$KANAMA_ROOT/src/iosMain" ]]; then
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
  # Register the demo's kotlin-src for BOTH targets. -PkanamaIosProjectScriptsDir feeds the
  # Kotlin/Native runtime (KSP registrars); -PkanamaProjectScriptsDir compiles the same sources
  # into the DESKTOP scripts jar the export-time editor loads. Without the second one the editor
  # binds no Kotlin class to the demo's .kt scripts, reports no @ScriptProperty list, and Godot's
  # export (which instantiates and re-packs every scene when converting text resources to binary)
  # silently drops every scene-stored script property — Match3's tile_scene arrived null on the
  # phone and Main._ready aborted (task 106).
  DEVELOPER_DIR="$XCODE_DEVELOPER_DIR" ./gradlew \
    ${KANAMA_IOS_RUN_GRADLE_ARGS:-} \
    installIosAddon \
    "-PkanamaIosProjectDir=$DEMO_DIR" \
    "-PkanamaIosProjectScriptsDir=$DEMO_DIR/kotlin-src" \
    "-PkanamaProjectScriptsDir=$DEMO_DIR/kotlin-src" \
    "-PkanamaXcodeDeveloperDir=$XCODE_DEVELOPER_DIR"
)

# Godot caches each text->binary scene conversion under .godot/exported/ keyed by the source
# .tscn's md5 + mtime, so a conversion made while the desktop scripts jar was wrong (properties
# stripped) is reused by every later export until the .tscn itself changes. Drop the cache so this
# export converts from the current scripts.
rm -rf "$DEMO_DIR/.godot/exported"

EXPORT_LOG="$OUTPUT_DIR/export.log"
echo "[ios_device_run] exporting Godot iOS project: $IPA_PATH (log: $EXPORT_LOG)"
"$GODOT_BIN" --headless --path "$DEMO_DIR" --export-debug iOS "$IPA_PATH" 2>&1 | tee "$EXPORT_LOG"
export_status="${PIPESTATUS[0]}"
if [[ "$export_status" -ne 0 ]]; then
  echo "[ios_device_run] Godot export failed (exit $export_status)" >&2
  exit 1
fi
# The desktop runtime logs every .kt it loads during the export with the Kotlin class it bound.
# An empty class means the scripts jar does not contain that script: the exported scenes would
# carry none of its @ScriptProperty values, which is invisible until the app crashes on device.
if grep -q 'ResourceFormatLoader\._load bound kotlinClass= ' "$EXPORT_LOG"; then
  echo "[ios_device_run] export-time editor bound no Kotlin class to a project script:" >&2
  grep -B1 'ResourceFormatLoader\._load bound kotlinClass= ' "$EXPORT_LOG" | grep '_load path=' >&2 || true
  echo "[ios_device_run] scene-stored @ScriptProperty values would be dropped from this export; refusing to build it." >&2
  exit 1
fi
# Task 112: compare every converted scene with its source — a script property present in the
# .tscn but missing from the exported .scn fails the step, whatever dropped it. The check lives in
# kanama (shared with the starter smoke); a KANAMA_ROOT that predates it is warned about, not failed.
SCENE_PARITY_CHECK="$KANAMA_ROOT/scripts/check_exported_scene_properties.gd"
if [[ -f "$SCENE_PARITY_CHECK" ]]; then
  if ! "$GODOT_BIN" --headless --path "$DEMO_DIR" --script "$SCENE_PARITY_CHECK"; then
    echo "[ios_device_run] exported scenes lost script properties (see [check_exported_scenes] lines above); refusing to build it." >&2
    exit 1
  fi
else
  echo "[ios_device_run] WARNING: $SCENE_PARITY_CHECK not found in KANAMA_ROOT; skipping the exported-scene parity check (task 112)" >&2
fi

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

# Task 111: run the demo's smoke on the phone. Only meaningful with a console window (the smoke's
# completion line is read from it) and only for demos that ship kotlin-src/SmokeQuit.kt.
SMOKE_QUIT="${KANAMA_IOS_SMOKE_QUIT:-0}"
SMOKE_COMPLETE_PATTERN="${KANAMA_IOS_SMOKE_COMPLETE_PATTERN:-\[kanama:smoke\] SmokeQuit complete}"
smoke_active=0
launch_env_args=()
if [[ "$SMOKE_QUIT" == "1" ]]; then
  if [[ "$CONSOLE_SECONDS" -eq 0 ]]; then
    echo "[ios_device_run] KANAMA_IOS_SMOKE_QUIT=1 needs KANAMA_IOS_CONSOLE_SECONDS > 0 (the completion line is read from the console); ignoring." >&2
  elif [[ ! -f "$DEMO_DIR/kotlin-src/SmokeQuit.kt" ]]; then
    echo "[ios_device_run] smoke: $DEMO_DIR has no kotlin-src/SmokeQuit.kt; launch-only step"
  else
    smoke_active=1
    launch_env_args=(--environment-variables '{"KANAMA_DEMO_SMOKE_QUIT":"1"}')
    echo "[ios_device_run] smoke: launching with KANAMA_DEMO_SMOKE_QUIT=1; requiring '$SMOKE_COMPLETE_PATTERN'"
  fi
fi

# Task 111: crash reports. The console window sees a crash only while it is open and only when
# devicectl prints the signal line; the device's crash logs see every crash of the process.
CRASH_REPORTS="${KANAMA_IOS_CRASH_REPORTS:-}"
if [[ -z "$CRASH_REPORTS" ]]; then
  if [[ "$CONSOLE_SECONDS" -gt 0 ]]; then CRASH_REPORTS=1; else CRASH_REPORTS=0; fi
fi
snapshot_crash_reports() {
  # $1 = destination dir; lists this app's .ips names (one per line) on stdout, empty on copy failure.
  local dest="$1"
  rm -rf "$dest"
  mkdir -p "$dest"
  if DEVELOPER_DIR="$XCODE_DEVELOPER_DIR" xcrun devicectl device copy from --device "$DEVICE_ID" \
      --domain-type systemCrashLogs --source . --destination "$dest" >/dev/null 2>&1; then
    local f
    for f in "$dest/$APP_NAME"-*.ips "$dest/$APP_NAME".*.ips; do
      if [[ -e "$f" ]]; then basename "$f"; fi
    done
  else
    echo "[ios_device_run] WARNING: could not copy the device's crash logs (before/after diff disabled for this step)" >&2
  fi
  return 0  # a no-match glob must not fail the caller under set -e
}
crashes_before=""
if [[ "$CRASH_REPORTS" == "1" ]]; then
  crashes_before="$(snapshot_crash_reports "$OUTPUT_DIR/crashes.before")"
fi

echo "[ios_device_run] launching app: $BUNDLE_ID"
if [[ "$CONSOLE_SECONDS" -eq 0 ]]; then
  DEVELOPER_DIR="$XCODE_DEVELOPER_DIR" xcrun devicectl device process launch \
    --device "$DEVICE_ID" \
    --terminate-existing \
    "${launch_env_args[@]}" \
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
    "${launch_env_args[@]}" \
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
  if [[ "$smoke_active" -eq 1 ]] && grep -q -E "$SMOKE_COMPLETE_PATTERN" "$CONSOLE_LOG" 2>/dev/null; then
    # Give the quit a moment so a crash inside SceneTree.quit() / teardown still lands in the log.
    sleep 3
  fi
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
  if grep -q -E "failed to launch|could not be, unlocked|RequestDenied" "$VERDICT_LOG"; then
    echo "[ios_device_run] console: the launch was refused (device locked?) — $(grep -m1 -o 'Unable to launch[^(]*' "$VERDICT_LOG" | head -1): $CONSOLE_LOG"
    echo "[ios_device_run] FAIL"
    exit 1
  fi
  if ! grep -q '\[kanama\]\[ios\]' "$VERDICT_LOG"; then
    echo "[ios_device_run] console: no [kanama][ios] line in ${waited}s (${console_lines} lines; launch timeout ${launch_timeout}s) — the runtime never reported: $CONSOLE_LOG"
    echo "[ios_device_run] FAIL"
    exit 1
  fi
  if [[ "$smoke_active" -eq 1 ]]; then
    if grep -q -E "$SMOKE_COMPLETE_PATTERN" "$VERDICT_LOG"; then
      echo "[ios_device_run] smoke: completion line seen ($(grep -c -E "$SMOKE_COMPLETE_PATTERN" "$VERDICT_LOG")x)"
    else
      echo "[ios_device_run] smoke: SmokeQuit ran but its completion line never appeared in ${waited}s — the smoke did not finish (crash without signature, hang, or the env var did not reach the app): $CONSOLE_LOG"
      echo "[ios_device_run] FAIL"
      exit 1
    fi
  fi
  echo "[ios_device_run] console: ${console_lines} lines in ${waited}s, no crash signature: $CONSOLE_LOG"
fi

if [[ "$CRASH_REPORTS" == "1" ]]; then
  crashes_after="$(snapshot_crash_reports "$OUTPUT_DIR/crashes.after")"
  new_crashes="$(comm -13 <(printf '%s\n' "$crashes_before" | sort -u) <(printf '%s\n' "$crashes_after" | sort -u) | sed '/^$/d')"
  if [[ -n "$new_crashes" ]]; then
    mkdir -p "$OUTPUT_DIR/crashes"
    while IFS= read -r ips; do
      cp "$OUTPUT_DIR/crashes.after/$ips" "$OUTPUT_DIR/crashes/" 2>/dev/null || true
      echo "[ios_device_run] crash report: NEW $ips -> $OUTPUT_DIR/crashes/$ips"
    done <<<"$new_crashes"
    echo "[ios_device_run] FAIL"
    exit 1
  fi
  echo "[ios_device_run] crash reports: no new $APP_NAME report on the device"
  rm -rf "$OUTPUT_DIR/crashes.before" "$OUTPUT_DIR/crashes.after"
fi

echo "[ios_device_run] PASS"
