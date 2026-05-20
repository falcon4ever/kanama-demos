import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node3D")
class PlatformFalling(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {

    private var falling = false
    private var fallVelocity = 0.0
    private lateinit var audio: Node

    @OnReady
    fun ready() {
        audio = self.getNodeOrNull("/root/Audio") ?: error("PlatformFalling requires the Audio autoload")
    }

    @OnPhysicsProcess
    fun physicsProcess(delta: Double) {
        self.scale = self.scale.lerp(Vector3.ONE, delta * 10.0)

        if (falling) {
            fallVelocity += 15.0 * delta
            val pos = self.position
            self.position = pos.withY(pos.y - (fallVelocity * delta).toFloat())
        } else {
            fallVelocity = 0.0
        }

        if (self.position.y < -10f) {
            self.queueFree()
        }
    }

    @RegisterFunction("_on_body_entered")
    fun onBodyEntered(_body: Node) {
        if (!falling) {
            audio.call("play", "res://sounds/fall.ogg")
            self.scale = Vector3(1.25f, 1f, 1.25f)
        }
        falling = true
    }
}
