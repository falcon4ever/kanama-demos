# Bunnymark Follow-Up Tasks

Use this list for future benchmark hardening. Keep benchmark methodology
changes separate from Kanama runtime/API performance fixes.

- Done in the 2026-10 refresh: the benchmarker warms Kotlin and C# up before
  measuring, `runAll.sh` runs every benchmark/language pair, and each score is
  kept in `benchmark_results.json` under its pair. Still open: several measured
  passes per pair in one command.
- Report min, max, median, and run count instead of a single sample.
- Record exact Godot binary paths, Kanama commit, kanama-demos commit,
  Godot/JVM commit, Java versions, and machine details for each run.
- Keep windowed desktop runs as the primary comparison path; headless runs can
  be useful for smoke checks but should not replace rendering benchmarks.
- Re-run the full table after meaningful Kanama wrapper/runtime performance
  changes.
