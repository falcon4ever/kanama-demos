import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Sprite2D
import net.multigesture.kanama.types.Vector2
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Sprite2D")
class BunnyKanama(godotObject: MemorySegment) : KanamaScript<Sprite2D>(godotObject, ::Sprite2D) {
    @ScriptProperty
    var speed: Vector2 = Vector2(0.0, 0.0)

    private val gravity = 500.0
    private lateinit var screenSize: Vector2

    @OnReady
    fun ready() {
        GD.randomize()
    }

    @OnProcess
    fun process(delta: Double) {
        screenSize = self.getViewportRect().size
        var pos = self.position

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

        self.position = pos
    }
}
