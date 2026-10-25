# Comparing Runtimes with Bulletmark

How to measure the same Bulletmark game on four runtimes on one machine: GDScript, C#, godot-kotlin-jvm 1.0 and
Kanama. The numbers go in `BENCHMARK_RESULTS.md` with the versions listed under [What to Record](#what-to-record).

## Before You Start

Frame times move by several percent with whatever else the machine does, so measure on a quiet machine:

- **Nothing else running.** No builds, no other Godot or Gradle processes (check with `ps aux | grep -i -e godot -e
  gradle`), no IDE indexing, no browser, no backups or Spotlight indexing in progress.
- **On the power adapter**, with Low Power Mode off, and the lid open if the run uses a window.
- **The same Godot build for every row**: Godot 4.7.2 stable for GDScript, godot-kotlin-jvm and Kanama, and the 4.7.2
  stable .NET build for C#. Check with `--version`; both should print the same commit (`ed1daf0bf`).
- **The same JDK for both JVM runtimes.** Kanama needs JDK 25; godot-kotlin-jvm accepts it too. Set `JAVA_HOME`
  to it in every shell you use.
- **Headless.** Compare headless runs (`-Pheadless`); a report records its mode, and a window run is never compared
  with a headless one.
- **Rounds.** Run three rounds of the four runtimes, rotating the order each round (GDScript, C#, JVM, Kanama, then C#,
  JVM, Kanama, GDScript, and so on), and report the median of the three.

## One-Time Setup

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export GODOT=/Applications/Godot.app/Contents/MacOS/Godot
export GODOT_MONO=/Applications/Godot_mono.app/Contents/MacOS/Godot
export PATH=/usr/local/share/dotnet:$PATH          # the dotnet command, for the C# scripts
```

**Kanama, GDScript and C#**, in `kanama-demos/Bulletmark` with Kanama checked out beside `kanama-demos`:

```sh
./gradlew buildScripts importGodot
dotnet build Bulletmark.csproj -nologo -v quiet
```

The project targets `net8.0`. On this Mac only the .NET 10 SDK and runtime are installed: the SDK fetches the `net8.0`
reference pack on the first build, and the 4.7.2 .NET editor runs the scripts on the .NET 10 runtime with no change
to the project. Record the runtime version it reports (`dotnet --list-runtimes`).

**godot-kotlin-jvm 1.0**, in `~/dev/github/godot-jvm`, following its README and `harness/bulletmark/README.md`:

```sh
git checkout stable                  # 1.0.0, abfe72bb; its harness/bulletmark matches the one ported here
git submodule update --init
scons platform=macos target=editor   # the native library, written into harness/tests/addons/jvm
cd harness/bulletmark
./gradlew importResources            # builds the Kotlin side and imports the project
```

## The Runs

Each command measures 2,400 warm-up frames and 1,200 measured frames, about half a minute, and writes
`results/<label>.json`. Replace `r1` with the round number.

**GDScript** (in `kanama-demos/Bulletmark`):

```sh
./gradlew runBulletmark -Pheadless -Plang=gd -Plabel=gd-r1
```

**C#** (in `kanama-demos/Bulletmark`), through Gradle, which builds the scripts first:

```sh
./gradlew runBulletmark -Pheadless -Plang=cs -Plabel=cs-r1
```

or with the .NET editor directly, after `dotnet build`:

```sh
timeout 900 "$GODOT_MONO" --headless --fixed-fps 60 --path . -- --lang=cs --label=cs-r1
```

**Kanama** (in `kanama-demos/Bulletmark`):

```sh
./gradlew runBulletmark -Pheadless -Plabel=kanama-r1 -Pbaseline=results/gd-r1.json
```

`-Pbaseline` checks that the Kotlin port played the same game as GDScript: the summary says `NOT COMPARABLE` if it
did not.

**godot-kotlin-jvm 1.0** (in `godot-jvm/harness/bulletmark`):

```sh
./gradlew runBulletmark -Pheadless -Plabel=jvm-r1
```

Its report lands in that folder's `results/`; copy it beside the others. All four reports must show the same `game`
totals and the same `script_calls_per_frame`.

GDScript and C# runs from this folder also start Kanama, since it is one of the project's extensions; it sits idle,
as godot-kotlin-jvm's JVM does in its own harness. For a run with no JVM in the process, copy the folder without
`addons/kanama` and `.godot`, import the copy, and run the GDScript or C# command there.

The Gradle tasks in this folder stop Godot after 15 minutes. godot-jvm's own task has no timeout, so watch it, or run
its Godot command (printed as the task's first line) under `timeout 900`.

## What to Record

For each runtime, in `BENCHMARK_RESULTS.md`:

- the p50, p99, p99.9 and worst frame (medians of the three rounds), the hitches, and for Kotlin and C# the allocation
  per frame and the GC counts;
- the machine (`sysctl -n machdep.cpu.brand_string`, memory), macOS version, and that it ran on the adapter;
- `godot --version` for both editors, `java -version`, `dotnet --list-runtimes`;
- the commits: Kanama (`git -C ../../kanama rev-parse HEAD`), kanama-demos, and godot-jvm (`stable`, abfe72bb);
- the settings block of one report, to show every run used the defaults.

The numbers are for tuning Kanama; publishing them is a separate decision.
