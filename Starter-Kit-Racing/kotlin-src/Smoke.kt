package racing

import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.api.RigidBody3D
import java.lang.foreign.MemorySegment
import net.multigesture.kanama.api.KanamaScript

@ScriptClass(attachTo = "Node3D")
class Smoke(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {
    private var frames = 0
    private val sphere by lazy {
        val root = self.getParent() ?: error("Racing smoke expected Smoke to have a parent scene")
        root.requireAs("Vehicle/Sphere", ::RigidBody3D)
    }

    @OnPhysicsProcess
    fun physicsProcess(delta: Double) {
        if (OS.getEnvironment("KANAMA_RACING_SMOKE") != "1") return
        frames += 1
        if (frames < 3) return

        check(sphere.angularVelocity.length() >= 0.0) {
            "Racing smoke expected RigidBody3D.angularVelocity to be readable"
        }

        self.getTree().quit()
    }
}
