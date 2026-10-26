package net.multigesture.kanama.demos.bunnymark

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node2D
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.Sprite2D
import net.multigesture.kanama.api.Texture2D
import net.multigesture.kanama.generated.benchmarkFinished
import net.multigesture.kanama.types.Vector2
import kotlin.random.Random

@ScriptClass(attachTo = "Node2D")
// Web copy of kotlin-src/BunnymarkSpritesKanama.kt: the Web backend has no RandomNumberGenerator wrapper yet, so the
// two generators are kotlin.random.Random with the same seeds and the same distributions (a uniform float in [0, 1),
// a uniform float in a range, a 32-bit unsigned integer), drawn in Kotlin with no bridge call. The sequences are
// Kotlin's, not Godot's PCG, so a Web run spawns and bounces a different, equally fixed population. Delete this
// override when Web has RandomNumberGenerator.
class BunnymarkSpritesKanama(godotObject: GodotHandle) : KanamaScript<Node2D>(godotObject, ::Node2D) {

    @Signal
    fun benchmarkFinished(output: Long) = Unit

    private class Bunny(val sprite: Sprite2D, var speed: Vector2)

    private val bunnies = mutableListOf<Bunny>()
    private val gravity = 500.0
    private var bunnyTexture: Texture2D? = null
    private val spawnRandom = Random(SPAWN_SEED)
    // One generator for every bounce in the run, kept apart from the spawn generator so that the bounce sequence
    // does not shift when the ramp happens to spawn a different number of bunnies. A field rather than upstream's
    // static: a run has one benchmark node, and Kanama does not release a static reference before the engine exits.
    private val bounceRandom = Random(BOUNCE_SEED)

    // Zero until the first frame, as in GDScript: a run without a warm-up spawns its first bunnies before then.
    private var screenSize = Vector2(0.0, 0.0)

    @OnReady
    fun ready() {
        // Fixed seeds rather than randomize(): every run then spawns the same population and bounces it the same
        // way, so a difference in the score is a difference in the code. Spawning and bouncing draw from separate
        // generators so that the bounce sequence does not shift when the ramp happens to spawn a different number
        // of bunnies, which it does whenever the machine is a little faster or slower.
        bunnyTexture = ResourceLoader.loadTexture2D("res://images/godot_bunny.png")
    }

    @OnExitTree
    fun exitTree() {
        bunnyTexture?.close()
        bunnyTexture = null
    }

    @OnProcess
    fun process(delta: Double) {
        screenSize = self.getViewportRect().size

        for (bunny in bunnies) {
            val position = bunny.sprite.position
            var x = position.x + bunny.speed.x * delta
            var y = position.y + bunny.speed.y * delta
            var speedX = bunny.speed.x
            var speedY = bunny.speed.y + gravity * delta

            if (x > screenSize.x) {
                speedX *= -1.0
                x = screenSize.x
            }

            if (x < 0.0) {
                speedX *= -1.0
                x = 0.0
            }

            if (y > screenSize.y) {
                y = screenSize.y
                if (bounceRandom.nextFloat() > 0.5) {
                    // RandomNumberGenerator.randi(): a uint32, so the remainder is never negative.
                    speedY = -(bounceRandom.nextInt().toUInt() % 1100u + 50u).toDouble()
                } else {
                    speedY *= -0.85
                }
            }

            if (y < 0.0) {
                speedY = 0.0
                y = 0.0
            }

            bunny.sprite.position = Vector2(x, y)
            bunny.speed = Vector2(speedX, speedY)
        }
    }

    fun addBunny() {
        val bunny = Sprite2D.create()
        bunny.texture = bunnyTexture
        self.addChild(bunny)
        bunny.position = Vector2(screenSize.x / 2, screenSize.y / 2)
        // A ninety degree arc centred straight down -- +Y is down in 2D, so PI / 2 points at the floor.
        // Angle and speed are drawn separately, so every direction gets the same range of speeds.
        val angle = spawnRandom.nextDouble(Mathf.PI * 0.25, Mathf.PI * 0.75)
        val speed = spawnRandom.nextDouble(50.0, 250.0)
        bunnies.add(Bunny(bunny, Vector2(Mathf.cos(angle) * speed, Mathf.sin(angle) * speed)))
    }

    fun removeBunny() {
        if (bunnies.isEmpty()) return
        val bunny = bunnies.removeAt(bunnies.size - 1)
        self.removeChild(bunny.sprite)
        bunny.sprite.queueFree()
    }

    fun finish() {
        benchmarkFinished.emit(bunnies.size.toLong())
    }

    private companion object {
        // The desktop script's RandomNumberGenerator seeds.
        const val SPAWN_SEED = 20260921
        const val BOUNCE_SEED = 20260922
    }
}
