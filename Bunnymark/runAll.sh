#!/usr/bin/env bash
# Runs every benchmark once per language, after the godot-kotlin-jvm harness's runAll.sh. Each run appends its score
# to benchmark_results.json in the project's user:// folder. Build the Kotlin scripts first (buildScripts), and the
# C# scripts (dotnet build Bunnymark.csproj) when GODOT_MONO is set.
#
#   GODOT       the Godot editor for GDScript and Kotlin (default: the macOS install)
#   GODOT_MONO  the .NET build of the same Godot version; C# runs only when it is set
#   LANGS       the languages to run (default: "gd kt", plus cs with GODOT_MONO)
#   RUN_TIMEOUT seconds before a single run is stopped (default: 900)
#
# Any further arguments go to the benchmarker, for example --target=60 or --warmup=false.
set -euo pipefail
cd "$(dirname "$0")"

GODOT="${GODOT:-/Applications/Godot.app/Contents/MacOS/Godot}"
RUN_TIMEOUT="${RUN_TIMEOUT:-900}"
LANGS="${LANGS:-gd kt${GODOT_MONO:+ cs}}"

# A run that hangs is stopped rather than left behind; `timeout` is GNU coreutils (Homebrew's on macOS).
if command -v timeout >/dev/null 2>&1; then
    limit=(timeout "$RUN_TIMEOUT")
elif command -v gtimeout >/dev/null 2>&1; then
    limit=(gtimeout "$RUN_TIMEOUT")
else
    echo "runAll.sh: no timeout command; runs are not time-limited" >&2
    limit=()
fi

for bench in BunnymarkSceneTree BunnymarkSprites BunnymarkDrawTexture BunnymarkScripts BunnymarkComputation; do
    for lang in $LANGS; do
        editor="$GODOT"
        if [[ "$lang" == "cs" ]]; then
            editor="${GODOT_MONO:?GODOT_MONO must point at the .NET build of Godot for C# runs}"
        fi
        echo "== $bench $lang"
        ${limit[@]+"${limit[@]}"} "$editor" --path . -- --bench="$bench" --lang="$lang" "$@"
    done
done
