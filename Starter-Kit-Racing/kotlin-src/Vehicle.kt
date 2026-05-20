package racing

import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AudioStreamPlayer3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GPUParticles3D
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.RayCast3D
import net.multigesture.kanama.api.RigidBody3D
import net.multigesture.kanama.types.Basis
import net.multigesture.kanama.types.Transform3D
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node3D")
@GlobalClass
open class Vehicle(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {

    protected lateinit var sphere: RigidBody3D
    protected lateinit var raycast: RayCast3D
    protected lateinit var vehicleModel: Node3D
    protected var vehicleBody: Node3D? = null

    private var wheelFl: Node3D? = null
    private var wheelFr: Node3D? = null
    private var wheelBl: Node3D? = null
    private var wheelBr: Node3D? = null

    private var trailLeft: GPUParticles3D? = null
    private var trailRight: GPUParticles3D? = null

    private lateinit var screechSound: AudioStreamPlayer3D
    private lateinit var engineSound: AudioStreamPlayer3D
    private lateinit var impactSound: AudioStreamPlayer3D

    protected var input = Vector3.ZERO
    protected var normal = Vector3.ZERO

    protected var acceleration = 0.0
    protected var angularSpeed = 0.0
    var linearSpeed = 0.0
        protected set

    protected var colliding = false
    protected var linearVelocity = Vector3.ZERO
    protected var prevPosition = Vector3.ZERO
    protected var calculatedLean = 0.0

    @OnReady
    open fun ready() {
        sphere = self.requireAs("Sphere", ::RigidBody3D)
        raycast = self.requireAs("Ground", ::RayCast3D)
        vehicleModel = self.requireAs("Container", ::Node3D)
        vehicleBody = self.getAsOrNull("Container/Model/body", ::Node3D)

        wheelFl = self.getAsOrNull("Container/Model/wheel-front-left", ::Node3D)
        wheelFr = self.getAsOrNull("Container/Model/wheel-front-right", ::Node3D)
        wheelBl = self.getAsOrNull("Container/Model/wheel-back-left", ::Node3D)
        wheelBr = self.getAsOrNull("Container/Model/wheel-back-right", ::Node3D)

        trailLeft = self.getAsOrNull("Container/TrailLeft", ::GPUParticles3D)
        trailRight = self.getAsOrNull("Container/TrailRight", ::GPUParticles3D)

        screechSound = self.requireAs("Container/ScreechSound", ::AudioStreamPlayer3D)
        engineSound = self.requireAs("Container/EngineSound", ::AudioStreamPlayer3D)
        impactSound = self.requireAs("Container/ImpactSound", ::AudioStreamPlayer3D)
    }

    @RegisterFunction("get_vehicle_position")
    open fun getVehiclePosition(): Vector3 = vehicleModel.globalPosition

    @OnPhysicsProcess
    open fun physicsProcess(delta: Double) {
        handleInput(delta)

        var direction = GD.signf(linearSpeed)
        if (direction == 0.0) {
            direction = if (Mathf.abs(input.z) > 0.1f) GD.signf(input.z.toDouble()) else 1.0
        }

        val steeringGrip = GD.clampf(Mathf.abs(linearSpeed), 0.2, 1.0)
        val targetAngular = -input.x * steeringGrip * 4.0 * direction
        angularSpeed = GD.lerpf(angularSpeed, targetAngular, delta * 4.0)
        vehicleModel.rotateY(angularSpeed * delta)

        if (raycast.isColliding()) {
            if (!colliding) {
                vehicleBody?.position = Vector3(0f, 0.1f, 0f)
                input = input.withZ(0f)
            }

            normal = raycast.getCollisionNormal()
            if (normal.dot(vehicleModel.globalBasis.y) > 0.5) {
                val xform = alignWithY(vehicleModel.globalTransform, normal)
                vehicleModel.globalTransform = vehicleModel.globalTransform
                    .interpolateWith(xform, 0.2)
                    .orthonormalized()
            }
        }

        colliding = raycast.isColliding()

        val targetSpeed = input.z.toDouble()
        linearSpeed = if (targetSpeed < 0.0 && linearSpeed > 0.01) {
            GD.lerpf(linearSpeed, 0.0, delta * 8.0)
        } else if (targetSpeed < 0.0) {
            GD.lerpf(linearSpeed, targetSpeed / 2.0, delta * 2.0)
        } else {
            GD.lerpf(linearSpeed, targetSpeed, delta * 6.0)
        }

        acceleration = GD.lerpf(
            acceleration,
            linearSpeed + (Mathf.abs(sphere.angularVelocity.length() * linearSpeed) / 100.0),
            delta,
        )

        vehicleModel.position = sphere.position - Vector3(0f, 0.65f, 0f)
        raycast.position = sphere.position

        linearVelocity = (vehicleModel.position - prevPosition) / delta
        prevPosition = vehicleModel.position

        effectEngine(delta)
        effectBody(delta)
        effectWheels(delta)
        effectTrails()
    }

    protected fun handleInput(delta: Double) {
        if (raycast.isColliding()) {
            input = input
                .withX(Input.getAxis("left", "right"))
                .withZ(Input.getAxis("back", "forward"))
        }

        sphere.angularVelocity += vehicleModel.globalTransform.basis.x * (linearSpeed * 100.0) * delta
    }

    protected open fun effectBody(delta: Double) {
        calculatedLean = GD.lerpAngle(calculatedLean, -input.x / 5.0 * linearSpeed, delta * 5.0)

        vehicleBody?.let { body ->
            body.rotation = body.rotation
                .withX(GD.lerpAngle(body.rotation.x.toDouble(), -(linearSpeed - acceleration) / 6.0, delta * 10.0))
                .withZ(calculatedLean)
            body.position = body.position.lerp(Vector3(0f, 0.2f, 0f), delta * 5.0)
        }
    }

    protected open fun effectWheels(delta: Double) {
        for (wheel in listOf(wheelFl, wheelFr, wheelBl, wheelBr)) {
            if (wheel != null) {
                wheel.rotation = wheel.rotation.withX(wheel.rotation.x + acceleration)
            }
        }

        wheelFl?.let { it.rotation = it.rotation.withY(GD.lerpAngle(it.rotation.y.toDouble(), -input.x / 1.5, delta * 10.0)) }
        wheelFr?.let { it.rotation = it.rotation.withY(GD.lerpAngle(it.rotation.y.toDouble(), -input.x / 1.5, delta * 10.0)) }
    }

    private fun effectEngine(delta: Double) {
        val speedFactor = GD.clampf(Mathf.abs(linearSpeed), 0.0, 1.0)
        val throttleFactor = GD.clampf(Mathf.abs(input.z.toDouble()), 0.0, 1.0)

        val targetVolume = GD.remap(speedFactor + (throttleFactor * 0.5), 0.0, 1.5, -15.0, -5.0)
        engineSound.setVolumeDb(GD.lerpf(engineSound.getVolumeDb(), targetVolume, delta * 5.0))

        var targetPitch = GD.remap(speedFactor, 0.0, 1.0, 0.5, 3.0)
        if (throttleFactor > 0.1) targetPitch += 0.2

        engineSound.setPitchScale(GD.lerpf(engineSound.getPitchScale(), targetPitch, delta * 2.0))
    }

    private fun effectTrails() {
        val driftIntensity = Mathf.abs(linearSpeed - acceleration) + (Mathf.abs(calculatedLean) * 2.0)
        val shouldEmit = driftIntensity > 0.25

        trailLeft?.setEmitting(shouldEmit)
        trailRight?.setEmitting(shouldEmit)

        val targetVolume = if (shouldEmit) {
            GD.remap(GD.clampf(driftIntensity, 0.25, 2.0), 0.25, 2.0, -10.0, 0.0)
        } else {
            -80.0
        }

        screechSound.setPitchScale(GD.lerpf(screechSound.getPitchScale(), GD.clampf(Mathf.abs(linearSpeed), 1.0, 3.0), 0.1))
        screechSound.setVolumeDb(GD.lerpf(screechSound.getVolumeDb(), targetVolume, 10.0 * self.getPhysicsProcessDeltaTime()))
    }

    private fun alignWithY(xform: Transform3D, newY: Vector3): Transform3D {
        var basis = xform.basis.withY(newY)
        basis = basis.withX(-basis.z.cross(newY)).orthonormalized()
        return xform.withBasis(basis)
    }

    @RegisterFunction("_on_sphere_body_entered")
    open fun onSphereBodyEntered(body: GodotObject) {
        val currentBody = vehicleBody ?: return
        if (!impactSound.isPlaying()) {
            val impactVelocity = Mathf.abs(linearVelocity.dot(currentBody.globalBasis.z))
            impactSound.setVolumeDb(GD.clampf(GD.remap(impactVelocity, 0.0, 6.0, -20.0, 0.0), -20.0, 0.0))
            impactSound.play()
        }
    }
}
