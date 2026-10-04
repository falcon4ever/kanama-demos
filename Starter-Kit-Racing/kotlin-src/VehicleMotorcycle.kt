package racing

import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.Node3D

@ScriptClass(attachTo = "Node3D")
@GlobalClass
class VehicleMotorcycle(godotObject: GodotHandle) : Vehicle(godotObject) {
    private lateinit var motorcycle: Node3D
    private lateinit var fork: Node3D
    private lateinit var wheelFront: Node3D
    private lateinit var wheelBack: Node3D

    override fun ready() {
        super.ready()
        motorcycle = self.requireAs("Container/Model/motorcycle", ::Node3D)
        fork = self.requireAs("Container/Model/motorcycle/body/fork", ::Node3D)
        wheelFront = self.requireAs("Container/Model/motorcycle/wheel-front", ::Node3D)
        wheelBack = self.requireAs("Container/Model/motorcycle/wheel-back", ::Node3D)
        vehicleBody = self.requireAs("Container/Model/motorcycle/body", ::Node3D)
    }

    override fun effectBody(delta: Double) {
        val targetLean = -input.x / 5.0 * linearSpeed
        calculatedLean = GD.lerpAngle(calculatedLean, targetLean, delta * 5.0)

        motorcycle.rotation = motorcycle.rotation.withZ(
            GD.lerpAngle(motorcycle.rotation.z, input.x * linearSpeed, delta * 3.0),
        )
        vehicleBody?.let { body ->
            body.rotation = body.rotation.withX(
                GD.lerpAngle(body.rotation.x, -(linearSpeed - acceleration) / 6.0, delta * 10.0),
            )
        }
    }

    override fun effectWheels(delta: Double) {
        for (wheel in listOf(wheelFront, wheelBack)) {
            wheel.rotation = wheel.rotation.withX(wheel.rotation.x + acceleration)
        }

        fork.rotation = fork.rotation.withY(
            GD.lerpAngle(fork.rotation.y, -input.x / 1.5, delta * 5.0),
        )
        wheelFront.rotation = wheelFront.rotation.withY(
            GD.lerpAngle(wheelFront.rotation.y, -input.x / 1.5, delta * 10.0),
        )
    }
}
