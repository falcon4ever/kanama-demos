package charactercontroller

import net.multigesture.kanama.annotations.Export
import net.multigesture.kanama.annotations.ExportGroup
import net.multigesture.kanama.annotations.ExportRange
import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.OnUnhandledInput
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AudioStreamPlayer3D
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.GPUParticles3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEvent
import net.multigesture.kanama.api.InputEventMouseMotion
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.castOrNull
import net.multigesture.kanama.generated.Autoloads
import net.multigesture.kanama.generated.EventsNames
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector3
import net.multigesture.kanama.generated.flagReached
import net.multigesture.kanama.generated.killPlaneTouched

@ScriptClass(attachTo = "CharacterBody3D")
class Player3DTemplate(godotObject: GodotHandle) : KanamaScript<CharacterBody3D>(godotObject, ::CharacterBody3D) {
    @Export
    @ExportGroup("Movement")
    var moveSpeed = 8.0

    @Export
    var acceleration = 20.0

    @Export
    var jumpImpulse = 12.0

    @Export
    var rotationSpeed = 12.0

    @Export
    var stoppingSpeed = 1.0

    @ExportRange(0.0, 1.0, 0.01)
    @ExportGroup("Camera")
    var mouseSensitivity = 0.25

    @Export
    var controllerCameraSensitivity = 2.5

    // Spelled literals (Mathf.PI / 3.0 and -Mathf.PI / 8.0): expression defaults are not
    // portable to the Web proxy, which needs a plain literal it can re-emit.
    @Export
    var tiltUpperLimit = 1.0471975511965976

    @Export
    var tiltLowerLimit = -0.39269908169872414

    var groundHeight = 0.0

    private var gravity = -30.0
    private var wasOnFloorLastFrame = true
    private var cameraInputDirection = Vector2.ZERO
    private lateinit var lastInputDirection: Vector3
    private lateinit var startPosition: Vector3

    private val cameraPivot by node<Node3D>("%CameraPivot")
    private val camera by node<Camera3D>("%Camera3D")
    private val skinNode by node<Node3D>("%SophiaSkin")
    private val skin by script<SophiaSkin>("%SophiaSkin")
    private val landingSound by node<AudioStreamPlayer3D>("%LandingSound")
    private val jumpSound by node<AudioStreamPlayer3D>("%JumpSound")
    private val dustParticles by node<GPUParticles3D>("%DustParticles")

    @OnReady
    fun ready() {
        lastInputDirection = self.globalBasis.z
        startPosition = self.globalPosition

        val events = Autoloads.Events
        events.killPlaneTouched.connect {
            self.globalPosition = startPosition
            self.velocity = Vector3.ZERO
            skin.idle()
            self.setPhysicsProcess(true)
        }
        events.flagReached.connect {
            self.setPhysicsProcess(false)
            skin.idle()
            dustParticles.setEmitting(false)
        }
    }

    @OnExitTree
    fun exitTree() {
        if (!self.isNodeReady()) return
        landingSound.stop()
        jumpSound.stop()
    }

    @OnInput
    fun input(event: InputEvent) {
        if (event.isActionPressed("ui_cancel")) {
            Input.setMouseMode(Input.MouseMode.VISIBLE)
        } else if (event.isActionPressed("left_click")) {
            Input.setMouseMode(Input.MouseMode.CAPTURED)
        }
    }

    @OnUnhandledInput
    fun unhandledInput(event: InputEvent) {
        val motion = event.castOrNull<InputEventMouseMotion>() ?: return
        if (Input.getMouseMode() != Input.MouseMode.CAPTURED) return
        val relative = motion.getRelative()
        cameraInputDirection = Vector2(-relative.x * mouseSensitivity, relative.y * mouseSensitivity)
    }

    @OnPhysicsProcess
    fun physicsProcess(delta: Double) {
        val cameraInput = Input.getVector("camera_left", "camera_right", "camera_up", "camera_down")
        if (cameraInput.length() > 0.0) {
            cameraInputDirection += Vector2(
                -cameraInput.x * controllerCameraSensitivity,
                -cameraInput.y * controllerCameraSensitivity,
            )
        }

        val pivotRotation = cameraPivot.rotation
        cameraPivot.rotation = pivotRotation
            .withX(Mathf.clamp(pivotRotation.x + cameraInputDirection.y * delta, tiltLowerLimit, tiltUpperLimit))
            .withY(pivotRotation.y + cameraInputDirection.x * delta)
        cameraInputDirection = Vector2.ZERO

        val rawInput = Input.getVector("move_left", "move_right", "move_up", "move_down", 0.4)
        val forward = camera.globalBasis.z
        val right = camera.globalBasis.x
        var moveDirection = forward * rawInput.y + right * rawInput.x
        moveDirection = moveDirection.withY(0.0).normalized()

        if (moveDirection.length() > 0.2) {
            lastInputDirection = moveDirection.normalized()
        }
        val targetAngle = Vector3.BACK.signedAngleTo(lastInputDirection, Vector3.UP)
        val skinRotation = skinNode.rotation
        skinNode.globalRotation = skinNode.globalRotation.withY(
            Mathf.lerpAngle(skinRotation.y, targetAngle, rotationSpeed * delta),
        )

        val yVelocity = self.velocity.y
        self.velocity = self.velocity.withY(0.0)
        self.velocity = self.velocity.moveToward(moveDirection * moveSpeed, acceleration * delta)
        if (Mathf.isEqualApprox(moveDirection.lengthSquared(), 0.0) && self.velocity.lengthSquared() < stoppingSpeed) {
            self.velocity = Vector3.ZERO
        }
        self.velocity = self.velocity.withY(yVelocity + gravity * delta)

        val groundSpeed = Vector2(self.velocity.x, self.velocity.z).length()
        val isJustJumping = Input.isActionJustPressed("jump") && self.isOnFloor()
        if (isJustJumping) {
            self.velocity = self.velocity.withY(self.velocity.y + jumpImpulse)
            skin.jump()
            jumpSound.play()
        } else if (!self.isOnFloor() && self.velocity.y < 0.0) {
            skin.fall()
        } else if (self.isOnFloor()) {
            if (groundSpeed > 0.0) {
                skin.move()
            } else {
                skin.idle()
            }
        }

        dustParticles.setEmitting(self.isOnFloor() && groundSpeed > 0.0)

        if (self.isOnFloor() && !wasOnFloorLastFrame) {
            landingSound.play()
        }

        wasOnFloorLastFrame = self.isOnFloor()
        self.moveAndSlide()
    }

}
