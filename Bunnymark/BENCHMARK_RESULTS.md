# Bunnymark Results

Preliminary one-pass desktop results for the Bunnymark demo. The benchmark adds
bunnies until the scene stabilizes near 60 FPS, so higher numbers are better.

## Benchmark Context

- Date: 2026-05-20
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

The engine versions differ, so these numbers are directional rather than a
strict apples-to-apples runtime comparison. The Godot/JVM V3 GDScript row needed
a local harness patch because `class_name Bunny` conflicts with the Godot/JVM
`Bunny.gdj` global script name.

## Results

| Benchmark | Kanama GDScript | Kanama Kotlin | Godot/JVM GDScript | Godot/JVM Kotlin |
| --- | ---: | ---: | ---: | ---: |
| V1 Sprites | 27,700 | 39,700 | 27,300 | 46,000 |
| V1 DrawTexture | 47,100 | 73,000 | 46,700 | 96,800 |
| V2 | 28,200 | 25,500 | 27,600 | 34,100 |
| V3 | 24,100 | 20,500 | 23,000 | 27,700 |

## Notes

- Kanama Kotlin V1 DrawTexture uses the typed `CanvasItem.drawTexture` wrapper.
  The older generic `call("draw_texture", ...)` path measured much lower.
- Kanama Kotlin sprite variants use typed texture loading and `Sprite2D`
  texture assignment.
- Godot/JVM V3 GDScript was measured after the local harness patch described
  above; without it, the row does not compile or run.
- Run a warm-up pass and several measured passes before treating these as final
  release-quality benchmark numbers.
