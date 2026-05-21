# Bunnymark Results

Desktop results for the Bunnymark demo. The benchmark adds bunnies until the
scene stabilizes near 60 FPS, so higher numbers are better.

## Latest Cross-Runtime Snapshot

This table lines up the latest Kanama-side numbers with the latest Godot/JVM
same-binary numbers. The Godot/JVM columns come from one custom
Godot/JVM+Mono 4.6.2 binary. The Kanama GDScript and Kanama Kotlin columns come
from the Kanama Godot 4.7 beta 2 demo run; the Kanama-side C# column is the
separate Godot 4.7 beta 2 Mono C# run because Kanama itself does not provide a
C# runtime.

| Benchmark | Kanama GDScript | Kanama Kotlin | Kanama-side C# | Godot/JVM GDScript | Godot/JVM Kotlin | Godot/JVM C# |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| V1 Sprites | 28,100 | 51,100 | 48,200 | 27,500 | 46,400 | 47,000 |
| V1 DrawTexture | 47,100 | 182,266 | 151,354 | 47,100 | 93,900 | 149,867 |
| V2 | 28,700 | 38,600 | 19,300 | 27,700 | 33,700 | 19,000 |
| V3 | 24,200 | 32,400 | 8,500 | 23,500 | 28,600 | 8,500 |

## 2026-05-21 Godot/JVM + Mono Same-Binary Comparison Pass

- Hardware: Apple M1 Max, 64 GB RAM, Metal 4.0
- Method: windowed runs, one pass per row, no dedicated warm-up pass
- Engine: Godot 4.6.2 with Godot/JVM 0.16.0 and Mono enabled
  (`4.6.2.stable.jvm.0.16.0.mono.custom_build.001aa128b`)
- Godot source:
  `001aa128b1cd80dc4e47e823c360bccf45ed6bad`
- Godot/JVM module:
  `2392b6c97579105cba32c2d5b8ac1ca5a42a0dc7`
- Java: OpenJDK 21
  (`/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`)
- .NET SDK: `10.0.300`
- C# source: copied from the Godot/JVM Bunnymark harness into
  `/private/tmp/kanama-bunnymark-godotjvm-20260521`, then
  compatibility-patched for Godot 4 C# APIs and built against the local
  `Godot.NET.Sdk/4.6.2` package produced from this engine build.

This pass rebuilds a single macOS editor binary with both Godot/JVM and Mono
support, then runs GDScript, Godot/JVM Kotlin, and C# through that same binary.
It is the fairest cross-language comparison in this file because all three
columns share the same engine executable and renderer.

Current table:

| Benchmark | GDScript | Godot/JVM Kotlin | C# |
| --- | ---: | ---: | ---: |
| V1 Sprites | 27,500 | 46,400 | 47,000 |
| V1 DrawTexture | 47,100 | 93,900 | 149,867 |
| V2 | 27,700 | 33,700 | 19,000 |
| V3 | 23,500 | 28,600 | 8,500 |

Notes:

- This run required a custom local Godot/JVM+Mono build, Mono glue generation,
  and C# assembly packaging into `bin/GodotSharp`.
- Godot/JVM runtime required `JAVA_HOME` to point at the real JDK home, not the
  Homebrew formula root.
- The C# harness was old Godot 3-era code. The temporary compatibility patch
  updated `_Process(float)` to `_Process(double)`, `Vector2.x/y` to
  `Vector2.X/Y`, `Update()` to `QueueRedraw()`, the V3 texture/signal API
  usage, and the project SDK/target framework for Godot 4.6.2.
- C# V1 Sprites wrote a valid result and then crashed during shutdown/finalizer
  cleanup with a leaked texture reference. All hybrid runs also printed
  shutdown leak warnings after writing their benchmark output.

## 2026-05-21 Godot 4.7 Beta 2 C# Comparison Pass

- Hardware: Apple M1 Max, 64 GB RAM, Metal 4.0
- Method: windowed runs, one pass per row, no dedicated warm-up pass
- Kanama engine: Godot 4.7 beta 2
  (`4.7.beta2.official.777579205`)
- Kanama Java: OpenJDK 25.0.3
- Kanama runtime:
  `8b8cdef41adfeab90d248b7ffa7932f6cfcd5e9e`
- Kanama demos:
  `9ec135959ac3d52f25733d99cd70632cdc194377`
- C# engine: Godot 4.7 beta 2 Mono
  (`4.7.beta2.mono.official.777579205`)
- C# .NET SDK: `10.0.300`
- C# source: copied from the Godot/JVM Bunnymark harness at
  `2392b6c97579105cba32c2d5b8ac1ca5a42a0dc7` into
  `/private/tmp/kanama-bunnymark-cs-20260521`, then compatibility-patched for
  Godot 4.7 C# APIs. The original Godot/JVM working tree was already dirty and
  was not modified.

This pass reruns the Kanama Bunnymark rows after the retained-resource wrapper
lifetime fix and adds a directional C# comparison. GDScript and Kanama Kotlin
use the Kanama Bunnymark project and the non-Mono Godot 4.7 beta 2 binary. C#
uses Godot's Mono binary because C# requires it, so the C# column is comparable
as same-machine Godot 4.7 data, but not a perfectly identical runtime
environment.

Current table:

| Benchmark | Kanama GDScript | Kanama Kotlin | Godot C# |
| --- | ---: | ---: | ---: |
| V1 Sprites | 28,100 | 51,100 | 48,200 |
| V1 DrawTexture | 47,100 | 182,266 | 151,354 |
| V2 | 28,700 | 38,600 | 19,300 |
| V3 | 24,200 | 32,400 | 8,500 |

Notes:

- The Kanama Kotlin rows are in the same range or slightly above the
  2026-05-20 current table, so the retained-resource lifetime fix did not show
  a Bunnymark regression in this pass.
- The C# harness was old Godot 3-era code. The temporary compatibility patch
  updated `_Process(float)` to `_Process(double)`, `Vector2.x/y` to
  `Vector2.X/Y`, `Update()` to `QueueRedraw()`, and the V3 signal/texture API
  usage for Godot 4.7 C#.
- C# V1 Sprites wrote a valid result and then Godot Mono reported a
  texture/resource leak and crashed during shutdown. The benchmark output was
  already written, but that row should be treated as having an exit-time Mono
  or harness caveat.

## 2026-05-20 Runtime Performance Pass

- Hardware: Apple M1 Max, 64 GB RAM, Metal 4.0
- Method: windowed runs. The original runtime-pass Kanama Kotlin rows used
  one warm-up/probe pass and two direct measured passes per row, publishing
  the lower measured value for each rerun row. A later hot-path supplemental
  pass updates only the V2 and V3 rows, where clean probes improved.
- Kanama engine: Godot 4.7 beta 2
- Kanama Java: OpenJDK 25.0.3
- Kanama runtime: original full-pass rows used
  `beafe6dcd47cea2c68c8f543368f461cff74f288`; supplemental V2/V3 probes
  used `3cb81a33f34d3fb8e313ed0fd0f7962ab067516c`.
- Kanama demos: benchmark source came from
  `28639bc6121913c273fda8b3e7d141e1cb2e8106`; later commits in this
  section update only this results document.
- Godot/JVM repo: <https://github.com/utopia-rise/godot-kotlin-jvm>
- Godot/JVM commit: [`aff67e2a93c669cffb9575fd6dd6d66dc4a53e1c`](https://github.com/utopia-rise/godot-kotlin-jvm/commit/aff67e2a93c669cffb9575fd6dd6d66dc4a53e1c)
- Godot/JVM engine: Godot 4.6.2 JVM 0.16.0
- Godot/JVM Java: OpenJDK 21

This pass records the first Kanama runtime performance improvement cycle after
the initial benchmark table. The Bunnymark demo source stayed representative;
the gains came from shared Kanama runtime and generated-dispatch hot paths.

Performance learnings since the initial recorded benchmark:

| Benchmark | Previous Kanama Kotlin | Current Kanama Kotlin | Change |
| --- | ---: | ---: | ---: |
| V1 Sprites | 39,700 | 50,400 | +10,700 |
| V1 DrawTexture | 73,000 | 171,273 | +98,273 |
| V2 | 25,500 | 37,800 | +12,300 |
| V3 | 20,500 | 31,200 | +10,700 |

- **Direct lifecycle dispatch pays off broadly.** KSP-generated registrars now
  expose direct `_process(delta)` and `_physics_process(delta)` lambdas, so
  `ScriptBridge.call_func` can skip the generic generated method switch for
  frame callbacks. This helps every script that runs per frame, and it shows up
  most clearly in V3 where each bunny is its own Kanama script.
- **Per-call FFM allocation is expensive in render loops.** Selected hot
  `ObjectCalls` ptrcall wrappers reuse small thread-local scratch buffers
  instead of allocating a fresh confined arena on every call. This is the main
  reason V1 DrawTexture moved so much: the draw loop calls into Godot once per
  bunny per frame. These scratch buffers are reusable native argument/return
  slots for synchronous ptrcalls, not cached Godot objects or cached benchmark
  results.
- **V1 Sprites improved through the same shared wrapper path.** The benchmark
  keeps one Kanama script on the root but still performs high-volume typed
  wrapper calls for viewport bounds and sprite position updates. Scratch-backed
  `Rect2` and primitive/small-struct ptrcall helpers reduce the tiny native
  allocations around those repeated Kotlin-to-Godot calls, so the row benefits
  without changing the Sprite2D-per-bunny benchmark shape.
- **Typed container decoding should avoid wrapper churn.** Typed object-array
  decoding can wrap directly as the requested Godot type, avoiding a temporary
  `GodotObject` wrapper for each element. This helps normal scene-tree code
  such as `Node.getChildren()`, which V2 exercises every frame.
- **Supplemental V2 hot-path gains came from child-list decoding.**
  `Node.getChildren()` now calls the typed node-list ptrcall helper directly,
  and the bool-argument Array-return helper uses thread-local scratch storage.
  V2 exercises that path every frame before updating child sprite positions, so
  it benefits from avoiding both temporary `GodotObject` wrappers and repeated
  tiny FFM allocations.
- **Supplemental V3 gains came after the callback lookup path.** Dense
  `ObjectRegistry` lookup makes the common ScriptInstance handle path cheaper,
  while `getViewportRect()` uses scratch-backed `Rect2` return storage. A sample
  run still showed most V3 cost at the Godot-to-JVM Panama upcall boundary, so
  this row improved by reducing work after each callback rather than removing
  the callback cost itself.
- **Benchmark code should stay ordinary.** These improvements came from shared
  runtime paths and generated dispatch policy, not benchmark-specific rewrites.
  That keeps Bunnymark useful as a proxy for real Kanama game code.

Current table:

| Benchmark | Kanama GDScript | Kanama Kotlin | Godot/JVM GDScript | Godot/JVM Kotlin |
| --- | ---: | ---: | ---: | ---: |
| V1 Sprites | 27,700 | 50,400 | 27,300 | 46,000 |
| V1 DrawTexture | 47,100 | 171,273 | 46,700 | 96,800 |
| V2 | 28,200 | 37,800 | 27,600 | 34,100 |
| V3 | 24,100 | 31,200 | 23,000 | 27,700 |

## Notes

- Kanama Kotlin V1 DrawTexture uses the typed `CanvasItem.drawTexture` wrapper.
  The older generic `call("draw_texture", ...)` path measured much lower.
- Kanama Kotlin measured ranges for the full runtime pass: V1 Sprites
  50,400-51,600 (warm-up/probe 52,100), V1 DrawTexture 171,273-187,654,
  V2 36,000-36,400, and V3 26,000-29,400.
- Supplemental hot-path probes after `3cb81a3`: V1 Sprites 49,700-50,500
  and V1 DrawTexture 168,532 stayed in the same ballpark but did not beat
  their table rows; V2 37,800 and V3 31,200 replace the previous V2/V3
  table rows.
- Kanama Kotlin sprite variants use typed texture loading and `Sprite2D`
  texture assignment.
- Godot/JVM and GDScript rows were not rerun during this Kanama runtime
  performance pass; they remain from the initial recorded table below.

## 2026-05-20 Initial Desktop Pass

- Hardware: Apple M1 Max, 64 GB RAM, Metal 4.0
- Method: windowed runs, one pass per row, no dedicated warm-up pass
- Kanama engine: Godot 4.7 beta 2
- Kanama Java: OpenJDK 25
- Kanama runtime: local working tree based on `eaf8507d88a96a22c1ba0d1dcbdd127e3987911b`
- Kanama demos: local working tree based on `25a6e5208f9ca994590e6024d80a501c9988e1d4`
- Godot/JVM repo: <https://github.com/utopia-rise/godot-kotlin-jvm>
- Godot/JVM commit: [`aff67e2a93c669cffb9575fd6dd6d66dc4a53e1c`](https://github.com/utopia-rise/godot-kotlin-jvm/commit/aff67e2a93c669cffb9575fd6dd6d66dc4a53e1c)
- Godot/JVM engine: Godot 4.6.2 JVM 0.16.0
- Godot/JVM Java: OpenJDK 21

Initial table:

| Benchmark | Kanama GDScript | Kanama Kotlin | Godot/JVM GDScript | Godot/JVM Kotlin |
| --- | ---: | ---: | ---: | ---: |
| V1 Sprites | 27,700 | 39,700 | 27,300 | 46,000 |
| V1 DrawTexture | 47,100 | 73,000 | 46,700 | 96,800 |
| V2 | 28,200 | 25,500 | 27,600 | 34,100 |
| V3 | 24,100 | 20,500 | 23,000 | 27,700 |

Notes:

- Kanama Kotlin V1 DrawTexture used the typed `CanvasItem.drawTexture` wrapper.
  The older generic `call("draw_texture", ...)` path measured much lower.
- Kanama Kotlin sprite variants used typed texture loading and `Sprite2D`
  texture assignment.
- Godot/JVM V3 GDScript was measured after the local harness patch described
  above; without it, the row does not compile or run.

The engine versions differ, so cross-runtime numbers are directional rather
than a strict apples-to-apples runtime comparison.

## Benchmark Shapes

These rows are not interchangeable microbenchmarks. Each one stresses a
different mix of rendering, wrapper calls, and Godot/Kanama boundary crossings.

- **V1 Sprites** keeps one Kanama script on the benchmark root and creates one
  Godot `Sprite2D` per bunny. The per-frame loop stays in Kotlin, but each
  bunny updates Godot through typed wrapper calls such as `Sprite2D.position`
  get/set. This row mainly tests high-volume Kotlin -> Godot ptrcalls on normal
  scene nodes.
- **V1 DrawTexture** keeps bunny state in Kotlin data objects and draws every
  bunny from one root script with `CanvasItem.drawTexture`. It avoids one Godot
  node per bunny, but it performs one typed draw ptrcall per bunny per frame.
  This row is especially sensitive to per-call FFM allocation and argument
  marshalling overhead.
- **V2** creates Godot `Sprite2D` children under a container and asks the scene
  tree for those children each frame with `Node.getChildren()`. It stresses
  typed object-array decoding, temporary wrapper allocation, and the same
  Kotlin -> Godot position updates as V1 Sprites.
- **V3** attaches a Kanama script to every bunny sprite. Each bunny receives its
  own `_process(delta)` callback from Godot, so this row stresses Godot ->
  Kanama ScriptInstance dispatch, Variant `delta` decoding, and per-script
  wrapper calls. It is the callback-density benchmark and is expected to be the
  hardest row for Kanama.
