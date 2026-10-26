package net.multigesture.kanama.demos.bunnymark

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node2D
import net.multigesture.kanama.api.RandomNumberGenerator
import net.multigesture.kanama.api.Resource
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.Sprite2D
import net.multigesture.kanama.api.Texture2D
import net.multigesture.kanama.api.asScript
import net.multigesture.kanama.generated.benchmarkFinished
import net.multigesture.kanama.types.Vector2

@ScriptClass(attachTo = "Node2D")
class BunnymarkScriptsKanama(godotObject: GodotHandle) : KanamaScript<Node2D>(godotObject, ::Node2D) {

    @Signal
    fun benchmarkFinished(output: Long) = Unit

    private val spawnRandom = RandomNumberGenerator.create()
    private var spawned = 0L
    private var bunnyTexture: Texture2D? = null
    // Kanama has no `Bunny.new()`: a bunny is a new Sprite2D that the script is then attached to, which is what
    // `new()` does in GDScript.
    private var bunnyScript: Resource? = null
    private val label = Label.create()
    private val bunnies = Node2D.create()

    // Zero until the first frame, as in GDScript: a run without a warm-up spawns its first bunnies before then.
    private var screenSize = Vector2(0.0, 0.0)

    @OnReady
    fun ready() {
        // Fixed seed rather than randomize(): every run then spawns the same population, so a difference in the
        // score is a difference in the code. Each bunny bounces from a generator of its own, seeded below.
        spawnRandom.seed = 20260921
        bunnyTexture = ResourceLoader.loadTexture2D("res://images/godot_bunny.png")
        bunnyScript = ResourceLoader.load("res://kotlin-src/BunnyKanama.kt")
        self.addChild(bunnies)

        label.setPosition(Vector2(0.0, 20.0))
        self.addChild(label)
    }

    @OnExitTree
    fun exitTree() {
        bunnyTexture?.close()
        bunnyScript?.close()
        bunnyTexture = null
        bunnyScript = null
    }

    @OnProcess
    fun process(@Suppress("UNUSED_PARAMETER") delta: Double) {
        screenSize = self.getViewportRect().size
        label.text = "Bunnies ${bunnies.getChildCount()}"
    }

    fun addBunny() {
        val sprite = Sprite2D.create()
        sprite.setScript(bunnyScript)
        val bunny = sprite.asScript<BunnyKanama>()!!
        sprite.texture = bunnyTexture
        bunnies.addChild(sprite)
        sprite.position = Vector2(screenSize.x / 2, screenSize.y / 2)
        // A ninety degree arc centred straight down -- +Y is down in 2D, so PI / 2 points at the floor.
        // Angle and speed are drawn separately, so every direction gets the same range of speeds.
        val angle = spawnRandom.randfRange(Mathf.PI * 0.25, Mathf.PI * 0.75)
        val speed = spawnRandom.randfRange(50.0, 250.0)
        bunny.speed = Vector2(Mathf.cos(angle) * speed, Mathf.sin(angle) * speed)
        bunny.seedBounce(20260922 + spawned)
        spawned++
    }

    fun removeBunny() {
        val childCount = bunnies.getChildCount()
        if (childCount != 0) {
            val bunny = bunnies.getChild(childCount - 1) ?: return
            bunnies.removeChild(bunny)
            bunny.queueFree()
        }
    }

    fun finish() {
        benchmarkFinished.emit(bunnies.getChildCount().toLong())
    }
}
