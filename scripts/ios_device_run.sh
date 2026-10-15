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
      environment, via devicectl --environment-variables) so its kotlin-src/Smoke*.kt smoke runs on the phone —
      spawn / damage / free / quit — inside the console window; the step then also REQUIRES the smoke's
      completion line (KANAMA_IOS_SMOKE_COMPLETE_PATTERN, default: [kanama:smoke] SmokeQuit complete).
      Ignored for demos whose kotlin-src/Smoke*.kt does not read KANAMA_DEMO_SMOKE_QUIT. Needs KANAMA_IOS_CONSOLE_SECONDS > 0. (task 111)
  KANAMA_IOS_CRASH_REPORTS=1  (default when KANAMA_IOS_CONSOLE_SECONDS > 0) snapshot the device's crash
      logs before the launch and again after the window; a NEW <AppName>-*.ips fails the step even when the
      crash left no signature in the console, and the report is copied to <output-dir>/crashes/. (task 111)
  KANAMA_IOS_ENGINE_LOG=auto|1|0  after the console window, pull the app's engine errors from the device's unified log:
      Godot iOS logs through os_log (subsystem = the bundle id, category ERROR / SCRIPT ERROR / SHADER ERROR / WARNING /
      engine), which `devicectl --console` does not show. The runner fetches a log archive with `idevicesyslog archive`
      (libimobiledevice; no sudo, ~12 s) for the window, reads it with `log show --archive`, prints every ERROR /
      SHADER ERROR line, keeps only this app's error lines (<output-dir>/engine-errors.log; the archive itself, every
      app's log, is deleted on every path) and FAILS on any SCRIPT ERROR line that is not one of the runtime
      self-test's deliberate ones, and when the deliberate ones are not exactly the expected number (5 when the
      self-test ran). auto (default): on when KANAMA_IOS_CONSOLE_SECONDS > 0, idevicesyslog has the `archive` command
      and the device is attached over USB, otherwise a `SKIP: device engine-error check: <reason>` line; a capture
      that fails is a loud SKIP too. 1 requires it (each of those is a FAIL). 0 skips it. (kanama task 111 item 3)
      The engine-log verdict is taken after the crash-report check, so a failure of one never hides the other.
  KANAMA_IOS_CONSOLE_SECONDS=N  after launching, wait for the runtime's first [kanama][ios] line
      (bounded by KANAMA_IOS_LAUNCH_TIMEOUT, default 120 s), then stream the device console for N more seconds
      (devicectl --console) into <output-dir>/console.log and FAIL when it shows a crash
      signature (KANAMA_IOS_CONSOLE_FAIL_PATTERN, default: app terminated by a signal / FATAL)
      or when no `[kanama][ios]` line arrived in the window. 0 (default): launch-only, as before.
      The verdict is taken from the log as it stood when the window ended, then the stream is stopped
      and the runner terminates the app itself: it finds the app's pid(s) by executable path
      (`.../<AppName>.app/<AppName>`) in `devicectl device info processes` and runs
      `devicectl device process terminate --pid <pid>` (SIGKILL if one survives SIGTERM), logging
      each step; a cleanup problem is only a warning, never a failure. Stopping the stream alone
      does not end the app, and neither does the demo's `get_tree().quit()` on iOS (it stops the main
      loop but not the process; the 2026-10-02 runs left both demos on a grey screen). Launch-only
      runs (KANAMA_IOS_CONSOLE_SECONDS=0) leave the app running, as before. (119 item 41)
      The window also FAILS on any `[kanama][ios][c] FAULT ` line (the self-test's seven
      deliberate `[kanama][ios][c] FAULT-PROBE ` lines do not match), on any OBJECTCALLS SELFTEST
      summary line whose `faults=` and `expected=` disagree, and on a run whose self-test ran (the
      `PTRCALL SELFTEST MATRIX` line is there) but printed no OBJECTCALLS SELFTEST summary line
      (kanama task 124 — the iOS bridge reports every guarded early return instead of returning
      quietly).

Self-check:
  scripts/ios_device_run.sh --check-console-faults /path/to/console.log
      Run ONLY the task-124 fault check against an existing console log and exit 0/1. This is how
      the check's own red run is re-executed without a phone: copy a green console.log and add one
      FAULT line (anywhere, including between FAULT-PROBE lines), edit a summary line to faults=8
      expected=7, or delete the two summary lines — each must trip it.
  scripts/ios_device_run.sh --self-test-console-checks
      Run the console checks (runtime self-test verdict, FAULT lines, fault counts) against built-in fixture logs,
      including the one that reported exit 0 over `OBJECTCALLS SELFTEST FAIL:` and `2 failed`; no device. Exit 0/1.
      A console with no self-test text at all fails too (this runner builds the self-test in,
      kanama task 138 item 24); KANAMA_IOS_REQUIRE_SELFTEST=0 accepts one, for a KANAMA_ROOT that cannot.
  scripts/ios_device_run.sh --check-engine-errors /path/to/engine-errors.log bundle.id
      Run ONLY the engine-error verdict on a saved `log show --style compact` extract (the runner keeps
      <output-dir>/engine-errors.log) and exit 0/1: a `[bundle.id:SCRIPT ERROR]` line that is not one of the
      self-test's deliberate ones fails, and so does a deliberate count other than expected (5; 0 with
      KANAMA_IOS_REQUIRE_SELFTEST=0). No device.
  scripts/ios_device_run.sh --self-test-app-pids
      Run the app-pid parser (used to close the app after a run) against built-in samples of the
      `devicectl device info processes` output; no device needed. Exit 0/1.
  scripts/ios_device_run.sh --print-app-pids AppName /path/to/processes.json
      Print the pid(s) the cleanup would terminate for AppName from a saved
      `xcrun devicectl device info processes --device <udid> --json-output processes.json`
      (or its table output).
EOF
}

# kanama task 124 — the iOS bridge no longer fails quietly, and this step is what makes that
# visible from here. The C shim reports every guarded early return as
# `[kanama][ios][c] FAULT <entry>: <reason> <detail>` and counts it; a debug build's self-test
# makes exactly SEVEN deliberate faults (two bind probes and the five drained pending slots) and
# prints them as `faults=<N> expected=<N>` on both OBJECTCALLS SELFTEST summary lines. While it
# makes them it puts the shim's sink in probe mode, so those seven print as
# `[kanama][ios][c] FAULT-PROBE <entry>: ...` — the sink marks them on the line itself. So:
#
#   * ANY `[kanama][ios][c] FAULT ` line (with the space: FAULT-PROBE does not match) fails the
#     run — including one the summary count would have hidden, because the sink stops printing
#     after 64 while the count keeps counting. `null-bind`, the most common real fault, stays
#     fatal: the probes are told apart by the sink's own mark, never by a reason token;
#   * any summary line where faults != expected fails the run. This is what holds the probes to
#     exactly seven, and it also catches a real fault printed as FAULT-PROBE because another thread
#     raised it while probe mode was on (faults=8 expected=7);
#   * a run whose self-test RAN (the C ptrcall matrix printed `PTRCALL SELFTEST MATRIX`) but that
#     printed no summary line never reached the faults=/expected= comparison, so its silence is
#     not evidence of health: it fails too.
#
# Nothing here reads a line's meaning from its POSITION. The self-test's `fault-probes begin/end`
# markers are Kotlin println (stdout) and the sink writes stderr; `devicectl --console` merges the
# two streams without preserving their relative order, and on the first iPhone run all seven probe
# lines landed after the `end` marker. An earlier version of this check trusted FAULT lines
# between the markers and failed that healthy run; the markers are now for humans only.
#
# A summary line without `expected=` is an older runtime: expected is then 0, so any fault at all
# on such a build trips the check rather than passing unnoticed.
# A LITERAL marker, matched with awk's index() rather than as a regex: the bracket-heavy prefix is
# a minefield of character classes once it passes through `awk -v` escape processing (the first cut
# of this check silently matched nothing for exactly that reason, and its red run caught it).
FAULT_LINE_MARKER='[kanama][ios][c] FAULT '
# Printed by the C ptrcall matrix at the start of every debug self-test, before any Kotlin row.
SELFTEST_RAN_MARKER='PTRCALL SELFTEST MATRIX'

# Task 118 (found by a device run: step exit 0 with `OBJECTCALLS SELFTEST FAIL: ...` and `306 passed, 2 failed`
# on the console): the runtime self-tests' OWN verdict is part of this check, as in kanama's ios_visual_smoke.sh.
#   * any `SELFTEST FAIL:` line fails the run, with or without a parenthesised phase (`SELFTEST (frame 1) FAIL:`);
#   * any summary line (PTRCALL matrix or ObjectCalls, the early one, the final one and the `(frame 1)` one alike)
#     with a non-zero failed count fails the run;
#   * a console that shows the self-test ran must carry BOTH summaries, `PTRCALL SELFTEST MATRIX: N passed, 0 failed`
#     and `OBJECTCALLS SELFTEST: N passed, 0 failed`; a missing one means the self-test did not finish.
#   * and the frame-1 summary, `OBJECTCALLS SELFTEST (frame 1): N passed, 0 failed` (kanama task 138 item 24).
# Since kanama task 138 item 24 the self-tests are a Kanama test hook compiled only into a build made with
# -PkanamaIosSelfTest=true, which this runner passes (users' builds carry none), so they are REQUIRED by default: a
# console with no self-test text at all fails (the build lost the flag). KANAMA_IOS_REQUIRE_SELFTEST=0 goes back to
# the marker-based check (expected only when the console shows any self-test text), for a KANAMA_ROOT that cannot
# build them.
SELFTEST_ANY_RE='SELFTEST( MATRIX)?[:( ]'
REQUIRE_SELFTEST="${KANAMA_IOS_REQUIRE_SELFTEST:-1}"

check_selftest_verdict() {
  local log="$1" fail_lines bad_summaries any
  # justified: grep exits 1 when nothing matches, which is the clean case; the checks below read the captured text.
  # An optional parenthesised phase: the frame-1 phase prints `OBJECTCALLS SELFTEST (frame 1) FAIL:` and
  # `OBJECTCALLS SELFTEST (frame 1): N passed, M failed`, which the phase-less patterns let through (a device run
  # with a frame-1 failure printed PASS, kanama task 129 A).
  fail_lines="$(grep -E 'SELFTEST( \([^)]*\))? FAIL:' "$log" 2>/dev/null || true)"
  if [[ -n "$fail_lines" ]]; then
    echo "[ios_device_run] console: a runtime self-test reported a FAILURE: $log"
    printf '%s\n' "$fail_lines" | head -n 5 | sed 's/^/[ios_device_run]   /'
    return 1
  fi
  # justified: grep exits 1 when no summary has a failed count, which is the clean case.
  bad_summaries="$(grep -E 'SELFTEST( MATRIX| \([^)]*\))?: [0-9]+ passed, [1-9][0-9]* failed' "$log" 2>/dev/null || true)"
  if [[ -n "$bad_summaries" ]]; then
    echo "[ios_device_run] console: a runtime self-test summary has failures: $log"
    printf '%s\n' "$bad_summaries" | head -n 5 | sed 's/^/[ios_device_run]   /'
    return 1
  fi
  # justified: grep -c prints 0 and exits 1 when nothing matches, which is the answer, not an error.
  any="$(grep -c -E "$SELFTEST_ANY_RE" "$log" 2>/dev/null || true)"
  : "${any:=0}"
  if (( any > 0 || REQUIRE_SELFTEST == 1 )); then
    if ! grep -q -E 'PTRCALL SELFTEST MATRIX: [0-9]+ passed, 0 failed' "$log" 2>/dev/null; then  # justified: the grep status is the test
      echo "[ios_device_run] console: no 'PTRCALL SELFTEST MATRIX: N passed, 0 failed' summary line; the self-test did not finish (or did not run: a build without -PkanamaIosSelfTest=true has none): $log"
      return 1
    fi
    if ! grep -q -E 'OBJECTCALLS SELFTEST: [0-9]+ passed, 0 failed' "$log" 2>/dev/null; then  # justified: the grep status is the test
      echo "[ios_device_run] console: no 'OBJECTCALLS SELFTEST: N passed, 0 failed' summary line; the self-test did not finish (or did not run: a build without -PkanamaIosSelfTest=true has none): $log"
      return 1
    fi
    if ! grep -q -E 'OBJECTCALLS SELFTEST \(frame 1\): [0-9]+ passed, 0 failed' "$log" 2>/dev/null; then  # justified: the grep status is the test
      echo "[ios_device_run] console: no 'OBJECTCALLS SELFTEST (frame 1): N passed, 0 failed' summary line; the app never reached its first frame or the self-test did not finish: $log"
      return 1
    fi
  fi
  return 0
}

check_console_faults() {
  local log="$1"

  check_selftest_verdict "$log" || return 1

  local first
  # justified: awk exits non-zero only when the log is unreadable, and the callers have just written or checked it.
  first="$(awk -v m="$FAULT_LINE_MARKER" 'index($0, m) { print; exit }' "$log" 2>/dev/null || true)"
  if [[ -n "$first" ]]; then
    echo "[ios_device_run] console: the iOS bridge reported a FAULT — a call did not reach Godot: $log"
    echo "[ios_device_run]   first: $first"
    echo "[ios_device_run]   see docs/exporting/ios.md \"When you see a FAULT line\" in the kanama checkout"
    return 1
  fi

  local ran summaries
  # justified: grep -c prints 0 and exits 1 when nothing matches, which is the answer, not an error.
  ran="$(grep -c -F "$SELFTEST_RAN_MARKER" "$log" 2>/dev/null || true)"
  # justified: grep -c prints 0 and exits 1 when nothing matches, which is the answer, not an error.
  summaries="$(grep -c -E 'OBJECTCALLS SELFTEST.*faults=' "$log" 2>/dev/null || true)"
  : "${ran:=0}" "${summaries:=0}"
  if (( ran > 0 && summaries == 0 )); then
    echo "[ios_device_run] console: the self-test ran ('$SELFTEST_RAN_MARKER' x$ran) but printed no"
    echo "[ios_device_run]   'OBJECTCALLS SELFTEST ... faults=' summary line, so the faults=/expected="
    echo "[ios_device_run]   comparison never ran — the self-test did not finish: $log"
    return 1
  fi

  local line faults expected
  while IFS= read -r line; do
    faults="$(printf '%s\n' "$line" | sed -n 's/.*faults=\([0-9][0-9]*\).*/\1/p')"
    expected="$(printf '%s\n' "$line" | sed -n 's/.*expected=\([0-9][0-9]*\).*/\1/p')"
    [[ -z "$expected" ]] && expected=0
    if [[ -n "$faults" && "$faults" != "$expected" ]]; then
      echo "[ios_device_run] console: self-test fault count disagrees with the deliberate probes (faults=$faults expected=$expected): $log"
      echo "[ios_device_run]   line: $line"
      return 1
    fi
  # justified: no summary line is the empty loop; the "ran but printed no summary" case was already failed above.
  done < <(grep -E 'OBJECTCALLS SELFTEST.*faults=' "$log" 2>/dev/null || true)
  return 0
}

# Task 119 item 41 — close the demo app after a console run. Godot's `get_tree().quit()` (the
# demos' smokes) stops the main loop on iOS but does not end the process, and stopping the
# `devicectl --console` stream does not reliably kill it either: the 2026-10-02 runs left
# KanamaMatch3 and KanamaThirdPerson alive on a grey screen. So after the verdict is taken the run
# finds the app's pid(s) in `devicectl device info processes` and terminates them itself.
#
# parse_app_pids <AppName> reads a `devicectl device info processes` listing on stdin and prints
# the pid of every process whose executable is the app's own binary,
# `.../<AppName>.app/<AppName>` (so app extensions under <AppName>.app/PlugIns/ and an app whose
# name merely starts with <AppName> do not match). It reads the --json-output document
# (result.runningProcesses[] = {executable, processIdentifier}) and also the default table
# (`<pid>  <executable url>` per row), splitting the JSON into one key per line first so the key
# order and the pretty-printing do not matter; `\/` escapes are undone. Literal index() matching,
# no regex built from the name.
parse_app_pids() {
  awk -v app="$1" '
    function is_exe(s,   needle, i, nxt) {
      needle = "/" app ".app/" app
      i = index(s, needle)
      if (!i) return 0
      nxt = substr(s, i + length(needle), 1)
      return (nxt == "" || nxt == "\"" || nxt == "," || nxt == " " || nxt == "\t" || nxt == "\r")
    }
    function emit(pid) { if (!(pid in seen)) { seen[pid] = 1; print pid } }
    function reset() { havepid = 0; haveexe = 0; pid = ""; exe_ok = 0 }
    function field(s,   n, parts, i, p, v) {
      if (s ~ /^[ \t]*[0-9]+[ \t]/ && is_exe(s)) {          # table row: "<pid>  <executable>"
        match(s, /[0-9]+/); emit(substr(s, RSTART, RLENGTH)); return
      }
      if (s ~ /"processIdentifier"/) {
        v = s; sub(/^.*"processIdentifier"[ \t]*:[ \t]*/, "", v); sub(/[^0-9].*$/, "", v)
        if (v != "") { pid = v; havepid = 1 }
      } else if (s ~ /"executable"/) {
        exe_ok = is_exe(s); haveexe = 1
      } else if (s ~ /\{/) {
        reset()
      }
      if (havepid && haveexe) { if (exe_ok) emit(pid); reset() }
    }
    BEGIN { reset() }
    {
      line = $0
      gsub(/\\\//, "/", line)
      gsub(/[{}]/, "\n&\n", line)
      gsub(/,[ \t]*"/, ",\n\"", line)
      n = split(line, parts, "\n")
      for (i = 1; i <= n; i++) field(parts[i])
    }
  '
}

# Never fails the run: every step is best effort and the function always returns 0. Justified: it runs after
# the verdict is on disk and only closes the app; each `|| true` / `2>/dev/null` below is that cleanup. A pid
# that could not be terminated prints a WARNING.
terminate_demo_app() {
  local list_file="$OUTPUT_DIR/processes.json" listing pids pid
  device_process_listing() {
    rm -f "$list_file"
    if DEVELOPER_DIR="$XCODE_DEVELOPER_DIR" xcrun devicectl device info processes --device "$DEVICE_ID" \
        --timeout 30 --json-output "$list_file" >/dev/null 2>&1 && [[ -s "$list_file" ]]; then  # justified: cleanup after the verdict; falls back to the table listing below
      cat "$list_file"
    else
      DEVELOPER_DIR="$XCODE_DEVELOPER_DIR" xcrun devicectl device info processes --device "$DEVICE_ID" \
        --timeout 30 2>/dev/null || true  # justified: cleanup after the verdict; an empty listing means nothing to terminate
    fi
  }
  listing="$(device_process_listing)"
  pids="$(printf '%s\n' "$listing" | parse_app_pids "$APP_NAME" || true)"  # justified: cleanup after the verdict; an empty result means nothing to terminate
  if [[ -z "$pids" ]]; then
    echo "[ios_device_run] cleanup: no running $APP_NAME process on the device (nothing to terminate)"
    return 0
  fi
  for pid in $pids; do
    if DEVELOPER_DIR="$XCODE_DEVELOPER_DIR" xcrun devicectl device process terminate --device "$DEVICE_ID" \
        --pid "$pid" --timeout 30 >/dev/null 2>&1; then  # justified: cleanup after the verdict; failure prints a WARNING in the else branch
      echo "[ios_device_run] cleanup: terminated $APP_NAME (pid $pid)"
    else
      echo "[ios_device_run] cleanup: WARNING: could not terminate $APP_NAME (pid $pid); continuing" >&2
    fi
  done
  # A Godot app that already quit its main loop can sit through SIGTERM; check, and SIGKILL what is left.
  sleep 2
  listing="$(device_process_listing)"
  pids="$(printf '%s\n' "$listing" | parse_app_pids "$APP_NAME" || true)"  # justified: cleanup after the verdict; an empty result means nothing to terminate
  for pid in $pids; do
    if DEVELOPER_DIR="$XCODE_DEVELOPER_DIR" xcrun devicectl device process terminate --device "$DEVICE_ID" \
        --pid "$pid" --kill --timeout 30 >/dev/null 2>&1; then  # justified: cleanup after the verdict; failure prints a WARNING in the else branch
      echo "[ios_device_run] cleanup: $APP_NAME (pid $pid) survived SIGTERM; killed it"
    else
      echo "[ios_device_run] cleanup: WARNING: $APP_NAME (pid $pid) is still running and could not be killed; continuing" >&2
    fi
  done
  rm -f "$list_file"
  return 0
}

# Device-free self-test of the pid parser against captured-format samples (`devicectl device info
# processes --json-output` shape, key order varied, one compact one-line document, `\/` escapes,
# and the default table). Exit 0/1.
self_test_app_pids() {
  local failures=0 got
  expect() {  # <label> <expected pids, space separated> <app> <sample text>
    got="$(printf '%s\n' "$4" | parse_app_pids "$3" | tr '\n' ' ' | sed 's/ $//')"
    if [[ "$got" == "$2" ]]; then
      echo "[ios_device_run] self-test ok:   $1 -> '${got}'"
    else
      echo "[ios_device_run] self-test FAIL: $1 -> '${got}' (expected '$2')"
      failures=$((failures + 1))
    fi
  }
  local json='{
  "info" : { "commandType" : "device.info.processes", "outcome" : "success" },
  "result" : {
    "deviceIdentifier" : "00008101-000E109E3C63001E",
    "runningProcesses" : [
      { "executable" : "file:///sbin/launchd", "processIdentifier" : 1 },
      { "processIdentifier" : 7 },
      { "executable" : "file:///private/var/containers/Bundle/Application/AAAA-1111/KanamaMatch3.app/KanamaMatch3", "processIdentifier" : 4321 },
      { "processIdentifier" : 4400, "executable" : "file:///private/var/containers/Bundle/Application/AAAA-1111/KanamaMatch3.app/PlugIns/Ext.appex/Ext" },
      { "executable" : "file:///private/var/containers/Bundle/Application/BBBB-2222/KanamaMatch3Other.app/KanamaMatch3Other", "processIdentifier" : 4500 },
      { "processIdentifier" : 4999, "executable" : "file:///private/var/containers/Bundle/Application/CCCC-3333/KanamaMatch3.app/KanamaMatch3" },
      { "executable" : "file:///private/var/containers/Bundle/Application/DDDD-4444/KanamaThirdPerson.app/KanamaThirdPerson", "processIdentifier" : 4322 }
    ]
  }
}'
  expect "json, two instances, extension and look-alike skipped" "4321 4999" KanamaMatch3 "$json"
  expect "json, other app" "4322" KanamaThirdPerson "$json"
  expect "json, app not running" "" KanamaFPS "$json"
  expect "json, process without executable does not steal the next pid" "" Ext "$json"
  expect "json, compact one-line document with escaped slashes" "88" KanamaMatch3 \
    '{"result":{"runningProcesses":[{"processIdentifier":5,"executable":"file:\/\/\/sbin\/launchd"},{"executable":"file:\/\/\/private\/var\/containers\/Bundle\/Application\/X\/KanamaMatch3.app\/KanamaMatch3","processIdentifier":88}]}}'
  expect "default table" "4321" KanamaMatch3 \
    'Process ID   Executable
-----------  ----------------------------------------------------------------------------------
1            file:///sbin/launchd
4321         file:///private/var/containers/Bundle/Application/AAAA-1111/KanamaMatch3.app/KanamaMatch3
4400         file:///private/var/containers/Bundle/Application/AAAA-1111/KanamaMatch3.app/PlugIns/Ext.appex/Ext'
  expect "empty listing" "" KanamaMatch3 ""
  if (( failures > 0 )); then
    echo "[ios_device_run] app pid parser self-test: FAIL ($failures)"
    return 1
  fi
  echo "[ios_device_run] app pid parser self-test: PASS"
}

# Kanama task 111 item 3 -- engine errors. `devicectl --console` carries stdout/stderr only; Godot iOS installs OsLogLogger
# (drivers/apple_embedded/os_apple_embedded.mm), so its error output goes to the unified log: subsystem = the app's
# bundle id, category "ERROR" / "SCRIPT ERROR" / "SHADER ERROR" / "WARNING" / "engine", message
# `file:line:function(): rationale code` (the "ERROR:" text is the category, not part of the message).
# `log show --style compact` prints a line as `<time> <type> <process>[pid:tid] [<subsystem>:<category>] <message>`.
# The verdict reads that extract: SCRIPT ERROR lines of this app fail the step, ERROR / SHADER ERROR lines are printed
# for a human (an engine ERROR is not always a Kanama bug). A literal match (grep -F), never a regex over a bundle id.
#
# The runtime self-test this runner builds in (-PkanamaIosSelfTest=true) raises script errors ON PURPOSE: it proves that
# a throwing script method, property setter (two export paths), property getter and KanamaScope coroutine are contained
# and reported to Godot (kanama src/iosMain: KanamaIosRuntime.kt ThrowingProbeScript, ObjectCallsSelfTest.kt). Each
# reaches the unified log as one `[<bundle>:SCRIPT ERROR]` line carrying the exception message below. Those lines, and
# only those, are excluded from the verdict, and their number is ASSERTED (like the console's faults=/expected=): a
# missing one means the report path or the capture broke, an extra one means a probe fired twice. Any other SCRIPT
# ERROR line still fails the step. (The self-test's `throwSelfTestScriptError` report is built, never sent: no line.)
DELIBERATE_SCRIPT_ERROR_TEXTS=(
  "kanama self-test: deliberate script method failure"
  "kanama self-test: deliberate property setter failure"
  "kanama self-test: deliberate property getter failure"
  "kanama self-test: deliberate coroutine failure"
)
DELIBERATE_SCRIPT_ERROR_COUNTS=(1 2 1 1)

check_engine_errors() {  # <engine-errors extract> <bundle id> [<self-test ran: 1|0>, default 1]
  local log="$1" bundle="$2" selftest_ran="${3:-1}" script_errors other unexpected idx text seen want
  local total_seen=0 total_expected=0 mismatches="" status=0
  # justified: grep exits 1 when nothing matches, which is the clean case; both counts are read from the captured text.
  script_errors="$(grep -F "[$bundle:SCRIPT ERROR]" "$log" || true)"
  # justified: grep exits 1 when nothing matches, which is the clean case; the lines are printed, not judged.
  other="$({ grep -F "[$bundle:ERROR]" "$log"; grep -F "[$bundle:SHADER ERROR]" "$log"; } || true)"
  if [[ -n "$other" ]]; then
    echo "[ios_device_run] engine log: $(printf '%s\n' "$other" | wc -l | tr -d ' ') ERROR line(s) (printed, not failing):"
    printf '%s\n' "$other" | head -n 20 | sed 's/^/[ios_device_run]   /'
  fi
  unexpected="$script_errors"
  for idx in "${!DELIBERATE_SCRIPT_ERROR_TEXTS[@]}"; do
    text="${DELIBERATE_SCRIPT_ERROR_TEXTS[$idx]}"
    want="${DELIBERATE_SCRIPT_ERROR_COUNTS[$idx]}"
    [[ "$selftest_ran" == "1" ]] || want=0
    # justified: grep -c prints 0 and exits 1 when nothing matches, which is the count.
    seen="$(printf '%s\n' "$script_errors" | grep -c -F "$text" || true)"
    # justified: grep -v exits 1 when every line was deliberate, which leaves nothing unexpected.
    unexpected="$(printf '%s\n' "$unexpected" | grep -v -F "$text" || true)"
    total_seen=$((total_seen + seen))
    total_expected=$((total_expected + want))
    if [[ "$seen" -ne "$want" ]]; then
      mismatches="${mismatches}[ios_device_run]   '$text': seen=$seen expected=$want"$'\n'
    fi
  done
  unexpected="$(printf '%s\n' "$unexpected" | sed '/^$/d')"
  echo "[ios_device_run] engine log: deliberate self-test SCRIPT ERRORs=$total_seen expected=$total_expected (self-test ran: $selftest_ran)"
  if [[ -n "$mismatches" ]]; then
    echo "[ios_device_run] engine log: the self-test's deliberate SCRIPT ERROR count is wrong (a report did not reach the log, or a probe fired twice):"
    printf '%s' "$mismatches"
    status=1
  fi
  if [[ -n "$unexpected" ]]; then
    echo "[ios_device_run] engine log: $(printf '%s\n' "$unexpected" | wc -l | tr -d ' ') SCRIPT ERROR line(s) from $bundle that are not the self-test's deliberate ones:"
    printf '%s\n' "$unexpected" | head -n 20 | sed 's/^/[ios_device_run]   /'
    status=1
  fi
  if [[ "$status" -eq 0 ]]; then
    echo "[ios_device_run] engine log: no SCRIPT ERROR from $bundle besides the self-test's deliberate ones"
  fi
  return "$status"
}

# Device-free red runs of check_console_faults against fixture console logs (task 118): the clean log must pass,
# and each broken one must fail with its own message. Includes the 2026-10 device run that reported exit 0 over
# `OBJECTCALLS SELFTEST FAIL: ...` and `306 passed, 2 failed`. Exit 0/1.
self_test_console_checks() {
  local dir failures=0
  dir="$(mktemp -d "${TMPDIR:-/tmp}/ios_device_run_console.XXXXXX")"
  local good="$dir/good.log"
  cat >"$good" <<'LOG'
[kanama][ios] runtime up
PTRCALL SELFTEST MATRIX: 120 passed, 0 failed
[kanama][ios][c] FAULT-PROBE kanama_ios_godot_get_method_bind: bind-lookup-failed Node3D.set_visible
OBJECTCALLS SELFTEST: 308 passed, 0 failed faults=7 expected=7
[kanama][ios][kn] OBJECTCALLS SELFTEST (frame 1): 25 passed, 0 failed faults=7 expected=7
[kanama:smoke] SmokeQuit complete
LOG
  case_log() {  # <label> <expect: pass|fail> <expected text in output, or -> <log file> [REQUIRE_SELFTEST]
    local out rc=0
    out="$(REQUIRE_SELFTEST="${5:-$REQUIRE_SELFTEST}" check_console_faults "$4" 2>&1)" || rc=$?
    if [[ "$2" == "pass" && "$rc" -eq 0 ]] ||
       [[ "$2" == "fail" && "$rc" -ne 0 && ( "$3" == "-" || "$out" == *"$3"* ) ]]; then
      echo "[ios_device_run] console self-test ok:   $1 -> $2"
    else
      echo "[ios_device_run] console self-test FAIL: $1 expected $2 ${3:+('$3')}, got rc=$rc: $out"
      failures=$((failures + 1))
    fi
  }
  case_log "clean log" pass - "$good"
  # the reported case: FAIL line and a failed count, everything else healthy
  sed 's/OBJECTCALLS SELFTEST: 308 passed, 0 failed/OBJECTCALLS SELFTEST FAIL: property-retain(a new set releases the old values)\nOBJECTCALLS SELFTEST: 306 passed, 2 failed/' "$good" >"$dir/r1.log"
  case_log "FAIL line + 2 failed (the device run)" fail "reported a FAILURE" "$dir/r1.log"
  grep -v 'SELFTEST FAIL' "$dir/r1.log" >"$dir/r2.log"
  case_log "ObjectCalls summary with failures, no FAIL line" fail "summary has failures" "$dir/r2.log"
  sed 's/MATRIX: 120 passed, 0 failed/MATRIX: 119 passed, 1 failed/' "$good" >"$dir/r3.log"
  case_log "PTRCALL matrix summary with a failure" fail "summary has failures" "$dir/r3.log"
  { grep -v 'OBJECTCALLS SELFTEST' "$good"; echo 'OBJECTCALLS SELFTEST: 12 passed, 0 failed'; echo 'OBJECTCALLS SELFTEST: 306 passed, 2 failed faults=7 expected=7'; } >"$dir/r4.log"
  case_log "early summary clean, final summary failed" fail "summary has failures" "$dir/r4.log"
  grep -v 'OBJECTCALLS SELFTEST' "$good" >"$dir/r5.log"
  case_log "self-test ran, ObjectCalls summary missing" fail "no 'OBJECTCALLS SELFTEST: N passed, 0 failed'" "$dir/r5.log"
  grep -v 'PTRCALL SELFTEST MATRIX' "$good" >"$dir/r6.log"
  case_log "ObjectCalls summary present, matrix summary missing" fail "no 'PTRCALL SELFTEST MATRIX: N passed, 0 failed'" "$dir/r6.log"
  printf '[kanama][ios] runtime up\n' >"$dir/r7.log"
  case_log "no self-test text at all (the default requires it: kanama task 138 item 24)" fail "did not run" "$dir/r7.log" 1
  case_log "no self-test text at all, KANAMA_IOS_REQUIRE_SELFTEST=0 (a KANAMA_ROOT that cannot build it)" pass - "$dir/r7.log" 0
  { cat "$good"; echo '[kanama][ios][c] FAULT kanama_ios_godot_ptrcall: null-bind'; } >"$dir/r8.log"
  case_log "a real FAULT line" fail "reported a FAULT" "$dir/r8.log"
  sed 's/faults=7 expected=7/faults=8 expected=7/' "$good" >"$dir/r9.log"
  case_log "faults=8 expected=7" fail "fault count disagrees" "$dir/r9.log"
  # kanama task 129 A: the frame-1 phase lines of the iPhone 12 run that printed PASS.
  { cat "$good"
    echo '[kanama][ios][kn] OBJECTCALLS SELFTEST (frame 1) FAIL: tween-await(signal fires -> the callback after the await runs)'
    echo '[kanama][ios][kn] OBJECTCALLS SELFTEST (frame 1): 24 passed, 1 failed faults=7 expected=7'; } >"$dir/r10.log"
  case_log "frame-1 FAIL line + frame-1 summary with a failure" fail "reported a FAILURE" "$dir/r10.log"
  grep -v 'SELFTEST (frame 1) FAIL' "$dir/r10.log" >"$dir/r11.log"
  case_log "frame-1 summary with a failure, no FAIL line" fail "summary has failures" "$dir/r11.log"
  grep -v 'SELFTEST (frame 1)' "$good" >"$dir/r12.log"
  case_log "frame-1 summary missing (kanama task 138 item 24)" fail "no 'OBJECTCALLS SELFTEST (frame 1)" "$dir/r12.log"
  # kanama task 111 item 3: the engine-error verdict over a `log show --style compact` extract. The deliberate lines are
  # the five an iPhone 12 run of KanamaMatch3 wrote (2026-10-15), verbatim.
  local app_id=net.multigesture.kanama.match3 eng="$dir/engine.log"
  case_engine() {  # <label> <expect: pass|fail> <expected text, or -> <extract file> [self-test ran: 1|0]
    local out rc=0
    out="$(check_engine_errors "$4" "$app_id" "${5:-1}" 2>&1)" || rc=$?
    if [[ "$2" == "pass" && "$rc" -eq 0 ]] ||
       [[ "$2" == "fail" && "$rc" -ne 0 && ( "$3" == "-" || "$out" == *"$3"* ) ]]; then
      echo "[ios_device_run] console self-test ok:   $1 -> $2"
    else
      echo "[ios_device_run] console self-test FAIL: $1 expected $2 ${3:+('$3')}, got rc=$rc: $out"
      failures=$((failures + 1))
    fi
  }
  cat >"$eng" <<'LOG'
Timestamp               Ty Process[PID:TID]
2026-10-15 10:07:21.009 E  KanamaMatch3[21157:b8f010] [net.multigesture.kanama.match3:ERROR] scene/main/node.cpp:3468:queue_free(): Can't queue free a node when no SceneTree is available. Parameter "tree" is null.
2026-10-15 10:07:21.285 E  KanamaMatch3[21157:b8f010] [net.multigesture.kanama.match3:SCRIPT ERROR] :0:kanama_ios_throwing_probe.throw_probe(): kotlin.IllegalStateException: kanama self-test: deliberate script method failure kotlin.IllegalStateException
2026-10-15 10:07:21.329 E  KanamaMatch3[21157:b8f010] [net.multigesture.kanama.match3:SCRIPT ERROR] :0:kanama_ios_throwing_probe property <property 0> set(): kotlin.IllegalStateException: kanama self-test: deliberate property setter failure kotlin.IllegalStateException
2026-10-15 10:07:21.374 E  KanamaMatch3[21157:b8f010] [net.multigesture.kanama.match3:SCRIPT ERROR] :0:kanama_ios_throwing_probe property <property 0> set(): kotlin.IllegalStateException: kanama self-test: deliberate property setter failure kotlin.IllegalStateException
2026-10-15 10:07:21.424 E  KanamaMatch3[21157:b8f010] [net.multigesture.kanama.match3:SCRIPT ERROR] :0:kanama_ios_throwing_probe property <property 0> get(): kotlin.IllegalStateException: kanama self-test: deliberate property getter failure kotlin.IllegalStateException
2026-10-15 10:07:21.527 E  KanamaMatch3[21157:b8f010] [net.multigesture.kanama.match3:SCRIPT ERROR] :0:KanamaScope coroutine(): kotlin.IllegalStateException: kanama self-test: deliberate coroutine failure kotlin.IllegalStateException
2026-10-15 10:07:21.569 E  KanamaMatch3[21157:b8f010] [net.multigesture.kanama.match3:ERROR] core/extension/gdextension_interface.cpp:1650:gdextension_classdb_get_method_bind(): Method 'Node3D.set_visible' has changed and no compatibility fallback has been provided.
2026-10-15 10:07:21.600 Df KanamaMatch3[21157:b8f010] [net.multigesture.kanama.match3:WARNING] some warning
LOG
  case_engine "engine log: the five deliberate self-test SCRIPT ERRORs + ERROR lines (an iPhone 12 run)" pass - "$eng"
  echo "2026-10-15 10:07:22.366 E  KanamaMatch3[21157:b8f010] [$app_id:SCRIPT ERROR] res://Foo.gd:12:_ready(): Invalid call. Nonexistent function 'x' in base 'Node'." >"$dir/engine.unknown.line"
  cat "$eng" "$dir/engine.unknown.line" >"$dir/engine.unknown.log"
  case_engine "engine log: a SCRIPT ERROR not on the deliberate list" fail "not the self-test's deliberate ones" "$dir/engine.unknown.log"
  grep -v 'deliberate coroutine failure' "$eng" >"$dir/engine.missing.log"
  case_engine "engine log: a deliberate SCRIPT ERROR missing (deliberate=4 expected=5)" fail "SCRIPT ERRORs=4 expected=5" "$dir/engine.missing.log"
  { cat "$eng"; grep 'deliberate script method failure' "$eng"; } >"$dir/engine.twice.log"
  case_engine "engine log: a deliberate SCRIPT ERROR twice (deliberate=6 expected=5)" fail "SCRIPT ERRORs=6 expected=5" "$dir/engine.twice.log"
  grep -v 'SCRIPT ERROR' "$eng" >"$dir/engine.none.log"
  case_engine "engine log: no SCRIPT ERROR at all while the self-test ran" fail "SCRIPT ERRORs=0 expected=5" "$dir/engine.none.log"
  case_engine "engine log: no SCRIPT ERROR, no self-test (KANAMA_IOS_REQUIRE_SELFTEST=0 and none ran)" pass - "$dir/engine.none.log" 0
  case_engine "engine log: deliberate lines without a self-test run" fail "SCRIPT ERRORs=5 expected=0" "$eng" 0
  sed 's/SCRIPT ERROR/SHADER ERROR/' "$dir/engine.unknown.line" | cat "$eng" - >"$dir/engine.shader.log"
  case_engine "engine log: SHADER ERROR is printed, not failing" pass - "$dir/engine.shader.log"
  sed "s/\[$app_id:SCRIPT ERROR\]/[com.other.app:SCRIPT ERROR]/" "$dir/engine.unknown.line" | cat "$eng" - >"$dir/engine.other.log"
  case_engine "engine log: another app's SCRIPT ERROR is not this app's" pass - "$dir/engine.other.log"
  rm -rf "$dir"
  if (( failures > 0 )); then
    echo "[ios_device_run] console checks self-test: FAIL ($failures)"
    return 1
  fi
  echo "[ios_device_run] console checks self-test: PASS"
}

if [[ "${1:-}" == "--self-test-console-checks" ]]; then
  self_test_console_checks && exit 0
  exit 1
fi
if [[ "${1:-}" == "--check-engine-errors" ]]; then
  if [[ $# -ne 3 || ! -f "$2" ]]; then
    echo "usage: scripts/ios_device_run.sh --check-engine-errors /path/to/engine-errors.log bundle.id" >&2
    exit 2
  fi
  # The runner builds the self-test in, so its deliberate SCRIPT ERRORs are expected unless KANAMA_IOS_REQUIRE_SELFTEST=0.
  if check_engine_errors "$2" "$3" "$REQUIRE_SELFTEST"; then
    echo "[ios_device_run] engine error check: PASS ($2)"
    exit 0
  fi
  echo "[ios_device_run] FAIL"
  exit 1
fi
if [[ "${1:-}" == "--self-test-app-pids" ]]; then
  self_test_app_pids && exit 0
  exit 1
fi
if [[ "${1:-}" == "--print-app-pids" ]]; then
  if [[ $# -ne 3 || ! -f "$3" ]]; then
    echo "usage: scripts/ios_device_run.sh --print-app-pids AppName /path/to/devicectl-processes-output" >&2
    exit 2
  fi
  parse_app_pids "$2" <"$3"
  exit 0
fi

if [[ "${1:-}" == "--check-console-faults" ]]; then
  if [[ $# -ne 2 || ! -f "$2" ]]; then
    echo "usage: scripts/ios_device_run.sh --check-console-faults /path/to/console.log" >&2
    exit 2
  fi
  if check_console_faults "$2"; then
    echo "[ios_device_run] console fault check: PASS ($2)"
    exit 0
  fi
  echo "[ios_device_run] FAIL"
  exit 1
fi

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
    -PkanamaIosSelfTest=true \
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
export_status=0
# `|| export_status=` keeps errexit from aborting the pipeline before the status is read (pipefail is on).
"$GODOT_BIN" --headless --path "$DEMO_DIR" --export-debug iOS "$IPA_PATH" 2>&1 | tee "$EXPORT_LOG" || export_status="${PIPESTATUS[0]}"
# Kanama's editor export guard prints this line, and ends the export, when a .kt script the export uses
# does not bind in the export-time editor (desktop addon missing, or kanama-scripts.jar stale).
if grep -q 'Kanama: Export stopped' "$EXPORT_LOG"; then
  echo "[ios_device_run] the Kanama export guard stopped the export: the export-time editor cannot bind the project's .kt scripts (see the 'Kanama: Export stopped' message above); log: $EXPORT_LOG" >&2
  exit 1
fi
if [[ "$export_status" -ne 0 ]]; then
  echo "[ios_device_run] Godot export failed (exit $export_status)" >&2
  exit 1
fi
# The desktop runtime logs every .kt it loads during the export with the Kotlin class it bound.
# An empty class means the scripts jar does not contain that script: the exported scenes would
# carry none of its @ScriptProperty values, which is invisible until the app crashes on device.
if grep -q 'ResourceFormatLoader\._load bound kotlinClass= ' "$EXPORT_LOG"; then
  echo "[ios_device_run] export-time editor bound no Kotlin class to a project script:" >&2
  # justified: diagnostics; the exit 1 on the next lines is the verdict.
  grep -B1 'ResourceFormatLoader\._load bound kotlinClass= ' "$EXPORT_LOG" | grep '_load path=' >&2 || true
  echo "[ios_device_run] scene-stored @ScriptProperty values would be dropped from this export; refusing to build it." >&2
  exit 1
fi
# Task 112: compare every converted scene with its source — a script property present in the
# .tscn but missing from the exported .scn fails the step, whatever dropped it. The check lives in
# kanama (shared with the starter smoke); a KANAMA_ROOT that predates it is warned about, not failed.
SCENE_PARITY_CHECK="$KANAMA_ROOT/scripts/check_exported_scene_properties.gd"
if [[ -f "$SCENE_PARITY_CHECK" ]]; then
  # --require-converted: an export that converted no scene (it wrote nothing, or wrote the scenes
  # unconverted) must not pass as "nothing to compare". A KANAMA_ROOT that predates the option ignores it.
  if ! "$GODOT_BIN" --headless --path "$DEMO_DIR" --script "$SCENE_PARITY_CHECK" -- --require-converted; then
    echo "[ios_device_run] exported scenes lost script properties (see [check_exported_scenes] lines above); refusing to build it." >&2
    exit 1
  fi
elif [[ "${KANAMA_IOS_ALLOW_MISSING_SCENE_CHECK:-0}" == "1" ]]; then
  echo "SKIP: exported-scene parity check (task 112): $SCENE_PARITY_CHECK not found in KANAMA_ROOT and KANAMA_IOS_ALLOW_MISSING_SCENE_CHECK=1 was set"
else
  # Task 118: this was a WARNING and the run went on to build and launch an app whose scenes were never
  # compared with their sources. A KANAMA_ROOT that predates the check must be opted out of by name.
  echo "[ios_device_run] $SCENE_PARITY_CHECK not found in KANAMA_ROOT: the exported-scene parity check (task 112) cannot run." >&2
  echo "[ios_device_run] Use a KANAMA_ROOT that has it, or set KANAMA_IOS_ALLOW_MISSING_SCENE_CHECK=1 to skip it explicitly." >&2
  exit 1
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
INSTALL_LOG="$OUTPUT_DIR/install.log"
DEVELOPER_DIR="$XCODE_DEVELOPER_DIR" xcrun devicectl device install app --device "$DEVICE_ID" "$APP_PATH" 2>&1 | tee "$INSTALL_LOG"
# The exported app keeps the bundle id from the demo's export preset. If that is not the id this
# run was asked to launch, the launch below would start whatever app already sits under the
# requested id (an older build) and report ITS console as this run's result — which is exactly how
# a task-115 diagnosis run re-ran the previous gate build unnoticed. Refuse instead (task 118).
INSTALLED_BUNDLE_ID="$(sed -n 's/^.*bundleID: *//p' "$INSTALL_LOG" | head -n 1 | tr -d '[:space:]')"
if [[ -z "$INSTALLED_BUNDLE_ID" ]]; then
  echo "[ios_device_run] install did not report a bundleID (see $INSTALL_LOG); refusing to launch blind." >&2
  exit 1
fi
if [[ "$INSTALLED_BUNDLE_ID" != "$BUNDLE_ID" ]]; then
  echo "[ios_device_run] installed bundle id '$INSTALLED_BUNDLE_ID' (from the export preset) != requested '$BUNDLE_ID'; the launch would start a different app. Pass the exported id." >&2
  exit 1
fi

CONSOLE_SECONDS="${KANAMA_IOS_CONSOLE_SECONDS:-0}"
if [[ ! "$CONSOLE_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "[ios_device_run] KANAMA_IOS_CONSOLE_SECONDS must be a non-negative integer (got: $CONSOLE_SECONDS)." >&2
  exit 2
fi
CONSOLE_FAIL_PATTERN="${KANAMA_IOS_CONSOLE_FAIL_PATTERN:-App terminated due to signal|FATAL}"

# Task 111: run the demo's smoke on the phone. Only meaningful with a console window (the smoke's
# completion line is read from it) and only for demos whose kotlin-src/Smoke*.kt reads KANAMA_DEMO_SMOKE_QUIT
# (scripts/demo_has_smoke_quit.sh).
SMOKE_QUIT="${KANAMA_IOS_SMOKE_QUIT:-0}"
SMOKE_COMPLETE_PATTERN="${KANAMA_IOS_SMOKE_COMPLETE_PATTERN:-\[kanama:smoke\] SmokeQuit complete}"
smoke_active=0
launch_env_args=()
if [[ "$SMOKE_QUIT" == "1" ]]; then
  if [[ "$CONSOLE_SECONDS" -eq 0 ]]; then
    echo "[ios_device_run] KANAMA_IOS_SMOKE_QUIT=1 needs KANAMA_IOS_CONSOLE_SECONDS > 0 (the completion line is read from the console); ignoring." >&2
  elif ! "$(dirname "${BASH_SOURCE[0]}")/demo_has_smoke_quit.sh" "$DEMO_DIR"; then
    echo "[ios_device_run] smoke: no kotlin-src/Smoke*.kt in $DEMO_DIR reads KANAMA_DEMO_SMOKE_QUIT; launch-only step"
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
  local copy_log="$dest.copy.log"
  if DEVELOPER_DIR="$XCODE_DEVELOPER_DIR" xcrun devicectl device copy from --device "$DEVICE_ID" \
      --domain-type systemCrashLogs --source . --destination "$dest" >"$copy_log" 2>&1; then
    local f
    for f in "$dest/$APP_NAME"-*.ips "$dest/$APP_NAME".*.ips; do
      if [[ -e "$f" ]]; then basename "$f"; fi
    done
  else
    # Task 118: this was a WARNING and the step went on without its crash-report check, which is a green
    # for a check that did not run. It fails now, and says how to opt out and what that costs.
    echo "[ios_device_run] FAIL: could not copy the device's crash logs (devicectl output below)." >&2
    echo "[ios_device_run]   This step compares the device's crash reports before and after the launch to catch a crash" >&2
    echo "[ios_device_run]   the console window missed; without them that check is blind, so the step fails instead of" >&2
    echo "[ios_device_run]   passing quietly. To run without it, set KANAMA_IOS_CRASH_REPORTS=0 (the step then prints" >&2
    echo "[ios_device_run]   'SKIP: device crash-report check'). Usual causes: a locked device, a dropped connection." >&2
    tail -n 20 "$copy_log" >&2
    return 1
  fi
  return 0  # a no-match glob must not fail the caller under set -e
}
# Device-wide data this step reads but never keeps: the unified-log archive of the window (~200 MB, every app's log;
# see capture_engine_errors) and the crash-log snapshots (every app's crash reports). Only this app's filtered lines
# (engine-errors.log) and its own new .ips (crashes/) stay next to the evidence. Removed on every exit path, also on a
# failure or an interrupted run.
ENGINE_TAR="$OUTPUT_DIR/device-log.tar"
ENGINE_ARCHIVE="$OUTPUT_DIR/device-log.logarchive"
remove_device_log() {
  rm -rf "$ENGINE_TAR" "$ENGINE_ARCHIVE"
}
remove_device_data() {
  remove_device_log
  rm -rf "$OUTPUT_DIR/crashes.before" "$OUTPUT_DIR/crashes.after"
}
trap remove_device_data EXIT
crashes_before=""
if [[ "$CRASH_REPORTS" == "1" ]]; then
  crashes_before="$(snapshot_crash_reports "$OUTPUT_DIR/crashes.before")" || exit 1
else
  echo "SKIP: device crash-report check: KANAMA_IOS_CRASH_REPORTS=$CRASH_REPORTS"
fi

# Kanama task 111 item 3: engine errors from the device's unified log (see check_engine_errors). The archive is fetched by
# `idevicesyslog archive` (libimobiledevice), which needs no root, unlike `log collect --device`; the window starts a few
# seconds before the launch to absorb clock skew between Mac and phone.
#   auto (default): on when there is a console window, the installed idevicesyslog has the `archive` command and the
#        device is reachable over USB (`idevice_id -l`); otherwise a `SKIP:` line says which. A capture that fails (no
#        archive, an unreadable one, or one that holds no line of this app) is a loud `SKIP:` with the reason, not a FAIL.
#   1:   required: each of those is a FAIL.
#   0:   skipped.
# The verdict over a capture that worked (check_engine_errors) fails the step in both modes.
ENGINE_LOG="${KANAMA_IOS_ENGINE_LOG:-auto}"
case "$ENGINE_LOG" in
  auto|1|0) ;;
  *)
    echo "[ios_device_run] KANAMA_IOS_ENGINE_LOG must be auto, 1 or 0 (got: $ENGINE_LOG)." >&2
    exit 2
    ;;
esac
# Not usable: a SKIP line in auto mode, a FAIL with KANAMA_IOS_ENGINE_LOG=1.
engine_log_unusable() {  # <reason>
  if [[ "$ENGINE_LOG" == "1" ]]; then
    echo "[ios_device_run] FAIL: KANAMA_IOS_ENGINE_LOG=1 but $1" >&2
    exit 1
  fi
  echo "SKIP: device engine-error check: $1 (KANAMA_IOS_ENGINE_LOG=auto; set 1 to require it)"
}
engine_log_active=0
if [[ "$ENGINE_LOG" == "0" ]]; then
  echo "SKIP: device engine-error check: KANAMA_IOS_ENGINE_LOG=0"
elif [[ "$CONSOLE_SECONDS" -eq 0 ]]; then
  echo "SKIP: device engine-error check: needs a console window (KANAMA_IOS_CONSOLE_SECONDS > 0)"
elif ! command -v idevicesyslog >/dev/null 2>&1; then  # justified: a presence probe; engine_log_unusable decides (auto: SKIP line, 1: FAIL)
  engine_log_unusable "idevicesyslog is not installed (brew install libimobiledevice)"
# justified: probes of the tool's help text and of the USB device list; engine_log_unusable decides (auto: SKIP line, 1: FAIL)
elif ! idevicesyslog --help 2>&1 | grep -Eq '^[[:space:]]*archive[[:space:]]'; then
  engine_log_unusable "this idevicesyslog has no 'archive' command (libimobiledevice 1.3.1+ has it)"
# justified: idevice_id lists the USB-attached devices; an empty or failed listing is the "not reachable" answer
elif ! idevice_id -l 2>/dev/null | grep -Fxq "$DEVICE_ID"; then
  engine_log_unusable "the device is not reachable over USB (idevice_id -l does not list it; a Wi-Fi-only pairing cannot serve the log archive)"
else
  engine_log_active=1
fi
ENGINE_LOG_START="$(( $(date +%s) - 15 ))"

# A capture problem: FAIL with KANAMA_IOS_ENGINE_LOG=1 (return 1), a loud SKIP in auto mode (return 0).
engine_capture_failed() {  # <reason> [<file whose tail explains it>]
  if [[ "$ENGINE_LOG" == "1" ]]; then
    echo "[ios_device_run] FAIL: device engine-error check: $1" >&2
    [[ -n "${2:-}" && -f "$2" ]] && tail -n 20 "$2" >&2
    echo "[ios_device_run]   Without the device log this step cannot see the engine's SCRIPT ERROR output, so it fails instead of" >&2
    echo "[ios_device_run]   passing quietly. KANAMA_IOS_ENGINE_LOG=auto skips with a SKIP line instead; 0 skips it. Usual causes: a locked device, a dropped USB connection." >&2
    return 1
  fi
  echo "SKIP: device engine-error check: capture failed: $1 (KANAMA_IOS_ENGINE_LOG=auto; set 1 to require it)"
  [[ -n "${2:-}" && -f "$2" ]] && tail -n 20 "$2" | sed 's/^/SKIP:   /'
  return 0
}

capture_engine_errors() {
  # Fetch the device log archive for the run window, extract this app's error-category lines and judge them.
  local capture_log="$OUTPUT_DIR/device-log.capture.log" predicate selftest_ran=0 app_lines
  predicate="subsystem == \"$BUNDLE_ID\" AND (category == \"ERROR\" OR category == \"SCRIPT ERROR\" OR category == \"SHADER ERROR\")"
  remove_device_log
  rm -f "$OUTPUT_DIR/engine-errors.log" "$OUTPUT_DIR/engine-errors.stderr.log"
  mkdir -p "$ENGINE_ARCHIVE"
  if ! idevicesyslog -u "$DEVICE_ID" archive "$ENGINE_TAR" --start-time "$ENGINE_LOG_START" >"$capture_log" 2>&1 ||
     ! tar -xf "$ENGINE_TAR" -C "$ENGINE_ARCHIVE" >>"$capture_log" 2>&1; then
    engine_capture_failed "could not fetch the device's log archive (idevicesyslog output below)" "$capture_log"
    return
  fi
  rm -f "$ENGINE_TAR"
  if ! /usr/bin/log show --archive "$ENGINE_ARCHIVE" --info --style compact --predicate "$predicate" >"$OUTPUT_DIR/engine-errors.log" 2>"$OUTPUT_DIR/engine-errors.stderr.log"; then
    engine_capture_failed "log show could not read the device log archive" "$OUTPUT_DIR/engine-errors.stderr.log"
    return
  fi
  # Evidence that the capture saw the app at all: an archive that holds none of its lines (a window that missed the run)
  # would make "no SCRIPT ERROR" blind, so it is a capture failure, not a pass.
  app_lines="$(/usr/bin/log show --archive "$ENGINE_ARCHIVE" --info --style compact --predicate "subsystem == \"$BUNDLE_ID\"" 2>/dev/null | grep -c -F "[$BUNDLE_ID:" || true)"  # justified: grep -c exits 1 on a count of 0, which is judged on the next line
  if [[ "$app_lines" -eq 0 ]]; then
    engine_capture_failed "the device log archive holds no line from $BUNDLE_ID at all (the window missed the run)"
    return
  fi
  echo "[ios_device_run] engine log: ${app_lines} line(s) from $BUNDLE_ID in the device log archive"
  # The self-test's deliberate SCRIPT ERRORs are expected when it ran, which the console window shows.
  if grep -q -F "$SELFTEST_RAN_MARKER" "$VERDICT_LOG"; then selftest_ran=1; fi  # justified: the grep status is the answer
  check_engine_errors "$OUTPUT_DIR/engine-errors.log" "$BUNDLE_ID" "$selftest_ran"
}

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
  # then stop the stream and terminate the app explicitly (terminate_demo_app). A crash inside the window shows up as devicectl's
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
    # justified (this loop's `2>/dev/null` probes): the console log is created empty before the loop and kill -0
    # is a liveness test; the verdict is read from $VERDICT_LOG after the loop.
    if grep -q -E "$CONSOLE_FAIL_PATTERN" "$CONSOLE_LOG" 2>/dev/null; then
      break
    fi
    if ! kill -0 "$console_pid" 2>/dev/null; then
      break  # the stream ended on its own: the app exited
    fi
    if [[ -z "$launched_at" ]] && grep -q '\[kanama\]\[ios\]' "$CONSOLE_LOG" 2>/dev/null; then  # justified: poll of the log being written; the verdict is read from the window copy afterwards
      launched_at="$waited"
      echo "[ios_device_run] console: runtime reported after ${launched_at}s; watching ${CONSOLE_SECONDS}s more"
    fi
    if [[ -z "$launched_at" ]]; then
      [[ "$waited" -ge "$launch_timeout" ]] && break
    else
      [[ $((waited - launched_at)) -ge "$CONSOLE_SECONDS" ]] && break
    fi
  done
  if [[ "$smoke_active" -eq 1 ]] && grep -q -E "$SMOKE_COMPLETE_PATTERN" "$CONSOLE_LOG" 2>/dev/null; then  # justified: only decides whether to wait 3 s more before the verdict is taken from the window copy
    # Give the quit a moment so a crash inside SceneTree.quit() / teardown still lands in the log.
    sleep 3
  fi
  # Judge the log as it stood BEFORE the stream is stopped: stopping devicectl terminates the
  # app with SIGTERM and it then reports "App terminated due to signal 15." — our own doing, not
  # a crash (the first validation run failed every demo on exactly that line).
  console_lines="$(wc -l <"$CONSOLE_LOG" | tr -d ' ')"
  VERDICT_LOG="$OUTPUT_DIR/console.window.log"
  head -n "$console_lines" "$CONSOLE_LOG" >"$VERDICT_LOG"
  # justified: stopping our own console stream after the window; the verdict was copied to $VERDICT_LOG above.
  kill "$console_pid" >/dev/null 2>&1 || true
  # justified: reaping our own console stream; its exit status says nothing about the app.
  wait "$console_pid" >/dev/null 2>&1 || true
  # The verdict is already on disk; now close the app (item 41) — it would otherwise stay open on a
  # grey screen after the demo's quit. Best effort, never changes the outcome.
  # justified: terminate_demo_app is documented never to fail the run (it only closes the app after the verdict).
  terminate_demo_app || true
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
  if ! check_console_faults "$VERDICT_LOG"; then
    echo "[ios_device_run] FAIL"
    exit 1
  fi
  if [[ "$smoke_active" -eq 1 ]]; then
    if grep -q -E "$SMOKE_COMPLETE_PATTERN" "$VERDICT_LOG"; then
      echo "[ios_device_run] smoke: completion line seen ($(grep -c -E "$SMOKE_COMPLETE_PATTERN" "$VERDICT_LOG")x)"
    else
      echo "[ios_device_run] smoke: the smoke ran but its completion line never appeared in ${waited}s — the smoke did not finish (crash without signature, hang, or the env var did not reach the app): $CONSOLE_LOG"
      echo "[ios_device_run] FAIL"
      exit 1
    fi
  fi
  echo "[ios_device_run] console: ${console_lines} lines in ${waited}s, no crash signature and no bridge FAULT: $CONSOLE_LOG"
fi

# The engine-log verdict is DEFERRED until the crash-report check has run: a failed capture or a SCRIPT ERROR must never
# hide a new crash report. Both results are printed; either one fails the step.
final_status=0
if [[ "$engine_log_active" -eq 1 ]]; then
  capture_engine_errors || final_status=1
  remove_device_log
fi

if [[ "$CRASH_REPORTS" == "1" ]]; then
  if crashes_after="$(snapshot_crash_reports "$OUTPUT_DIR/crashes.after")"; then
    new_crashes="$(comm -13 <(printf '%s\n' "$crashes_before" | sort -u) <(printf '%s\n' "$crashes_after" | sort -u) | sed '/^$/d')"
    if [[ -n "$new_crashes" ]]; then
      mkdir -p "$OUTPUT_DIR/crashes"
      while IFS= read -r ips; do
        # justified: keeping a copy of the report for humans; the step fails below because of the NEW report either way.
        cp "$OUTPUT_DIR/crashes.after/$ips" "$OUTPUT_DIR/crashes/" 2>/dev/null || true
        echo "[ios_device_run] crash report: NEW $ips -> $OUTPUT_DIR/crashes/$ips"
      done <<<"$new_crashes"
      final_status=1
    else
      echo "[ios_device_run] crash reports: no new $APP_NAME report on the device"
      rm -f "$OUTPUT_DIR/crashes.before.copy.log" "$OUTPUT_DIR/crashes.after.copy.log"
    fi
  else
    final_status=1  # snapshot_crash_reports printed the FAIL and its reason
  fi
fi

if [[ "$final_status" -ne 0 ]]; then
  echo "[ios_device_run] FAIL"
  exit 1
fi
echo "[ios_device_run] PASS"
