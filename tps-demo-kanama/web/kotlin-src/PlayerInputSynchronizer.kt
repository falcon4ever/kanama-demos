package tps

import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.ColorRect
import net.multigesture.kanama.api.DisplayServer
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEventMouseMotion
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.MultiplayerSynchronizer
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.PhysicsBody3D
import net.multigesture.kanama.api.PhysicsRayQueryParameters3D
import net.multigesture.kanama.api.TextureRect
import net.multigesture.kanama.api.getActionStrength
import net.multigesture.kanama.api.getMultiplayer
import net.multigesture.kanama.api.getWorld3d
import net.multigesture.kanama.api.globalTransform
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.api.makeCurrent
import net.multigesture.kanama.api.position
import net.multigesture.kanama.api.projectRayNormal
import net.multigesture.kanama.api.projectRayOrigin
import net.multigesture.kanama.api.setProcess
import net.multigesture.kanama.api.setProcessInput
import net.multigesture.kanama.api.size
import net.multigesture.kanama.api.isActionJustReleased
import net.multigesture.kanama.api.orthonormalize
import net.multigesture.kanama.types.Basis
import net.multigesture.kanama.types.Color
import net.multigesture.kanama.types.Quaternion
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector3

@ScriptClass(attachTo = "MultiplayerSynchronizer")
class PlayerInputSynchronizer(godotObject: GodotHandle) :
  KanamaScript<MultiplayerSynchronizer>(godotObject, ::MultiplayerSynchronizer) {

  private var toggledAim = false
  private var fallFadeAlpha = 0.0
  private var aimingTimer = 0.0

  @ScriptProperty var aiming = false

  @ScriptProperty(name = "shoot_target") var shootTarget = Vector3.ZERO

  @ScriptProperty var motion = Vector2.ZERO

  @ScriptProperty var shooting = false

  @ScriptProperty var jumping = false

  @ScriptProperty(name = "camera_animation") var cameraAnimation: AnimationPlayer? = null

  @ScriptProperty var crosshair: TextureRect? = null

  @ScriptProperty(name = "camera_base") var cameraBase: Node3D? = null

  @ScriptProperty(name = "camera_rot") var cameraRot: Node3D? = null

  @ScriptProperty(name = "camera_camera") var cameraCamera: Camera3D? = null

  @ScriptProperty(name = "color_rect") var colorRect: ColorRect? = null

  @OnReady
  fun ready() {
    if (self.getMultiplayerAuthority().toLong() == self.getMultiplayer()?.getUniqueId()?.toLong()) {
      cameraCamera?.makeCurrent()
      if (DisplayServer.getName() != "headless") {
        Input.setMouseMode(Input.MOUSE_MODE_CAPTURED)
      }
    } else {
      self.setProcess(false)
      self.setProcessInput(false)
      colorRect?.hide()
    }
  }

  @OnProcess
  fun process(delta: Double) {
    motion =
      Vector2(
        Input.getActionStrength("move_right") - Input.getActionStrength("move_left"),
        Input.getActionStrength("move_back") - Input.getActionStrength("move_forward"),
      )
    val cameraMove =
      Vector2(
        Input.getActionStrength("view_right") - Input.getActionStrength("view_left"),
        Input.getActionStrength("view_up") - Input.getActionStrength("view_down"),
      )
    var cameraSpeed = delta * CAMERA_CONTROLLER_ROTATION_SPEED
    if (aiming) cameraSpeed *= 0.5
    rotateCamera(cameraMove * cameraSpeed)

    val currentAim =
      if (Input.isActionJustReleased("aim") && aimingTimer <= AIM_HOLD_THRESHOLD) {
        toggledAim = true
        true
      } else {
        if (Input.isActionJustPressed("aim")) toggledAim = false
        toggledAim || Input.isActionPressed("aim")
      }

    if (currentAim) {
      aimingTimer += delta
    } else {
      aimingTimer = 0.0
    }

    if (aiming != currentAim) {
      aiming = currentAim
      cameraAnimation?.play(if (aiming) "shoot" else "far")
    }

    if (Input.isActionJustPressed("jump")) {
      requestJump()
    }

    shooting = Input.isActionPressed("shoot")
    if (shooting) {
      updateShootTarget()
    }

    updateFallFade(delta)
  }

  @OnInput
  fun input(inputEvent: GodotObject) {
    val motionEvent = InputEventMouseMotion.from(inputEvent)
    if (motionEvent != null) {
      var cameraSpeed = CAMERA_MOUSE_ROTATION_SPEED
      if (aiming) cameraSpeed *= 0.75
      rotateCamera(motionEvent.getRelative() * cameraSpeed)
    }
  }

  @RegisterFunction("rotate_camera")
  fun rotateCamera(move: Vector2) {
    val base = cameraBase ?: return
    val rot = cameraRot ?: return
    base.rotateY(-move.x)
    base.orthonormalize()
    rot.rotation =
      rot.rotation.withX((rot.rotation.x + move.y).coerceIn(CAMERA_X_ROT_MIN, CAMERA_X_ROT_MAX))
  }

  @RegisterFunction("get_aim_rotation")
  fun getAimRotation(): Double {
    val cameraX = (cameraRot?.rotation?.x ?: 0.0).coerceIn(CAMERA_X_ROT_MIN, CAMERA_X_ROT_MAX)
    return if (cameraX >= 0.0) -cameraX / CAMERA_X_ROT_MAX else cameraX / CAMERA_X_ROT_MIN
  }

  @RegisterFunction("get_camera_base_quaternion")
  fun getCameraBaseQuaternion(): Quaternion =
    cameraBase?.globalTransform?.basis?.getRotationQuaternion() ?: Quaternion.IDENTITY

  @RegisterFunction("get_camera_rotation_basis")
  fun getCameraRotationBasis(): Basis = cameraRot?.globalTransform?.basis ?: Basis.IDENTITY

  internal fun addCameraShakeTrauma(amount: Double) {
    cameraCamera?.kotlinScriptInstance<CameraNoiseShakeEffect>()?.addTrauma(amount)
  }

  @RegisterFunction
  fun jump() {
    jumping = true
  }

  // Web builds are single-player, so the desktop call-local RPC is just the local call.
  private fun requestJump() {
    jump()
  }

  private fun updateShootTarget() {
    val camera = cameraCamera ?: return
    val targetCrosshair = crosshair ?: return
    val center = targetCrosshair.position + targetCrosshair.size * 0.5
    val rayFrom = camera.projectRayOrigin(center)
    val rayDir = camera.projectRayNormal(center)
    val parentBody = self.getParent()?.let { PhysicsBody3D(it.handle) }
    val query =
      PhysicsRayQueryParameters3D.create(
        rayFrom,
        rayFrom + rayDir * 1000.0,
        0b11,
        parentBody?.let { listOf(it) } ?: emptyList(),
      )!! // create() never returns null for valid args
    val hit = self.getParent()?.let { Node3D(it.handle) }?.getWorld3d()?.directSpaceState?.intersectRay(query)
    shootTarget = hit?.position ?: (rayFrom + rayDir * 1000.0)
  }

  private fun updateFallFade(delta: Double) {
    val rect = colorRect ?: return
    val y = self.getParent()?.let { Node3D(it.handle).globalTransform.origin.y } ?: return
    // Web adaptation: the overlay's modulate is write-only here. Only its alpha ever changes
    // (the scene authors it as white with alpha 0 over a black fill), so the port tracks the
    // fade in Kotlin instead of reading the mirrored canvas snapshot back every frame.
    fallFadeAlpha =
      if (y < -17.0) {
        Mathf.min((-17.0 - y) / 15.0, 1.0)
      } else {
        Mathf.max(fallFadeAlpha * (1.0 - delta * 4.0), 0.0)
      }
    rect.modulate = Color(1.0f, 1.0f, 1.0f, fallFadeAlpha.toFloat())
  }

  private companion object {
    const val CAMERA_CONTROLLER_ROTATION_SPEED = 3.0
    const val CAMERA_MOUSE_ROTATION_SPEED = 0.001
    val CAMERA_X_ROT_MIN = net.multigesture.kanama.api.GD.degToRad(-89.9)
    val CAMERA_X_ROT_MAX = net.multigesture.kanama.api.GD.degToRad(70.0)
    const val AIM_HOLD_THRESHOLD = 0.4
  }
}
