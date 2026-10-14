#!/usr/bin/env bash
# Exit 0 when the demo ships a smoke that reads KANAMA_DEMO_SMOKE_QUIT, i.e. some kotlin-src/Smoke*.kt names the
# variable. The iOS runner uses this to decide whether to launch with the variable and require the completion line.
# Keyed on the variable, not on a file name: Match3 moved from SmokeQuit.kt to Smoke.kt, and FPS/Racing/City-Builder
# also have a Smoke.kt but read their own variables, so a plain "Smoke.kt exists" test would be wrong.
#
# Usage: scripts/demo_has_smoke_quit.sh <demo dir>
set -euo pipefail
demo_dir="${1:?usage: demo_has_smoke_quit.sh <demo dir>}"
shopt -s nullglob
files=("$demo_dir"/kotlin-src/Smoke*.kt)
[[ ${#files[@]} -gt 0 ]] || exit 1
grep -qF 'KANAMA_DEMO_SMOKE_QUIT' "${files[@]}"
