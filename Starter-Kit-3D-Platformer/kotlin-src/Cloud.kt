import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node3D")
class Cloud(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {

    private var time = 0.0
    private var randomVelocity = 1.0
    private var randomTime = 1.0

    @OnReady
    fun ready() {
        randomVelocity = GD.randfRange(0.1, 2.0)
        randomTime = GD.randfRange(0.1, 2.0)
    }

    @OnProcess
    fun process(delta: Double) {
        val pos = self.position
        self.position = pos.withY(pos.y + (Mathf.cos(time * randomTime) * randomVelocity * delta).toFloat())
        time += delta
    }
}
