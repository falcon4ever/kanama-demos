package racing

import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.ExportGroup
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import java.lang.foreign.MemorySegment
import net.multigesture.kanama.api.KanamaScript

@ScriptClass(attachTo = "Node3D")
class View(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {
    private lateinit var camera: Camera3D

    @ExportGroup("Properties")
    @ScriptProperty
    var target: Vehicle? = null

    @OnReady
    fun ready() {
        camera = self.requireAs("Camera", ::Camera3D)
    }

    @OnPhysicsProcess
    fun physicsProcess(delta: Double) {
        val vehicle = target ?: return

        self.position = self.position.lerp(vehicle.getVehiclePosition(), delta * 4.0)

        val speedFactor = GD.clampf(Mathf.abs(vehicle.linearSpeed), 0.0, 1.0)
        val targetZ = GD.remap(speedFactor, 0.0, 1.0, 10.0, 20.0)
        camera.position = camera.position.withZ(GD.lerpf(camera.position.z.toDouble(), targetZ, delta * 0.5))
    }
}
