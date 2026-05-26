# Kanama Demos

Godot demo projects ported from GDScript to Kotlin with
[Kanama](https://github.com/falcon4ever/kanama).

<p>
  <img alt="Godot 4.7 beta 3" src="https://img.shields.io/badge/Godot-4.7_beta_3-478cbf.svg">
  <img alt="Kanama 0.1.0" src="https://img.shields.io/badge/Kanama-0.1.0-6f42c1.svg">
  <img alt="JDK 25+" src="https://img.shields.io/badge/JDK-25%2B-f89820.svg">
  <img alt="Status: experimental" src="https://img.shields.io/badge/status-experimental-yellow.svg">
  <img alt="Demo code: MIT" src="https://img.shields.io/badge/Kotlin%20ports-MIT-blue.svg">
</p>

This repository is the companion demo workspace for Kanama. Each project keeps
the original Godot scenes and assets, while gameplay scripts are ported to
Kotlin under `kotlin-src/`.

## Repository Layout

Keep this checkout next to `kanama`:

```text
dev/
  kanama/
  kanama-demos/
```

The demos are normal external Kanama consumer projects. They build scripts
through the Gradle wrapper from the sibling `kanama` checkout, matching the
setup documented in `kanama/docs/getting-started/index.md`.

## Requirements

- Godot 4.7 beta 3 from the
  [Godot 4.7 beta 3 archive](https://godotengine.org/download/archive/4.7-beta3/)
- JDK 25+
- CMake 3.22.1+ and a platform C toolchain for the Kanama source checkout's
  native bootstrap build; Godot source is not required
- A sibling `kanama` checkout next to this repository
- macOS arm64, Windows x64, Linux x64, or Linux ARM64 for the current
  editor/runtime smoke paths

## Quick Start

Before running a demo, install the requirements above and keep the demo
checkout next to the Kanama source checkout:

```text
dev/
  kanama/
  kanama-demos/
```

The `BuildAndRunGodot` demo tasks build Kotlin scripts, run Kanama's
`installAddonJar` task, import Godot assets, and then launch the demo. The
install step copies `kanama.jar`, `kanama-scripts.jar`, the `.gdextension`
file, and the host native bootstrap into the demo's `addons/kanama` directory.

List demo tasks:

```sh
./gradlew tasks --group "kanama demos"
```

Build all Kotlin demo scripts:

```sh
./gradlew buildAllScripts
```

Run the demo parity audit:

```sh
./gradlew demoParityAudit
```

Run the runtime and replication guardrail audits:

```sh
./gradlew runtimeNodeLookupAudit replicatedScriptPropertiesAudit
```

Run or open one demo:

```sh
./gradlew match3RunGodot
./gradlew thirdPersonBuildAndRunGodot
./gradlew characterControllerOpenGodotEditor
```

Use `<demo>BuildAndRunGodot` for the usual edit-run loop because it runs
`<demo>BuildScripts` and `<demo>ImportGodot` before launching Godot. If you use
`<demo>RunGodot`, `<demo>OpenGodotEditor`, or open a demo directly in Godot,
run `<demo>BuildScripts` first so the demo's `addons/kanama` directory is
current.
If `<demo>BuildAndRunGodot` builds successfully but does not launch Godot,
check the Godot executable path instead of rerunning the build step.

Override the Godot executable when needed:

```sh
./gradlew fpsRunGodot -Pkanama.godot.executable=/path/to/godot
KANAMA_GODOT=/path/to/godot ./gradlew fpsRunGodot
```

On Windows, run Gradle commands from PowerShell with `.\gradlew.bat`. The root
`buildAllScripts` task uses each demo's `gradlew.bat` when present and falls
back to Git Bash plus `gradlew` for demos that only have a Unix wrapper.

## IntelliJ Workspace

Open this repository root in IntelliJ to work across all demo projects from
one window. The root Gradle workspace includes every demo as a
composite build and exposes proxy tasks for common actions.

In IntelliJ, use the Gradle tool window or a Gradle run configuration to run
the same tasks shown above, for example `thirdPersonBuildAndRunGodot` or
`thirdPersonOpenGodotEditor`. Set the project Gradle JVM to JDK 25. If Godot is
not on `PATH`, add `-Pkanama.godot.executable=/absolute/path/to/godot` to the
Gradle run configuration or set `KANAMA_GODOT`.

The Gradle task must also be able to find `cmake`. On macOS, IntelliJ launched
from the Dock may not inherit your shell `PATH`; launch IntelliJ from a shell
that can run `cmake`, or configure IntelliJ/Gradle so CMake is on the Gradle
task environment.

The root Gradle workspace is the canonical way to work across the ports. Each
demo also keeps its own Gradle wrapper so aggregate builds can run demos
sequentially and each project can still be opened or built standalone.

## Demo Tasks

Common per-demo task patterns:

```sh
./gradlew <demo>BuildScripts
./gradlew <demo>RunGodot
./gradlew <demo>BuildAndRunGodot
./gradlew <demo>OpenGodotEditor
```

Examples:

```sh
./gradlew platformerBuildScripts
./gradlew match3BuildScripts
./gradlew cityBuilderBuildScripts
./gradlew fpsRunGodot
./gradlew racingOpenGodotEditor
```

Aggregate tasks:

```sh
./gradlew buildStarterKitScripts
./gradlew buildReferenceDemoScripts
./gradlew buildAllScripts
./gradlew demoParityAudit
./gradlew runtimeNodeLookupAudit
./gradlew replicatedScriptPropertiesAudit
./gradlew androidSmokeAll
```

The aggregate build tasks run demos sequentially because each demo currently
uses Kanama's shared `project-scripts` build to generate registrars.

For headless desktop smoke validation across the current scripted demos, use:

```sh
scripts/desktop_smoke_all.sh /path/to/godot
```

Use the matching Godot 4.7 beta 3 binary for the platform under test. Windows
smokes use the console binary with PowerShell Gradle commands and Git Bash
marker checks. The desktop smoke script imports each project before running so
fresh checkouts without `.godot/imported` caches validate the same way as a
previously opened project. Linux smokes should run with `JAVA_HOME` set to JDK
25 and an isolated `XDG_DATA_HOME`.

## Running In Godot

`runGodot` and `buildAndRunGodot` run the demo's configured main scene
directly. `buildAndRunGodot` imports assets first so fresh checkouts have the
`.godot/imported` cache Godot needs before game launch. By default, Gradle uses
`/Applications/Godot.app/Contents/MacOS/Godot` when it exists, then falls back
to `godot` from `PATH`.

`openGodotEditor` opens the project in the Godot editor instead. Once the
editor is open, use the Kanama Tools plugin's **Build Scripts** button before
pressing Play.

## Android Exports

Experimental Android export presets and smoke coverage are checked in for
eight demos:

- `godot-demo-2d-dodge-the-creeps`
- `Starter-Kit-3D-Platformer`
- `Starter-Kit-Match3`
- `godot-demo-3d-squash-the-creeps`
- `Starter-Kit-FPS`
- `Starter-Kit-Racing`
- `godot-4-3d-character-controller-tutorial`
- `godot-4-3d-third-person-controller`

These exports validate the Kanama Android plugin AAR path through emulator
smoke checks and Pixel 7 startup/playability checks. Several action demos
include Android-only touch overlays, D-pad controls, or virtual joysticks so
the smoke path can exercise real gameplay input on a phone. This is still
smoke/playability coverage, not a claim of complete phone UI polish, renderer
parity with desktop, or finished mobile game design for every demo.

The current demo set has also had manual playability checks on macOS desktop
and Pixel 7 Android. Keep those checks paired with the automated smoke path
before updating demo status or support wording.

Run all Android-enabled demo smokes with:

```sh
ANDROID_HOME=/path/to/android/sdk \
./gradlew androidSmokeAll -Pkanama.godot.executable=/path/to/godot
```

The Gradle task delegates to `scripts/android_smoke_all.sh`, which runs the
Kanama Android smoke script once per Android-enabled demo and writes APKs,
logcat captures, and screenshots under `/tmp/kanama-android-smokes` by default.

## Benchmarking

`Bunnymark` is a benchmark project for comparing GDScript and Kotlin script
throughput in a simple 2D stress test. It includes Kanama ports of the
Godot/JVM Bunnymark harness plus local GDScript baselines.

Run it the same way as the gameplay demos:

```sh
./gradlew bunnymarkBuildAndRunGodot
```

Current one-pass desktop results and the exact Godot/JVM commit used for
comparison are recorded in `Bunnymark/BENCHMARK_RESULTS.md`.

## Ported Demos

| Demo | Folder | Upstream | Status |
| --- | --- | --- | --- |
| 3D Platformer | `Starter-Kit-3D-Platformer` | Kenney Starter Kit 3D Platformer | Ported and smoke-tested |
| Match-3 | `Starter-Kit-Match3` | Kenney Starter Kit Match-3 | Ported and smoke-tested |
| FPS | `Starter-Kit-FPS` | Kenney Starter Kit FPS | Ported and smoke-tested |
| Racing | `Starter-Kit-Racing` | Kenney Starter Kit Racing | Ported and smoke-tested |
| City Builder | `Starter-Kit-City-Builder` | Kenney Starter Kit City Builder | Ported and smoke-tested |
| Third Person Controller | `godot-4-3d-third-person-controller` | GDQuest 3D Third Person Controller | Ported and smoke-tested |
| Character Controller Tutorial | `godot-4-3d-character-controller-tutorial` | GDQuest Character Controller Tutorial | Ported and smoke-tested |
| Squash the Creeps | `godot-demo-3d-squash-the-creeps` | Godot demo projects, 3D | Ported and smoke-tested |
| Dodge the Creeps | `godot-demo-2d-dodge-the-creeps` | Godot demo projects, 2D | Ported and smoke-tested |

## Sources And Licenses

| Folder | Upstream | License | Source |
| --- | --- | --- | --- |
| `Starter-Kit-3D-Platformer` | Kenney `Starter-Kit-3D-Platformer` | MIT | https://github.com/KenneyNL/Starter-Kit-3D-Platformer |
| `Starter-Kit-Match3` | Kenney `Starter-Kit-Match-3` | MIT | https://github.com/KenneyNL/Starter-Kit-Match-3 |
| `Starter-Kit-FPS` | Kenney `Starter-Kit-FPS` | MIT | https://github.com/KenneyNL/Starter-Kit-FPS |
| `Starter-Kit-Racing` | Kenney `Starter-Kit-Racing` | MIT | https://github.com/KenneyNL/Starter-Kit-Racing |
| `Starter-Kit-City-Builder` | Kenney `Starter-Kit-City-Builder` | MIT | https://github.com/KenneyNL/Starter-Kit-City-Builder |
| `godot-4-3d-third-person-controller` | GDQuest `godot-4-3d-third-person-controller` | MIT | https://github.com/gdquest-demos/godot-4-3d-third-person-controller |
| `godot-4-3d-character-controller-tutorial` | GDQuest `godot-4-3d-character-controller-tutorial` | Code MIT; assets CC BY 4.0; all else Copyright 2016-2026 GDQuest | https://github.com/gdquest-demos/godot-4-3d-character-controller-tutorial |
| `godot-demo-3d-squash-the-creeps` | Godot demo projects `3d/squash_the_creeps` | Code MIT; `House In a Forest Loop.ogg` CC BY 3.0; Montserrat OFL 1.1 | https://github.com/godotengine/godot-demo-projects/tree/master/3d/squash_the_creeps |
| `godot-demo-2d-dodge-the-creeps` | Godot demo projects `2d/dodge_the_creeps` | Code MIT; images CC0; `House In a Forest Loop.ogg` CC BY 3.0; Xolonium OFL 1.1 | https://github.com/godotengine/godot-demo-projects/tree/master/2d/dodge_the_creeps |
| `Bunnymark` | Godot/JVM Bunnymark harness | MIT | https://github.com/utopia-rise/godot-kotlin-jvm/tree/master/harness/bunnymark |

## Porting Guidelines

- Keep gameplay scripts under each demo's `kotlin-src/` directory.
- Prefer typed Kanama wrappers over raw `Object.call`, `Object.get`, or raw
  string signal wiring.
- Keep smoke-test behavior in dedicated `Smoke.kt` or `SmokeQuit.kt` scripts,
  not in gameplay scripts.
- Preserve original demo behavior unless a Kanama framework fix is needed.
- Add new wrapper coverage in Kanama first instead of papering over missing
  APIs inside demo code.

`demoParityAudit` scans the Kotlin ports for risky drift patterns such as raw
string signal wiring, raw calls where a typed wrapper exists, gameplay
companion-object facades, smoke logic in gameplay scripts, and Kotlin random
or math helpers where the original demo used Godot helpers.

## Smoke Scripts

Some demos include small Kotlin scripts named `Smoke.kt` or `SmokeQuit.kt`.
These are demo-local automated validation harnesses, not gameplay code. They
usually do nothing unless a `KANAMA_*_SMOKE` or
`KANAMA_DEMO_SMOKE_QUIT` environment variable is set by a headless test
launch.

## Adding A Demo

Add the upstream project as a subtree:

```sh
git subtree add --prefix=<Folder-Name> <upstream-url> main --squash
```

Then:

1. Port GDScript files to Kotlin under `<Folder-Name>/kotlin-src/`.
2. Add the demo Gradle setup and parity rules from
   `kanama/docs/contributing/demo-porting-rules.md`.
3. Add root proxy tasks in this repository's Gradle workspace.
4. Add focused smoke coverage for the main scene.
5. Update this README with upstream source and license details.

## Attribution

- **Kenney starter kits**: original projects, art, and audio by Kenney.
  https://kenney.nl/starter-kits
- **GDQuest demos**: original projects by the GDQuest team.
  https://www.gdquest.com/
- **Godot demo projects**: original demo code from the Godot project.
  https://github.com/godotengine/godot-demo-projects

Squash the Creeps and Dodge the Creeps include `House In a Forest Loop.ogg` by
HorrorPen under CC BY 3.0. Squash includes Montserrat Medium under the SIL Open
Font License 1.1. Dodge includes Kenney Abstract Platformer images under CC0
and Xolonium under the SIL Open Font License 1.1.

The Kotlin port layer sitting on top of these projects is the contribution
from this repository. Upstream art, audio, scenes, and imports retain their
original licenses.

## License

Each demo retains the upstream license. Kotlin port code is MIT; see
`LICENSE`.
