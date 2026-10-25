# Bulletmark

A benchmark of the binding paths a 2D game hits every frame: objects spawned and freed, a physics-process script per
entity, engine-to-script signal dispatch from physics, tweens, timers and lambda callables, deferred calls, and scene
queries that return arrays of objects.

The scene exists in three languages that play exactly the same game: Kotlin on Kanama (`kotlin-src/`), GDScript
(`gd/`) and C# (`cs/`). It comes from the godot-kotlin-jvm project; see [Origin and License](#origin-and-license).

![Bulletmark at frame 400: blue turrets on the left and orange turrets on the right fire into the lanes, the two streams meet in the middle, drones with evil faces and sensor rings drain as they are hit](readme/bulletmark.png)

## Quick Start

From this folder, with the Kanama checkout beside `kanama-demos` (or pass `-PkanamaRoot=/path/to/kanama`):

```sh
./gradlew buildScripts importGodot              # build the Kotlin scripts and import the project
./gradlew runBulletmark -Pheadless -Plabel=kt   # measure without a window, report in results/kt.json
./gradlew runBulletmark -Plabel=kt-window       # measure in a window
./gradlew watchBulletmark                       # watch it play in real time
```

Run `buildScripts` again after every Kotlin change: `runBulletmark` refuses to measure scripts older than
`kotlin-src/`. Every run is stopped after 15 minutes (`-Ptimeout-minutes=N` changes that).

The other two languages run the same way:

```sh
./gradlew runBulletmark -Pheadless -Plang=gd -Plabel=gd -Pbaseline=results/kt.json
./gradlew runBulletmark -Pheadless -Plang=cs -Plabel=cs -Pbaseline=results/kt.json
```

A C# run needs the .NET build of the Godot editor (`/Applications/Godot_mono.app`, or `-PgodotMono=` /
`GODOT_EDITOR_MONO`) and a .NET SDK; the task builds `Bulletmark.csproj` first. `BENCHMARK_HOWTO.md` has the full
cross-runtime comparison.

## The Scene

Two turret lines face each other and fire into partly shared lanes. Each bullet is an `Area2D` with its own script; it
moves in `_physics_process`, learns about hits through `area_entered`, and frees itself on its first hit or when its
lifetime runs out. In the shared middle lanes bullets meet head-on; in the outer lanes they cross the arena and expire.

Twelve drones patrol the middle of the arena, one script class doing everything the enemy side of a game would:

| Drone behaviour | Binding path it exercises |
|---|---|
| Patrols diagonally, crossing the lanes | A second per-entity `_physics_process` next to the bullets' |
| Every 15 frames, walks the bullets inside its sensor ring and the drones in its group, then steers away from close drones and speeds up the more bullets its ring holds | `getOverlappingAreas` and `getNodesInGroup`, each element delivered to the script as an object, then a `position` read per drone |
| Is hit through its own `area_entered` and flashes | A second signal per hit, and a two-step `Tween` |
| On death, splits into two smaller drones | `callDeferred`, because the engine refuses new areas during a collision signal |
| Respawns half a second after dying | A `SceneTreeTimer` whose timeout runs a lambda |

Every kill is recorded in a score map. The two drones a split leaves have a third of the hit points and neither split
nor respawn, so the population stays bounded. The arena looks up its `Bullets` and `Drones` containers by name every
frame.

Bullets are drawn either as a `Sprite2D` child each (`-Prender=nodes`, the default) or by the arena in one `_draw`
(`-Prender=draw`). Both modes play the same game. A drone's colour drains from the top as it loses hit points; that
drain is shown only in a window, so a headless run leaves the extra engine call per hit out of its measurement.

## How a Run Is Measured

Godot runs with `--fixed-fps 60`: every frame advances the game by exactly 1/60 s however long it takes to compute, and
every random choice comes from one seeded generator, so every run simulates the same game. Two runs spawn, hit and
kill exactly the same things; only the frame times differ.

The runner (`runner/BulletmarkRunner.gd`, the same for all three languages) discards 2,400 warm-up frames, then
records the wall time of 1,200 measured frames: physics, scripts, signals, tweens and timers included. Its JSON report
in `results/<label>.json` holds the settings, the machine and Godot version, p50, p95, p99, p99.9, the worst frame
and the mean, the hitches (frames over 16.7 ms), the live bullet count and the game's totals. It also records:

- **Script calls per frame**: every time the engine entered the scene's scripts, counted by the scripts themselves.
  The game is deterministic, so this is identical between runs and between languages.
- **Allocated bytes per frame, collections, GC and JIT time** for Kotlin (from the JVM) and C# (from .NET).
  GDScript reports none of these, since it frees by reference counting.

`-Pbaseline=results/<other>.json` compares a run with an earlier report. It allows a different language but refuses
a different game, so it also proves that two ports play the same game.

## Options

Each option is a Gradle property for `runBulletmark` and `watchBulletmark`; run directly with Godot, the same names are
runner options after `--` (`-Pframes=600` becomes `--frames=600`). `-Pheadless` tells `runBulletmark` to start Godot
without a window.

| Option | Default | Meaning |
|---|---|---|
| `-Plabel=<name>` | `run` | Name of the run, and of its report file |
| `-Plang=kt`, `cs` or `gd` | `kt` | The scene's language |
| `-Pwarmup-frames=N` | 2400 | Frames run before measuring, so the JIT has compiled the hot paths |
| `-Pframes=N` | 1200 | Measured frames |
| `-Pseed=N` | 42 | Seed of the arena's random generator |
| `-Pbullets-per-frame=N` | 40 | Bullets spawned every physics frame, split between the two sides |
| `-Plifetime-frames=N` | 150 | Frames before an unhit bullet expires |
| `-Pmax-live=N` | 10000 | Cap on live bullets; spawns past it are skipped and counted |
| `-Prender=nodes` or `draw` | `nodes` | A `Sprite2D` per bullet, or the arena drawing every bullet in one `_draw` |
| `-Phits=on` or `off` | `on` | Off, nothing detects collisions: no `area_entered` signals, no overlap work, no kills |
| `-Preactions=on` or `off` | `on` | Off, no `Tween` per hit, and respawns are counted down by the arena instead of by a timer and lambda |
| `-Pbookkeeping=on` or `off` | `on` | Off, no node lookups, groups, drone scans or score |
| `-Pbaseline=<path>` | none | Compares the frame times with a previous run's report, relative to the project |
| `-Presults-dir=<path>` | `res://results` | Where the JSON report is written |
| `-Pscreenshots=N,M` | none | Saves those frames as PNG; ignored when headless |
| `-Praw-samples` | none | Adds every measured frame time to the report |

A switch removes one part of the workload, so a change in frame time can be traced to the subsystem behind it. It
changes the game, so a run is only comparable with runs using the same switches.

## Origin and License

Bulletmark is the godot-kotlin-jvm project's benchmark, ported to Kanama with thanks to its authors:

- Upstream: [utopia-rise/godot-jvm](https://github.com/utopia-rise/godot-jvm), path `harness/bulletmark`
- Ported from commit [`0b0599b3d13593300918b76ade57282e8adfa7f8`](https://github.com/utopia-rise/godot-jvm/tree/0b0599b3d13593300918b76ade57282e8adfa7f8/harness/bulletmark) (2026-10-18)
- License: MIT, © 2020 Godot-JVM, Utopia-Rise and Contributors; the full text is in [`LICENSE`](LICENSE)

The scene, its counts and timings, the GDScript (`gd/`) and C# (`cs/`) sides and the runner's measurement are
upstream's. **Changed for Kanama:** the Kotlin side is rewritten as Kanama scripts in `kotlin-src/` (`KanamaScript`,
`self`, typed signals) with the arena class named `BulletArena`, the runner attaches the Kotlin arena to a node and
reads its totals as JSON, and the Gradle tasks start Godot with a timeout instead of godot-jvm's build and A/B
snapshot tooling.

### Kotlin Port Notes

The Kotlin scene plays the same game as the GDScript one, with the same script calls per frame. Where it differs from
upstream's Kotlin, it is because of how Kanama works:

- **`BulletArena`, not `Arena`.** Kanama's generated registration code imports `java.lang.foreign.Arena`, so a script
  class named `Arena` does not compile.
- **Spawning.** There is no `Bullet()` or `Bullet.new()` for a Kanama script: the arena creates an `Area2D`, attaches
  the preloaded script with `setScript`, and takes the script object with `asScript<Bullet>()`.
- **Creating engine objects.** Most Kanama wrappers have no `create()` yet, so `Nodes.kt` holds two small helpers for
  GDScript's `Area2D.new()` and `CircleShape2D.new()`.
- **The score** is a Kotlin map, as `var score := {}` becomes in a Kanama port; Kanama has no engine-backed Dictionary.
- **Totals for the runner.** A script function cannot return a Dictionary to Godot, so `stats_json()` and
  `runtime_counters_json()` return JSON text that the runner parses.
- **The respawn timer** connects its lambda to the arena, because a Kanama lambda connection ends when its owner is
  freed and the drone is freed before the timer fires.
