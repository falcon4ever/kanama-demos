package citybuilder

import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.Process
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEventMouseMotion
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment
import net.multigesture.kanama.api.KanamaScript

@ScriptClass(attachTo = "Node3D")
class View(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {

	private var cameraPosition = Vector3.ZERO
	private var cameraRotation = Vector3.ZERO
	private var zoom = 30.0
	private lateinit var camera: Node3D

	@OnReady
	fun ready() {
		camera = self.requireAs("Camera", ::Node3D)
		cameraRotation = self.rotationDegrees
	}

	@Process
	fun process(delta: Double) {
		self.position = self.position.lerp(cameraPosition, delta * 8.0)
		self.rotationDegrees = self.rotationDegrees.lerp(cameraRotation, delta * 6.0)
		camera.position = camera.position.lerp(Vector3(0, 0, zoom), delta * 8.0)
		handleInput()
	}

	private fun handleInput() {
		var input = Vector3.ZERO
		input = input.withX(Input.getAxis("camera_left", "camera_right"))
		input = input.withZ(Input.getAxis("camera_forward", "camera_back"))
		input = input.rotated(Vector3.UP, self.rotation.y.toDouble()).normalized()
		cameraPosition += input / 4.0

		if (Input.isActionJustReleased("zoom_in")) {
			zoom = Mathf.max(15.0, zoom - 5.0)
		}
		if (Input.isActionJustReleased("zoom_out")) {
			zoom = Mathf.min(80.0, zoom + 5.0)
		}
		if (Input.isActionPressed("camera_center")) {
			cameraPosition = Vector3.ZERO
		}
	}

	@OnInput
	fun input(event: GodotObject) {
		val motion = InputEventMouseMotion.from(event) ?: return
		if (Input.isActionPressed("camera_rotate")) {
			cameraRotation += Vector3(0, -motion.getRelative().x.toDouble() / 10.0, 0)
		}
	}
}
