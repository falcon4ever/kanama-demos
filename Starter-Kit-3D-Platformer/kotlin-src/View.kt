package net.multigesture.kanama.demos.platformer3d

import net.multigesture.kanama.annotations.ExportGroup
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Export
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.types.Vector3
import net.multigesture.kanama.api.KanamaScript

@ScriptClass(attachTo = "Node3D")
class View(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {

	@ExportGroup("Properties")
	@Export
	var target: Node3D? = null

	@ExportGroup("Zoom")
	@Export
	var zoomMinimum: Long = 16

	@Export
	var zoomMaximum: Long = 4

	@Export
	var zoomSpeed: Long = 10

	@ExportGroup("Rotation")
	@Export
	var rotationSpeed: Long = 120

	private var cameraRotation: Vector3 = Vector3.ZERO
	private var zoom: Double = 10.0

	private lateinit var targetNode: Node3D
	private lateinit var camera: Camera3D

	@OnReady
	fun ready() {
		targetNode = target ?: error("View requires its target export (Properties/Target)")
		camera = self.requireAs("Camera", ::Camera3D)
		cameraRotation = self.rotationDegrees
	}

	@OnPhysicsProcess
	fun physicsProcess(delta: Double) {
		self.position = self.position.lerp(targetNode.position, delta * 4.0)
		self.rotationDegrees = self.rotationDegrees.lerp(cameraRotation, delta * 6.0)

		camera.position = camera.position.lerp(Vector3(0f, 0f, zoom.toFloat()), delta * 8.0)

		// Rotation input
		val inputY = Input.getAxis("camera_left", "camera_right")
		val inputX = Input.getAxis("camera_up", "camera_down")
		val raw = Vector3(inputX.toFloat(), inputY.toFloat(), 0f)
		val limited = raw.limitLength(1.0)
		cameraRotation = cameraRotation + limited * (rotationSpeed.toDouble() * delta)
		cameraRotation = cameraRotation.withX(Mathf.clamp(cameraRotation.x.toDouble(), -80.0, -10.0))

		// Zoom input
		zoom += Input.getAxis("zoom_in", "zoom_out") * zoomSpeed.toDouble() * delta
		zoom = Mathf.clamp(zoom, zoomMaximum.toDouble(), zoomMinimum.toDouble())
	}
}
