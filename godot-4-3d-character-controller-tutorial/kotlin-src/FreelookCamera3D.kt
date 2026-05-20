package charactercontroller

import net.multigesture.kanama.annotations.Export
import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEvent
import net.multigesture.kanama.api.InputEventKey
import net.multigesture.kanama.api.InputEventMouseButton
import net.multigesture.kanama.api.InputEventMouseMotion
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@GlobalClass
@ScriptClass(attachTo = "Camera3D")
class FreelookCamera3D(godotObject: MemorySegment) : KanamaScript<Camera3D>(godotObject, ::Camera3D) {

    @Export
    var movementSpeed = 10.0

    @Export
    var mouseSensitivity = 0.006

    @Export
    var fovChangeSpeed = 50.0

    private var previousCamera: Camera3D? = null
    private var cameraInputDirection = Vector2.ZERO
    private var targetFov = 0.0

    @OnReady
    fun ready() {
        self.setCurrent(false)
        self.setProcessMode(Node.PROCESS_MODE_ALWAYS)
        self.setProcess(self.isCurrent())
        targetFov = self.getFov()
    }

    @OnInput
    fun input(event: GodotObject) {
        val inputEvent = InputEvent(event.handle)
        if (inputEvent.isActionPressed("toggle_freelook_camera")) {
            toggleCameraMode()
        }

        if (!self.isCurrent()) return

        val motion = InputEventMouseMotion.from(event)
        if (motion != null && Input.getMouseMode() == Input.MOUSE_MODE_CAPTURED) {
            val relative = motion.getRelative()
            cameraInputDirection = Vector2(relative.x.toDouble() * mouseSensitivity, relative.y.toDouble() * mouseSensitivity)
        }

        val mouseButton = InputEventMouseButton.from(event) ?: return
        when (mouseButton.getButtonIndex()) {
            MOUSE_BUTTON_WHEEL_UP -> targetFov = maxOf(targetFov - 1.0, 1.0)
            MOUSE_BUTTON_WHEEL_DOWN -> targetFov = minOf(targetFov + 1.0, 179.0)
        }
    }

    @OnProcess
    fun process(delta: Double) {
        var movement = Vector3.ZERO
        if (Input.isKeyPressed(InputEventKey.KEY_W)) movement += Vector3.FORWARD
        if (Input.isKeyPressed(InputEventKey.KEY_A)) movement += Vector3.LEFT
        if (Input.isKeyPressed(InputEventKey.KEY_S)) movement += Vector3.BACK
        if (Input.isKeyPressed(InputEventKey.KEY_D)) movement += Vector3.RIGHT
        if (Input.isKeyPressed(InputEventKey.KEY_Q)) movement += Vector3.DOWN
        if (Input.isKeyPressed(InputEventKey.KEY_E)) movement += Vector3.UP

        val rotation = self.rotation
        self.rotation = rotation.copy(
            x = Mathf.clamp(rotation.x.toDouble() - cameraInputDirection.y.toDouble(), -Mathf.PI / 2.0, Mathf.PI / 2.0)
                .toFloat(),
            y = (rotation.y.toDouble() - cameraInputDirection.x.toDouble()).toFloat(),
        )

        self.globalPosition = self.globalPosition + self.globalTransform.basis * movement * delta * movementSpeed
        cameraInputDirection = Vector2.ZERO
        self.setFov(Mathf.moveToward(self.getFov(), targetFov, delta * fovChangeSpeed))
    }

    private fun toggleCameraMode() {
        if (!self.isCurrent()) {
            previousCamera = self.getViewport()?.getCamera3D()
            previousCamera?.let { previous ->
                self.setFov(previous.getFov())
                targetFov = self.getFov()
                self.globalTransform = Node3D(previous.handle).globalTransform
            }
            self.makeCurrent()
        } else {
            previousCamera?.makeCurrent()
        }

        self.getTree().setPaused(self.isCurrent())
        self.setProcess(self.isCurrent())
    }

    companion object {
        private const val MOUSE_BUTTON_WHEEL_UP = 4L
        private const val MOUSE_BUTTON_WHEEL_DOWN = 5L
    }
}
