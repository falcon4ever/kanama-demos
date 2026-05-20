package thirdperson

import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.OnUnhandledInput
import net.multigesture.kanama.annotations.PropertyHint
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEventMouseMotion
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.RayCast3D
import net.multigesture.kanama.api.SpringArm3D
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.types.Basis
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node3D")
class CameraController(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {
    @ScriptProperty
    var invertMouseY = false

    @ScriptProperty(hint = PropertyHint.RANGE, hintString = "0.0,1.0,0.01")
    var mouseSensitivity = 0.25

    @ScriptProperty(hint = PropertyHint.RANGE, hintString = "0.0,8.0,0.01")
    var joystickSensitivity = 2.0

    @ScriptProperty
    var tiltUpperLimit = GD.degToRad(-60.0)

    @ScriptProperty
    var tiltLowerLimit = GD.degToRad(60.0)

    private lateinit var camera: Camera3D
    private lateinit var overShoulderPivot: Node3D
    private lateinit var cameraSpringArm: SpringArm3D
    private lateinit var thirdPersonPivot: Node3D
    private lateinit var cameraRaycast: RayCast3D

    private var aimTarget = Vector3.ZERO
    private var aimCollider: GodotObject? = null
    private var pivot: Node3D? = null
    private var currentPivotType = PIVOT_UNSET
    private var rotationInput = 0.0
    private var tiltInput = 0.0
    private var offset = Vector3.ZERO
    private var anchor: CharacterBody3D? = null
    private var anchorPlayer: Player? = null
    private var eulerRotation = Vector3.ZERO

    @OnReady
    fun ready() {
        camera = self.requireAs("PlayerCamera", ::Camera3D)
        overShoulderPivot = self.requireAs("CameraOverShoulderPivot", ::Node3D)
        cameraSpringArm = self.requireAs("CameraSpringArm", ::SpringArm3D)
        thirdPersonPivot = self.requireAs("CameraSpringArm/CameraThirdPersonPivot", ::Node3D)
        cameraRaycast = self.requireAs("PlayerCamera/CameraRayCast", ::RayCast3D)
        self.setProcessInput(true)
        self.setProcessUnhandledInput(true)
    }

    @OnInput
    @OnUnhandledInput
    fun unhandledInput(event: GodotObject) {
        val motion = InputEventMouseMotion.from(event)
        if (motion != null && Input.getMouseMode() == Input.MOUSE_MODE_CAPTURED) {
            val relative = motion.getRelative()
            rotationInput = -relative.x.toDouble() * mouseSensitivity
            tiltInput = -relative.y.toDouble() * mouseSensitivity
        }
    }

    @OnProcess
    fun process(delta: Double) {
        val anchorBody = anchor ?: return

        rotationInput += Input.getActionRawStrength("camera_left") - Input.getActionRawStrength("camera_right")
        tiltInput += Input.getActionRawStrength("camera_up") - Input.getActionRawStrength("camera_down")

        if (invertMouseY) {
            tiltInput *= -1.0
        }

        if (cameraRaycast.isColliding()) {
            aimTarget = cameraRaycast.getCollisionPoint()
            aimCollider = cameraRaycast.getCollider()
        } else {
            aimTarget = cameraRaycast.globalTransform * cameraRaycast.getTargetPosition()
            aimCollider = null
        }

        val groundHeight = getAnchorGroundHeight(anchorBody)
        self.globalPosition = (anchorBody.globalPosition + offset).withY(
            Mathf.lerp(self.globalPosition.y.toDouble(), groundHeight, 0.1),
        )

        val minTilt = Mathf.min(tiltLowerLimit, tiltUpperLimit)
        val maxTilt = Mathf.max(tiltLowerLimit, tiltUpperLimit)
        eulerRotation = eulerRotation
            .withX((eulerRotation.x.toDouble() + tiltInput * delta).coerceIn(minTilt, maxTilt))
            .withY(eulerRotation.y.toDouble() + rotationInput * delta)
        self.basis = Basis.fromEuler(eulerRotation)

        val activePivot = pivot
        if (activePivot != null) {
            camera.globalTransform = activePivot.globalTransform
            camera.rotation = camera.rotation.withZ(0)
        }

        rotationInput = 0.0
        tiltInput = 0.0
    }

    @RegisterFunction
    fun setup(anchor: GodotObject) {
        val anchorBody = CharacterBody3D(anchor.handle)
        setupAnchor(anchorBody, anchor.kotlinScriptInstance<Player>())
    }

    fun setup(player: Player) {
        setupAnchor(player.self, player)
    }

    private fun setupAnchor(anchorBody: CharacterBody3D, anchorPlayer: Player?) {
        this.anchor = anchorBody
        this.anchorPlayer = anchorPlayer
        self.globalTransform = anchorBody.globalTransform
        offset = self.globalTransform.origin - anchorBody.globalTransform.origin
        setPivot(PIVOT_THIRD_PERSON)
        pivot?.let {
            camera.globalTransform = camera.globalTransform.interpolateWith(it.globalTransform, 0.1)
        }
        cameraSpringArm.addExcludedObject(anchorBody.getRid())
        cameraRaycast.addExceptionRid(anchorBody.getRid())
    }

    @RegisterFunction("set_pivot")
    fun setPivot(pivotType: Long) {
        if (pivotType == currentPivotType) return

        when (pivotType) {
            PIVOT_OVER_SHOULDER -> {
                overShoulderPivot.lookAt(aimTarget)
                pivot = overShoulderPivot
            }
            PIVOT_THIRD_PERSON -> {
                pivot = thirdPersonPivot
            }
        }

        currentPivotType = pivotType
    }

    @RegisterFunction("get_aim_target")
    fun getAimTarget(): Vector3 = aimTarget

    @RegisterFunction("get_camera_forward")
    fun getCameraForward(): Vector3 =
        (camera.globalTransform.basis * Vector3.FORWARD).normalized()

    @RegisterFunction("get_camera_global_position")
    fun getCameraGlobalPosition(): Vector3 =
        camera.globalPosition

    @RegisterFunction("get_aim_collider_instance_id")
    fun getAimColliderInstanceId(): Long =
        aimCollider?.takeIf { GD.isInstanceValid(it) }?.getInstanceId() ?: 0L

    private fun getAnchorGroundHeight(anchorBody: CharacterBody3D): Double {
        return anchorPlayer?.getGroundHeight() ?: anchorBody.globalPosition.y.toDouble()
    }

    companion object {
        private const val PIVOT_UNSET = -1L
        const val PIVOT_OVER_SHOULDER = 0L
        const val PIVOT_THIRD_PERSON = 1L
    }
}
