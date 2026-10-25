# Comparing Runtimes with Bunnymark

How to measure the five Bunnymark benchmarks on four runtimes on one machine: GDScript, C#, godot-kotlin-jvm 1.0 and
Kanama. The scores go in `BENCHMARK_RESULTS.md` with the versions listed under [What to Record](#what-to-record).

## Before You Start

Bunnymark measures the frame rate of a window, so the whole machine shows in the score. Measure on a quiet machine:

- **Nothing else running.** No builds, no other Godot or Gradle processes (check with `ps aux | grep -i -e godot -e
  gradle`), no IDE indexing, no browser, no backups or Spotlight indexing in progress. Close other windows.
- **On the power adapter**, with Low Power Mode off, the lid open, and the Bunnymark window on the built-in display.
  Leave the window alone while it runs.
- **The same Godot build for every row**: Godot 4.7.2 stable for GDScript, godot-kotlin-jvm and Kanama, and the 4.7.2
  stable .NET build for C#. Check with `--version`; both should print the same commit (`ed1daf0bf`).
- **The same renderer for every row.** This project defaults to the Compatibility renderer, for phones and the Web;
  godot-kotlin-jvm's harness uses Forward+. Every command below passes `--rendering-method forward_plus`, so all four
  rows draw the same way.
- **The same JDK for both JVM runtimes.** Kanama needs JDK 25; godot-kotlin-jvm accepts it too. Set `JAVA_HOME` to it.
- **Rounds.** Run each benchmark three times per runtime, rotating the runtime order between rounds, and report the
  median. Let the machine idle for a minute between runtimes.

## One-Time Setup

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export GODOT=/Applications/Godot.app/Contents/MacOS/Godot
export GODOT_MONO=/Applications/Godot_mono.app/Contents/MacOS/Godot
export PATH=/usr/local/share/dotnet:$PATH          # the dotnet command, for the C# scripts
```

**Kanama, GDScript and C#**, in `kanama-demos/Bunnymark` with Kanama checked out beside `kanama-demos`:

```sh
./gradlew buildScripts importGodot
dotnet build Bunnymark.csproj -nologo -v quiet
```

The project targets `net8.0`. On this Mac only the .NET 10 SDK and runtime are installed: the SDK fetches the `net8.0`
reference pack on the first build, and the 4.7.2 .NET editor runs the scripts on the .NET 10 runtime with no change
to the project. Record the runtime version (`dotnet --list-runtimes`).

**godot-kotlin-jvm 1.0**, in `~/dev/github/godot-jvm`, following its README and `harness/bunnymark/README.md`:

```sh
git checkout stable                  # 1.0.0, abfe72bb; its harness/bunnymark matches the one ported here
git submodule update --init
scons platform=macos target=editor   # the native library, written into harness/tests/addons/jvm
cd harness/bunnymark
./gradlew build                      # builds the Kotlin side and copies the addon in
"$GODOT" --headless --import --path .
```

## The Runs

One run per command; each takes one to three minutes and prints `benchmark output: <bunnies>`. `<bench>` is one of
`BunnymarkSceneTree`, `BunnymarkSprites`, `BunnymarkDrawTexture`, `BunnymarkScripts` and `BunnymarkComputation`.

**GDScript** (in `kanama-demos/Bunnymark`):

```sh
timeout 900 "$GODOT" --rendering-method forward_plus --path . -- --bench=<bench> --lang=gd
```

**C#** (in `kanama-demos/Bunnymark`):

```sh
timeout 900 "$GODOT_MONO" --rendering-method forward_plus --path . -- --bench=<bench> --lang=cs
```

**Kanama** (in `kanama-demos/Bunnymark`):

```sh
timeout 900 "$GODOT" --rendering-method forward_plus --path . -- --bench=<bench> --lang=kt
```

**godot-kotlin-jvm 1.0** (in `godot-jvm/harness/bunnymark`):

```sh
timeout 900 "$GODOT" --rendering-method forward_plus --path . -- --bench=<bench> --lang=kt
```

`runAll.sh` runs every benchmark for the languages in `LANGS`, with any extra arguments passed on:

```sh
LANGS="gd kt cs" ./runAll.sh                   # GODOT and GODOT_MONO as exported above
```

It does not add `--rendering-method`; pass it to Godot yourself, or run the commands above.

Kanama writes each score to `~/Library/Application Support/Godot/app_userdata/Kanama Bunnymark/benchmark_results.json`
(godot-kotlin-jvm to `.../app_userdata/Bunnymark/`, keeping only the last run); copy the console line too, with
`| tee <bench>-<lang>-r1.log`.

GDScript and C# runs from this folder also start Kanama, since it is one of the project's extensions; it sits idle,
as godot-kotlin-jvm's JVM does in its own harness. For a run with no JVM in the process, copy the folder without
`addons/kanama` and `.godot`, import the copy, and run the GDScript or C# command there.

## What to Record

For each benchmark and runtime, in `BENCHMARK_RESULTS.md`:

- the median score of the three runs, and the lowest and highest;
- the machine (`sysctl -n machdep.cpu.brand_string`, memory), macOS version, display refresh rate, and that it ran
  on the adapter;
- `godot --version` for both editors, `java -version`, `dotnet --list-runtimes`, and the renderer;
- the commits: Kanama (`git -C ../../kanama rev-parse HEAD`), kanama-demos, and godot-jvm (`stable`, abfe72bb).

The scores are not comparable with the V1/V2/V3 tables already in `BENCHMARK_RESULTS.md`: those used the earlier
benchmarker, which searched for the target by adding and removing bunnies, with no warm-up and random seeds.

The numbers are for tuning Kanama; publishing them is a separate decision.
