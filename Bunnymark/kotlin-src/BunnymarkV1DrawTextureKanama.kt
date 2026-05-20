import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.ManualGodotLifetimeApi
import net.multigesture.kanama.api.Node2D
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.Texture2D
import net.multigesture.kanama.types.Color
import net.multigesture.kanama.types.Vector2
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node2D")
@OptIn(ManualGodotLifetimeApi::class)
class BunnymarkV1DrawTextureKanama(godotObject: MemorySegment) : KanamaScript<Node2D>(godotObject, ::Node2D) {
    data class Bunny(var position: Vector2, var speed: Vector2)

    private val gravity = 500.0
    private val bunnies = mutableListOf<Bunny>()
    private lateinit var screenSize: Vector2
    private var bunnyTexture: Texture2D? = null
    private val white = Color(1.0f, 1.0f, 1.0f, 1.0f)

    @OnReady
    fun ready() {
        GD.randomize()
        screenSize = self.getViewportRect().size
        bunnyTexture = ResourceLoader.loadTexture2D("res://images/godot_bunny.png")
    }

    @OnExitTree
    fun exitTree() {
        bunnyTexture?.close()
        bunnyTexture = null
    }

    @RegisterFunction("_draw")
    fun draw() {
        val texture = bunnyTexture ?: return
        for (bunny in bunnies) {
            self.drawTexture(texture, bunny.position, white)
        }
    }

    @OnProcess
    fun process(delta: Double) {
        screenSize = self.getViewportRect().size
        for (bunny in bunnies) {
            var pos = bunny.position
            var speed = bunny.speed

            pos += speed * delta
            speed = Vector2(speed.x, speed.y + gravity * delta)

            if (pos.x > screenSize.x) {
                speed = Vector2(-speed.x, speed.y)
                pos = Vector2(screenSize.x, pos.y)
            }

            if (pos.x < 0.0) {
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

            bunny.position = pos
            bunny.speed = speed
        }
        self.queueRedraw()
    }

    @RegisterFunction("add_bunny")
    fun addBunny() {
        bunnies += Bunny(
            Vector2(screenSize.x / 2.0, screenSize.y / 2.0),
            Vector2((GD.randi() % 200L + 50L).toDouble(), (GD.randi() % 200L + 50L).toDouble()),
        )
    }

    @RegisterFunction("remove_bunny")
    fun removeBunny() {
        if (bunnies.isEmpty()) return
        bunnies.removeAt(bunnies.lastIndex)
    }

    @RegisterFunction("finish")
    fun finish() {
        self.emitSignal("benchmark_finished", bunnies.size)
    }
}
