import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GPUParticles3D
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import java.lang.foreign.MemorySegment
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.generated.PlayerMethods

@ScriptClass(attachTo = "Area3D")
class Coin(godotObject: MemorySegment) : KanamaScript<Area3D>(godotObject, ::Area3D) {

    private var time = 0.0
    private var grabbed = false
    private lateinit var mesh: Node3D
    private lateinit var particles: GPUParticles3D
    private lateinit var audio: Node

    @OnReady
    fun ready() {
        mesh = self.requireAs("Mesh", ::Node3D)
        particles = self.requireAs("Particles", ::GPUParticles3D)
        audio = self.getNodeOrNull("/root/Audio") ?: error("Coin requires the Audio autoload")
    }

    @RegisterFunction("_on_body_entered")
    fun onBodyEntered(body: Node) {
        if (grabbed) return
        if (!PlayerMethods.collectCoin(body)) return

        playAudio("res://sounds/coin.ogg")

        mesh.queueFree()
        particles.setEmitting(false)

        grabbed = true
    }

    private fun playAudio(path: String) {
        audio.call("play", path)
    }

    @OnProcess
    fun process(delta: Double) {
        // Rotation around Y, plus a small bob along Y.
        val rot = self.rotation
        self.rotation = rot.withY(rot.y + (2.0 * delta).toFloat())

        val pos = self.position
        self.position = pos.withY(pos.y + (Mathf.cos(time * 5.0) * delta).toFloat())

        time += delta
    }
}
