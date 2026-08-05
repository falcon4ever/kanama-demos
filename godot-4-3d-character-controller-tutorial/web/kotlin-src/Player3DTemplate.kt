package charactercontroller

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.OnUnhandledInput
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.AudioStreamPlayer3D
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.GPUParticles3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEvent
import net.multigesture.kanama.api.InputEventMouseMotion
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.globalBasis
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.api.setPhysicsProcess
import net.multigesture.kanama.generated.EventsNames
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector3

/**
 * Web port of the tutorial's player controller. Adaptations: globalBasis derives from the
 * synchronous global-rotation read (the rig is scale-1), and isEqualApprox is inlined.
 */
@ScriptClass(attachTo = "CharacterBody3D")
class Player3DTemplate(godotObject: GodotHandle) :
  KanamaScript<CharacterBody3D>(godotObject, ::CharacterBody3D) {
  @ScriptProperty var moveSpeed = 8.0

  @ScriptProperty var acceleration = 20.0

  @ScriptProperty var jumpImpulse = 12.0

  @ScriptProperty var rotationSpeed = 12.0

  @ScriptProperty var stoppingSpeed = 1.0

  @ScriptProperty var mouseSensitivity = 0.25

  @ScriptProperty var controllerCameraSensitivity = 2.5

  // Spelled literals (Mathf.PI / 3.0 and -Mathf.PI / 8.0): expression defaults are not
  // portable to the Web proxy, which needs a plain literal it can re-emit.
  @ScriptProperty var tiltUpperLimit = 1.0471975511965976

  @ScriptProperty var tiltLowerLimit = -0.39269908169872414

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
    lastInputDirection = self.globalBasis * Vector3(0.0, 0.0, 1.0)
    startPosition = self.globalPosition

    cameraPivot = self.requireAs("%CameraPivot", ::Node3D)
    camera = self.requireAs("%Camera3D", ::Camera3D)
    skinNode = self.requireAs("%SophiaSkin", ::Node3D)
    skin =
      skinNode.kotlinScriptInstance<SophiaSkin>()
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
    cameraInputDirection =
      Vector2(-relative.x * mouseSensitivity, relative.y * mouseSensitivity)
  }

  @OnPhysicsProcess
  fun physicsProcess(delta: Double) {
    val cameraInput = Input.getVector("camera_left", "camera_right", "camera_up", "camera_down")
    if (cameraInput.length() > 0.0) {
      cameraInputDirection +=
        Vector2(
          -cameraInput.x * controllerCameraSensitivity,
          -cameraInput.y * controllerCameraSensitivity,
        )
    }

    val pivotRotation = cameraPivot.rotation
    cameraPivot.rotation =
      pivotRotation
        .withX(
          Mathf.clamp(
            pivotRotation.x + cameraInputDirection.y * delta,
            tiltLowerLimit,
            tiltUpperLimit,
          )
        )
        .withY(pivotRotation.y + cameraInputDirection.x * delta)
    cameraInputDirection = Vector2.ZERO

    val rawInput = Input.getVector("move_left", "move_right", "move_up", "move_down", 0.4)
    val forward = camera.globalBasis * Vector3(0.0, 0.0, 1.0)
    val right = camera.globalBasis * Vector3(1.0, 0.0, 0.0)
    var moveDirection = forward * rawInput.y + right * rawInput.x
    moveDirection = moveDirection.withY(0.0).normalized()

    if (moveDirection.length() > 0.2) {
      lastInputDirection = moveDirection.normalized()
    }
    val targetAngle = Vector3.BACK.signedAngleTo(lastInputDirection, Vector3.UP)
    val skinRotation = skinNode.rotation
    skinNode.globalRotation =
      skinNode.globalRotation.withY(
        Mathf.lerpAngle(skinRotation.y, targetAngle, rotationSpeed * delta)
      )

    val yVelocity = self.velocity.y
    self.velocity = self.velocity.withY(0.0)
    self.velocity = self.velocity.moveToward(moveDirection * moveSpeed, acceleration * delta)
    if (moveDirection.lengthSquared() < 1e-9 && self.velocity.lengthSquared() < stoppingSpeed) {
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
