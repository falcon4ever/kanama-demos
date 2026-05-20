package racing

import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node3D")
@GlobalClass
class VehicleMotorcycle(godotObject: MemorySegment) : Vehicle(godotObject) {
    private lateinit var motorcycle: Node3D
    private lateinit var fork: Node3D
    private lateinit var wheelFront: Node3D
    private lateinit var wheelBack: Node3D

    @OnReady
    override fun ready() {
        super.ready()
        motorcycle = self.requireAs("Container/Model/motorcycle", ::Node3D)
        fork = self.requireAs("Container/Model/motorcycle/body/fork", ::Node3D)
        wheelFront = self.requireAs("Container/Model/motorcycle/wheel-front", ::Node3D)
        wheelBack = self.requireAs("Container/Model/motorcycle/wheel-back", ::Node3D)
        vehicleBody = self.requireAs("Container/Model/motorcycle/body", ::Node3D)
    }

    @RegisterFunction("get_vehicle_position")
    override fun getVehiclePosition(): Vector3 = super.getVehiclePosition()

    @OnPhysicsProcess
    override fun physicsProcess(delta: Double) {
        super.physicsProcess(delta)
    }

    @RegisterFunction("_on_sphere_body_entered")
    override fun onSphereBodyEntered(body: GodotObject) {
        super.onSphereBodyEntered(body)
    }

    override fun effectBody(delta: Double) {
        val targetLean = -input.x / 5.0 * linearSpeed
        calculatedLean = GD.lerpAngle(calculatedLean, targetLean, delta * 5.0)

        motorcycle.rotation = motorcycle.rotation.withZ(
            GD.lerpAngle(motorcycle.rotation.z.toDouble(), input.x * linearSpeed, delta * 3.0),
        )
        vehicleBody?.let { body ->
            body.rotation = body.rotation.withX(
                GD.lerpAngle(body.rotation.x.toDouble(), -(linearSpeed - acceleration) / 6.0, delta * 10.0),
            )
        }
    }

    override fun effectWheels(delta: Double) {
        for (wheel in listOf(wheelFront, wheelBack)) {
            wheel.rotation = wheel.rotation.withX(wheel.rotation.x + acceleration)
        }

        fork.rotation = fork.rotation.withY(
            GD.lerpAngle(fork.rotation.y.toDouble(), -input.x / 1.5, delta * 5.0),
        )
        wheelFront.rotation = wheelFront.rotation.withY(
            GD.lerpAngle(wheelFront.rotation.y.toDouble(), -input.x / 1.5, delta * 10.0),
        )
    }
}
