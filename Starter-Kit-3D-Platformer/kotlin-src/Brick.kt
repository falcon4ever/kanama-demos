import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.CollisionShape3D
import net.multigesture.kanama.api.GPUParticles3D
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.SceneTree
import net.multigesture.kanama.api.StaticBody3D
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "StaticBody3D")
class Brick(godotObject: MemorySegment) : KanamaScript<StaticBody3D>(godotObject, ::StaticBody3D), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()

    private var exploded = false

    private lateinit var bottomDetector: Area3D
    private lateinit var mesh: Node3D
    private lateinit var particles: GPUParticles3D
    private lateinit var collisionShape: CollisionShape3D
    private lateinit var audio: Node

    @OnReady
    fun ready() {
        bottomDetector = self.requireAs("BottomDetector", ::Area3D)
        mesh = self.requireAs("Mesh", ::Node3D)
        particles = self.requireAs("Particles", ::GPUParticles3D)
        collisionShape = self.requireAs("CollisionShape3D", ::CollisionShape3D)
        audio = self.getNodeOrNull("/root/Audio") ?: error("Brick requires the Audio autoload")

        bottomDetector.signal(Area3D.Signals.bodyEntered).connectObject(self) { body ->
            onBottomHit(Node(body.handle))
        }
    }

    @RegisterFunction
    fun onBottomHit(body: Node) {
        if (body.isInGroup("player")) explode()
    }

    private fun explode() {
        if (exploded) return
        exploded = true

        audio.call("play", "res://sounds/break.ogg")

        particles.restart(keepSeed = true)
        mesh.hide()
        collisionShape.setDisabled(true)
        bottomDetector.setDeferred("monitoring", false)

        kanamaScope.launch {
            SceneTree.delaySeconds(1.0)
            self.queueFree()
        }
    }
}
