package net.multigesture.kanama.demos.bunnymark

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.OverrideVirtual
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node2D
import net.multigesture.kanama.api.RandomNumberGenerator
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.Texture2D
import net.multigesture.kanama.generated.benchmarkFinished
import net.multigesture.kanama.types.Color
import net.multigesture.kanama.types.Vector2

@ScriptClass(attachTo = "Node2D")
class BunnymarkComputationKanama(godotObject: GodotHandle) : KanamaScript<Node2D>(godotObject, ::Node2D) {

    @Signal
    fun benchmarkFinished(output: Long) = Unit

    private class Bunny(
        var position: Vector2,
        val center: Vector2,
        var phase: Double,
        val frequency: Double,
        val radius: Vector2,
    )

    private val bunnies = mutableListOf<Bunny>()
    private var bunnyTexture: Texture2D? = null
    private val spawnRandom = RandomNumberGenerator.create()

    // Zero until the first frame, as in GDScript: a run without a warm-up spawns its first bunnies before then.
    private var screenSize = Vector2(0.0, 0.0)

    @OnReady
    fun ready() {
        // Fixed seed rather than randomize(): every run then lays out the same orbits, so a difference in the
        // score is a difference in the code. This benchmark never bounces, so it needs no second generator.
        spawnRandom.seed = 20260921
        bunnyTexture = ResourceLoader.loadTexture2D("res://images/godot_bunny.png")
    }

    @OnExitTree
    fun exitTree() {
        bunnyTexture?.close()
        bunnyTexture = null
    }

    @OverrideVirtual
    fun _draw() {
        val texture = bunnyTexture ?: return
        for (bunny in bunnies) {
            self.drawTexture(texture, bunny.position, WHITE)
        }
    }

    // Mathf runs in Kotlin, so this loop stays inside the language runtime, as it does in the GDScript and C# versions.
    @OnProcess
    fun process(delta: Double) {
        screenSize = self.getViewportRect().size

        for (bunny in bunnies) {
            bunny.phase += bunny.frequency * delta
            val phase = bunny.phase

            val offsetX = Mathf.sin(phase) * bunny.radius.x
            val offsetY = Mathf.cos(phase * 1.7) * bunny.radius.y
            val distance = Mathf.sqrt(offsetX * offsetX + offsetY * offsetY)
            val angle = fastAtan2(offsetY, offsetX) + Mathf.sin(phase * 0.5) * 0.75

            bunny.position =
                Vector2(bunny.center.x + Mathf.cos(angle) * distance, bunny.center.y + Mathf.sin(angle) * distance)
        }
        self.queueRedraw()
    }

    fun addBunny() {
        bunnies.add(
            Bunny(
                Vector2(0.0, 0.0),
                Vector2(
                    spawnRandom.randf() * screenSize.x,
                    spawnRandom.randf() * screenSize.y,
                ),
                spawnRandom.randf() * 6.283185307179586,
                spawnRandom.randf() * 2.0 + 0.5,
                Vector2(
                    spawnRandom.randf() * screenSize.x / 8,
                    spawnRandom.randf() * screenSize.y / 8,
                ),
            ),
        )
    }

    fun removeBunny() {
        if (bunnies.isEmpty()) return
        bunnies.removeAt(bunnies.size - 1)
    }

    // atan2 is the one function here that HotSpot does not intrinsify: Math.atan2 falls through to StrictMath and the
    // pure-Java fdlibm port, while GDScript and C# reach their platform's native libm. This polynomial is the same in
    // all three languages so every runtime does the same arithmetic, and it is built only from operations that are
    // hardware-backed everywhere. Max error 1.1e-5 rad, about a hundredth of a pixel at a 1000 px radius.
    private fun atanUnit(z: Double): Double {
        val z2 = z * z
        return z * (0.9998660 + z2 * (-0.3302995 + z2 * (0.1801410 + z2 * (-0.0851330 + z2 * 0.0208351))))
    }

    private fun fastAtan2(y: Double, x: Double): Double {
        val ax = Mathf.abs(x)
        val ay = Mathf.abs(y)
        var a = if (ax >= ay) {
            atanUnit(if (ax == 0.0) 0.0 else ay / ax)
        } else {
            Mathf.PI * 0.5 - atanUnit(ax / ay)
        }
        if (x < 0.0) a = Mathf.PI - a
        return if (y < 0.0) -a else a
    }

    fun finish() {
        benchmarkFinished.emit(bunnies.size.toLong())
    }

    private companion object {
        // draw_texture's default modulate; Kanama's drawTexture takes it explicitly.
        val WHITE = Color(1.0, 1.0, 1.0, 1.0)
    }
}
