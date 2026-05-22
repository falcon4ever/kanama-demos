package charactercontroller

import net.multigesture.kanama.annotations.Export
import net.multigesture.kanama.annotations.ExportGroup
import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.OnUnhandledInput
import net.multigesture.kanama.annotations.PropertyHint
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AudioStreamPlayer3D
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.GPUParticles3D
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEvent
import net.multigesture.kanama.api.InputEventMouseMotion
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.generated.EventsNames
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "CharacterBody3D")
class Player3DTemplate(godotObject: MemorySegment) : KanamaScript<CharacterBody3D>(godotObject, ::CharacterBody3D) {
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

    @Export(hint = PropertyHint.RANGE, hintString = "0.0,1.0,0.01")
    @ExportGroup("Camera")
    var mouseSensitivity = 0.25

    @Export
    var controllerCameraSensitivity = 2.5

    @Export
    var tiltUpperLimit = Mathf.PI / 3.0

    @Export
    var tiltLowerLimit = -Mathf.PI / 8.0

    var groundHeight = 0.0

    private var gravity = -30.0
    private var wasOnFloorLastFrame = true
    private var cameraInputDirection = Vector2.ZERO
    private lateinit var lastInputDirection: Vector3
    private lateinit var startPosition: Vector3

    private lateinit var cameraPivot: Node3D
    private lateinit var camera: Camera3D
    private lateinit var skinNode: Node3D
    private lateinit var skin: SophiaSkin
    private lateinit var landingSound: AudioStreamPlayer3D
    private lateinit var jumpSound: AudioStreamPlayer3D
    private lateinit var dustParticles: GPUParticles3D

    @OnReady
    fun ready() {
        lastInputDirection = self.globalBasis.z
        startPosition = self.globalPosition

        cameraPivot = self.requireAs("%CameraPivot", ::Node3D)
        camera = self.requireAs("%Camera3D", ::Camera3D)
        skinNode = self.requireAs("%SophiaSkin", ::Node3D)
        skin = skinNode.kotlinScriptInstance<SophiaSkin>()
            ?: error("%SophiaSkin is missing SophiaSkin script instance")
        landingSound = self.requireAs("%LandingSound", ::AudioStreamPlayer3D)
        jumpSound = self.requireAs("%JumpSound", ::AudioStreamPlayer3D)
        dustParticles = self.requireAs("%DustParticles", ::GPUParticles3D)

        val events = self.eventsNode()
        events.signal(EventsNames.Signals.killPlaneTouched).connectObject(self) {
            self.globalPosition = startPosition
            self.velocity = Vector3.ZERO
            skin.idle()
            self.setPhysicsProcess(true)
        }
        events.signal(EventsNames.Signals.flagReached).connect(self, argumentCount = 0) {
            self.setPhysicsProcess(false)
            skin.idle()
            dustParticles.setEmitting(false)
        }
    }

    @OnExitTree
    fun exitTree() {
        if (this::landingSound.isInitialized) {
            landingSound.stop()
        }
        if (this::jumpSound.isInitialized) {
            jumpSound.stop()
        }
    }

    @OnInput
    fun input(event: GodotObject) {
        val inputEvent = InputEvent(event.handle)
        if (inputEvent.isActionPressed("ui_cancel")) {
            Input.setMouseMode(Input.MOUSE_MODE_VISIBLE)
        } else if (inputEvent.isActionPressed("left_click")) {
            Input.setMouseMode(Input.MOUSE_MODE_CAPTURED)
        }
    }

    @OnUnhandledInput
    fun unhandledInput(event: GodotObject) {
        val motion = InputEventMouseMotion.from(event) ?: return
        if (Input.getMouseMode() != Input.MOUSE_MODE_CAPTURED) return
        val relative = motion.getRelative()
        cameraInputDirection = Vector2(-relative.x.toDouble() * mouseSensitivity, relative.y.toDouble() * mouseSensitivity)
    }

    @OnPhysicsProcess
    fun physicsProcess(delta: Double) {
        val cameraInput = Input.getVector("camera_left", "camera_right", "camera_up", "camera_down")
        if (cameraInput.length() > 0.0f) {
            cameraInputDirection += Vector2(
                -cameraInput.x.toDouble() * controllerCameraSensitivity,
                -cameraInput.y.toDouble() * controllerCameraSensitivity,
            )
        }

        val pivotRotation = cameraPivot.rotation
        cameraPivot.rotation = pivotRotation.copy(
            x = Mathf.clamp(pivotRotation.x.toDouble() + cameraInputDirection.y.toDouble() * delta, tiltLowerLimit, tiltUpperLimit)
                .toFloat(),
            y = (pivotRotation.y.toDouble() + cameraInputDirection.x.toDouble() * delta).toFloat(),
        )
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
            Mathf.lerpAngle(skinRotation.y.toDouble(), targetAngle, rotationSpeed * delta),
        )

        val yVelocity = self.velocity.y
        self.velocity = self.velocity.withY(0.0)
        self.velocity = self.velocity.moveToward(moveDirection * moveSpeed, acceleration * delta)
        if (Mathf.isEqualApprox(moveDirection.lengthSquared(), 0.0) && self.velocity.lengthSquared() < stoppingSpeed) {
            self.velocity = Vector3.ZERO
        }
        self.velocity = self.velocity.withY(yVelocity.toDouble() + gravity * delta)

        val groundSpeed = Vector2(self.velocity.x, self.velocity.z).length()
        val isJustJumping = Input.isActionJustPressed("jump") && self.isOnFloor()
        if (isJustJumping) {
            self.velocity = self.velocity.withY(self.velocity.y.toDouble() + jumpImpulse)
            skin.jump()
            jumpSound.play()
        } else if (!self.isOnFloor() && self.velocity.y < 0f) {
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
