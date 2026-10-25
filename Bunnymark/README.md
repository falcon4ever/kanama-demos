# Bunnymark

Bunnymark adds bouncing bunny sprites until the frame rate drops to a target, 60 fps by default, and reports how many
bunnies the scene held. It mixes Godot API calls with plain computation, and each of its five benchmarks weights the
two differently. Every benchmark exists in GDScript, C# and Kotlin on Kanama, written so that all three do the same
work: the same data, the same seeded random numbers, the same calls.

It comes from the godot-kotlin-jvm project; see [Origin and License](#origin-and-license).

## Quick Start

From this folder, with the Kanama checkout beside `kanama-demos`:

```sh
./gradlew buildScripts importGodot
/Applications/Godot.app/Contents/MacOS/Godot --path . -- --bench=BunnymarkSceneTree --lang=kt
```

`--lang` is `gd`, `kt` or `cs`; a C# run needs the .NET build of Godot and `dotnet build Bunnymark.csproj` first.
`runAll.sh` runs every benchmark in every language. Each run prints `benchmark output: <bunnies>` and writes it to
`benchmark_results.json` in the project's user data folder (on macOS,
`~/Library/Application Support/Godot/app_userdata/Kanama Bunnymark/`). `BENCHMARK_HOWTO.md` has the full
cross-runtime comparison.

Opening the project in the editor and pressing Play runs the main scene, `BunnymarkSceneTree` in Kotlin; each
benchmark and language pair also has its own scene under `scenes/` to run from the editor.

## The Benchmarks

| Benchmark | What each bunny is | What it measures |
|---|---|---|
| `BunnymarkSceneTree` | a `Sprite2D` read back through `get_children()` every frame, plus a `Label` updated every frame | everyday scene-tree API use |
| `BunnymarkSprites` | a `Sprite2D` the script keeps in its own list | sprite updates through the API, with less API overhead |
| `BunnymarkDrawTexture` | a data object drawn with `draw_texture`, no nodes | one draw call per bunny, and rendering |
| `BunnymarkScripts` | a `Sprite2D` with its own script and `_process` | the cost of calling many small scripts |
| `BunnymarkComputation` | like DrawTexture, but orbiting a point, with seven trigonometric operations per bunny per frame | the language's own floating-point speed; nothing in its loop calls the engine |

`benchmarks/<name>/Description.md` describes each one in upstream's words.

## How a Run Is Measured

1. **Warm-up** (Kotlin and C# only, since GDScript does not compile as it runs): 20,000 bunnies for 20 seconds, then
   freed, then 5 idle seconds so the garbage collector clears them. `--warmup=false` skips it.
2. **First wave**: a third of the benchmark's expected score, set per language in its `scenes/` file. If that alone
   drops the frame rate below the target, the scene is cleared and the ramp starts from 200 bunnies instead.
3. **Ramp**: every second, from the frame rate it measures, the runner estimates how many bunnies would land exactly
   on the target and adds a third of the gap, at least 200. The frame after a spawn is skipped, since it pays for the
   spawning. Bunnies are never removed.
4. **Finish**: once the frame rate has stayed at or below the target for 6 seconds, the count is the score.

Options after `--`: `--bench=<name>`, `--lang=gd|kt|cs`, `--target=<fps>` and `--warmup=true|false`. On Android and
iOS the target is 30 fps unless `--target` says otherwise.

## Origin and License

Bunnymark is the godot-kotlin-jvm project's benchmark, ported to Kanama with thanks to its authors:

- Upstream: [utopia-rise/godot-jvm](https://github.com/utopia-rise/godot-jvm), path `harness/bunnymark`
- Refreshed from commit [`0b0599b3d13593300918b76ade57282e8adfa7f8`](https://github.com/utopia-rise/godot-jvm/tree/0b0599b3d13593300918b76ade57282e8adfa7f8/harness/bunnymark) (2026-10-18); the first port, recorded in
  `BENCHMARK_RESULTS.md`, came from `aff67e2a93c669cffb9575fd6dd6d66dc4a53e1c`
- License: MIT, © 2020 Godot-JVM, Utopia-Rise and Contributors; the full text is in [`LICENSE`](LICENSE)

The benchmarks, their GDScript (`benchmarks/*/gd/`) and C# (`benchmarks/*/cs/`) versions, the per-pair scenes and the
benchmarker's method are upstream's. **Changed for Kanama:** the Kotlin benchmarks are Kanama scripts in
`kotlin-src/` (`KanamaScript`, `self`, a typed `benchmark_finished` signal), and the benchmarker loads them from
there, keeps earlier results in its JSON file, uses a 30 fps target and the safe area on phones, and accepts
`--lang=kanama`, this port's earlier name for `kt`.

### Kotlin Port Notes

- **`BunnymarkScripts`** creates each bunny as a `Sprite2D`, attaches `BunnyKanama.kt` with `setScript`, and sets its
  speed through the script object, since a Kanama script has no `new()`.
- **Creating engine objects.** GDScript's `Label.new()` and `RandomNumberGenerator.new()` are `Label.create()` and
  `RandomNumberGenerator.create()`.
- **The bounce generator** is a field rather than upstream's static, because Kanama does not release a static
  reference before the engine exits. A run has one benchmark node, so the bunnies bounce the same way.
- **`BunnymarkComputation`** uses `kotlin.math`, not `Mathf`: `Mathf.sin` and `Mathf.cos` call into Godot, and this
  benchmark's loop must stay inside the language runtime.
