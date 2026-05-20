import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.ManualGodotLifetimeApi
import net.multigesture.kanama.api.Node2D
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.Sprite2D
import net.multigesture.kanama.api.Texture2D
import net.multigesture.kanama.binding.runtime.ObjectCalls
import net.multigesture.kanama.types.Vector2
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node2D")
@OptIn(ManualGodotLifetimeApi::class)
class BunnymarkV2Kanama(godotObject: MemorySegment) : KanamaScript<Node2D>(godotObject, ::Node2D) {
    private val gravity = 500.0
    private val bunnySpeeds = mutableListOf<Vector2>()
    private lateinit var screenSize: Vector2
    private lateinit var bunnies: Node2D
    private lateinit var label: Label
    private var bunnyTexture: Texture2D? = null

    @OnReady
    fun ready() {
        screenSize = self.getViewportRect().size
        bunnyTexture = ResourceLoader.loadTexture2D("res://images/godot_bunny.png")
        GD.randomize()
        bunnies = Node2D(ObjectCalls.constructObject("Node2D"))
        self.addChild(bunnies)
        label = Label(ObjectCalls.constructObject("Label"))
        label.position = Vector2(0.0, 20.0)
        self.addChild(label)
    }

    @OnExitTree
    fun exitTree() {
        bunnyTexture?.close()
        bunnyTexture = null
    }

    @OnProcess
    fun process(delta: Double) {
        screenSize = self.getViewportRect().size
        label.text = "Bunnies: ${bunnies.getChildCount()}"

        val bunnyChildren = bunnies.getChildren()
        for (i in bunnyChildren.indices) {
            val sprite = Sprite2D(bunnyChildren[i].handle)
            var pos = sprite.position
            var speed = bunnySpeeds[i]

            pos += speed * delta
            speed = Vector2(speed.x, speed.y + gravity * delta)

            if (pos.x > screenSize.x) {
                speed = Vector2(-speed.x, speed.y)
                pos = Vector2(screenSize.x, pos.y)
            } else if (pos.x < 0.0) {
                speed = Vector2(-speed.x, speed.y)
                pos = Vector2(0.0, pos.y)
            }

            if (pos.y > screenSize.y) {
                pos = Vector2(pos.x, screenSize.y)
                speed = if (GD.randf() > 0.5) {
                    Vector2(speed.x, -((GD.randi() % 1100L) + 50L).toDouble())
                } else {
                    Vector2(speed.x, speed.y * -0.85)
                }
            }

            if (pos.y < 0.0) {
                speed = Vector2(speed.x, 0.0)
                pos = Vector2(pos.x, 0.0)
            }

            sprite.position = pos
            bunnySpeeds[i] = speed
        }
    }

    @RegisterFunction("add_bunny")
    fun addBunny() {
        val bunny = Sprite2D(ObjectCalls.constructObject("Sprite2D"))
        bunny.setTexture(bunnyTexture)
        bunnies.addChild(bunny)
        bunny.position = Vector2(screenSize.x / 2.0, screenSize.y / 2.0)
        bunnySpeeds += Vector2(
            (GD.randi() % 200L + 50L).toDouble(),
            (GD.randi() % 200L + 50L).toDouble(),
        )
    }

    @RegisterFunction("remove_bunny")
    fun removeBunny() {
        val childCount = bunnies.getChildCount().toInt()
        if (childCount == 0) return
        val bunny = bunnies.getChild(childCount - 1) ?: return
        bunnySpeeds.removeAt(bunnySpeeds.lastIndex)
        bunnies.removeChild(bunny)
        bunny.queueFree()
    }

    @RegisterFunction("finish")
    fun finish() {
        self.emitSignal("benchmark_finished", bunnies.getChildCount())
    }
}
