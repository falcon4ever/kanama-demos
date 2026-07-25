package net.multigesture.kanama.demos.platformer3d

import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.types.Vector3

/**
 * Web port of the platformer camera rig: follows the Player, applies camera_left/right/up/down
 * rotation and zoom_in/zoom_out from Input.get_axis, clamped like the desktop port.
 */
@ScriptClass(attachTo = "Node3D")
class View(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {
  @ScriptProperty var zoomMinimum: Long = 16

  @ScriptProperty var zoomMaximum: Long = 4

  @ScriptProperty var zoomSpeed: Long = 10

  @ScriptProperty var rotationSpeed: Long = 120

  private var cameraRotation: Vector3 = Vector3.ZERO
  private var zoom: Double = 10.0

  private var targetNode: Node3D? = null
  private lateinit var camera: Camera3D

  @OnReady
  fun ready() {
    targetNode = self.getAsOrNull("../Player", ::Node3D)
    camera = self.requireAs("Camera", ::Camera3D)
    cameraRotation = self.rotationDegrees
  }

  @OnPhysicsProcess
  fun physicsProcess(delta: Double) {
    targetNode?.let { self.position = self.position.lerp(it.position, delta * 4.0) }
    self.rotationDegrees = self.rotationDegrees.lerp(cameraRotation, delta * 6.0)

    camera.position = camera.position.lerp(Vector3(0.0, 0.0, zoom), delta * 8.0)

    // Rotation input.
    val inputY = Input.getAxis("camera_left", "camera_right")
    val inputX = Input.getAxis("camera_up", "camera_down")
    val limited = Vector3(inputX, inputY, 0.0).limitLength(1.0)
    cameraRotation = cameraRotation + limited * (rotationSpeed.toDouble() * delta)
    cameraRotation = cameraRotation.withX(Mathf.clamp(cameraRotation.x, -80.0, -10.0))

    // Zoom input.
    zoom += Input.getAxis("zoom_in", "zoom_out") * zoomSpeed.toDouble() * delta
    zoom = Mathf.clamp(zoom, zoomMaximum.toDouble(), zoomMinimum.toDouble())
  }
}
