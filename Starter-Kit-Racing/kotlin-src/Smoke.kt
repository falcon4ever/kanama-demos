package racing

import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.api.RigidBody3D
import net.multigesture.kanama.api.KanamaScript

/** Desktop smoke of the Racing demo, as an autoload (Kanama task 131 item 17). */
@ScriptClass(attachTo = "Node")
class Smoke(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
    private var frames = 0
    private val sphere by lazy {
        val main = self.getParent()?.getNodeOrNull("Main") ?: error("Racing smoke could not find the Main scene root")
        main.requireAs("Vehicle/Sphere", ::RigidBody3D)
    }

    // Read once: an autoload lives for the whole session, so without the switch it must not tick at all.
    @OnReady
    fun ready() {
        if (OS.getEnvironment("KANAMA_RACING_SMOKE") != "1") self.setPhysicsProcess(false)
    }

    @OnPhysicsProcess
    fun physicsProcess(delta: Double) {
        frames += 1
        if (frames < 3) return

        check(sphere.angularVelocity.length() >= 0.0) {
            "Racing smoke expected RigidBody3D.angularVelocity to be readable"
        }

        // The completion line scripts/desktop_smoke_all.sh requires (task 118/131). The iOS runner does not: this demo reads
        // KANAMA_RACING_SMOKE, not KANAMA_DEMO_SMOKE_QUIT, so it is launch-only on the phone.
        println("[kanama:smoke] SmokeQuit complete")
        requireNotNull(self.getTree()).quit()
    }
}
