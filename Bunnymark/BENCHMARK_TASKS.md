# Bunnymark Follow-Up Tasks

Use this list for future benchmark hardening. Keep benchmark methodology
changes separate from Kanama runtime/API performance fixes.

- Add a repeatable runner script that performs one warm-up pass and several
  measured passes per benchmark/language pair.
- Write raw run outputs to a machine-readable file before updating
  `BENCHMARK_RESULTS.md`.
- Report min, max, median, and run count instead of a single sample.
- Record exact Godot binary paths, Kanama commit, kanama-demos commit,
  Godot/JVM commit, Java versions, and machine details for each run.
- Keep windowed desktop runs as the primary comparison path; headless runs can
  be useful for smoke checks but should not replace rendering benchmarks.
- Re-run the full table after meaningful Kanama wrapper/runtime performance
  changes.
