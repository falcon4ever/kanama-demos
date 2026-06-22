# Agent Guide

This repository contains external Godot projects ported to Kanama. Use this
guide to work on demos without scanning every project directory first.

## Current Baseline

- Kanama version: `0.2.2`.
- Godot baseline: Godot `4.7 stable`.
- Desktop/runtime JDK: JDK `25+`.
- Default layout keeps this checkout beside `kanama`:

  ```text
  dev/
    kanama/
    kanama-demos/
  ```

Confirm this section against `README.md` before changing release or support
wording.

## Read First By Task

- Repo status, requirements, task names, demo list, licenses, and Android
  notes: `README.md`.
- Root aggregate tasks and demo prefixes: `build.gradle.kts`.
- Shared per-demo Kanama Gradle wiring: `gradle/kanama-demo.gradle.kts`.
- GDScript porting and gameplay rules:
  `../kanama/docs/contributing/demo-porting-rules.md`,
  `../kanama/docs/game-dev/porting-gdscript.md`, and
  `../kanama/docs/game-dev/scripts.md`.
- Kanama API usage: `../kanama/docs/game-dev/godot-api.md`,
  `../kanama/docs/game-dev/properties-resources.md`, and
  `../kanama/docs/game-dev/signals.md`.
- For TPS-specific work: `tps-demo-kanama/README.md`,
  `tps-demo-kanama/PORTING_NOTES.md`, and `tps-demo-kanama/UPSTREAM.md`.
- For benchmark work: `Bunnymark/BENCHMARK_RESULTS.md` and
  `Bunnymark/BENCHMARK_TASKS.md`.

Use targeted `rg` searches after reading the relevant files above. Do not
start by loading every demo's `kotlin-src/` tree.

## Demo Map

| Task prefix | Folder | Notes |
| --- | --- | --- |
| `bunnymark` | `Bunnymark` | Benchmark harness. |
| `platformer` | `Starter-Kit-3D-Platformer` | Kenney starter kit. |
| `match3` | `Starter-Kit-Match3` | Kenney starter kit. |
| `fps` | `Starter-Kit-FPS` | Kenney starter kit. |
| `racing` | `Starter-Kit-Racing` | Kenney starter kit. |
| `cityBuilder` | `Starter-Kit-City-Builder` | Kenney starter kit. |
| `dodge` | `godot-demo-2d-dodge-the-creeps` | Godot official demo. |
| `squash` | `godot-demo-3d-squash-the-creeps` | Godot official demo. |
| `characterController` | `godot-4-3d-character-controller-tutorial` | GDQuest demo. |
| `thirdPerson` | `godot-4-3d-third-person-controller` | GDQuest demo. |
| `tps` | `tps-demo-kanama` | Godot TPS demo. |

Per-demo task patterns are:

```sh
./gradlew <prefix>BuildScripts
./gradlew <prefix>ImportGodot
./gradlew <prefix>RunGodot
./gradlew <prefix>BuildAndRunGodot
./gradlew <prefix>OpenGodotEditor
```

Use `<prefix>BuildAndRunGodot` for normal edit/run work because it builds
Kotlin scripts and imports Godot assets before launching.

## Repository Rules

- Preserve upstream scenes, assets, gameplay semantics, source attribution, and
  license notes unless the task explicitly changes them.
- Put Kanama gameplay ports under each demo's `kotlin-src/`.
- Keep smoke harness code in `Smoke.kt` or `SmokeQuit.kt`; do not mix smoke
  behavior into gameplay scripts.
- Prefer typed Kanama wrappers over raw `Object.call`, `Object.get`,
  `Object.set`, and string signal wiring when a typed API exists.
- If a port exposes missing framework support, fix Kanama first instead of
  adding a demo-specific workaround that changes behavior.
- Do not commit Godot import caches, build outputs, crash logs, or local IDE
  state. Tracked `.uid`, `addons/kanama/kanama.gdextension`, and selected
  `.godot/extension_list.cfg` files can be intentional; review generated
  changes instead of deleting them blindly.
- Keep `README.md` badges, root Gradle defaults, and special demo defaults in
  sync with the active Kanama release.

## Kanama Inputs

By default, demos use a sibling Kanama source checkout:

```sh
./gradlew buildAllScripts
```

Override the Kanama checkout when needed:

```sh
./gradlew buildAllScripts -PkanamaRoot=/absolute/path/to/kanama
KANAMA_ROOT=/absolute/path/to/kanama ./gradlew buildAllScripts
```

Build from an unzipped desktop kit instead of a source checkout:

```sh
./gradlew buildAllScripts -PkanamaKitDir=/absolute/path/to/kanama-starter
```

Override Godot:

```sh
./gradlew fpsBuildAndRunGodot -Pkanama.godot.executable=/absolute/path/to/godot
KANAMA_GODOT=/absolute/path/to/godot ./gradlew fpsBuildAndRunGodot
```

## Common Workflows

### Update For A New Kanama Release

Update these together:

- `README.md` badges and requirements.
- `gradle/kanama-demo.gradle.kts` default `kanamaVersion`.
- Any special demo Gradle defaults, especially
  `tps-demo-kanama/build.gradle.kts`.
- Porting notes that name the previous Godot or Kanama baseline.
- Smoke results or support wording only after the matching smoke path passes.

Then run:

```sh
./gradlew buildAllScripts
./gradlew check
scripts/desktop_smoke_all.sh /absolute/path/to/godot-4.7-stable
```

### Port Or Fix Gameplay

Start from the original scene and GDScript behavior. Preserve exported
property names, signal names, node paths, scene-connected method names, and
resource references. Use `@ScriptClass`, lifecycle annotations, `@ScriptProperty`,
`@RegisterFunction`, and generated signal helpers as documented in Kanama.

Run the narrow demo first:

```sh
./gradlew <prefix>BuildScripts
./gradlew <prefix>BuildAndRunGodot
```

Then run the aggregate checks if the change affects shared patterns.

### Add A Demo

Record upstream source, license, and commit information in the demo folder and
in `README.md`. Add root proxy tasks in `build.gradle.kts`, use the shared
Gradle script where possible, add focused smoke coverage, and extend parity
audits when a new drift pattern appears.

## Validation

Run the narrowest useful check while iterating:

```sh
./gradlew <prefix>BuildScripts
./gradlew demoParityAudit
./gradlew runtimeNodeLookupAudit replicatedScriptPropertiesAudit
```

Before release-facing changes:

```sh
./gradlew buildAllScripts
./gradlew check
./gradlew importAllGodot -Pkanama.godot.executable=/absolute/path/to/godot-4.7-stable
scripts/desktop_smoke_all.sh /absolute/path/to/godot-4.7-stable
```

Run Android validation only for Android export work or support-claim changes:

```sh
ANDROID_HOME=/absolute/path/to/android/sdk \
./gradlew androidSmokeAll -Pkanama.godot.executable=/absolute/path/to/godot
```
