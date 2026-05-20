package thirdperson

import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.CanvasItem
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEventKey
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.types.Basis
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node3D")
class CameraMode(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {
    @ScriptProperty
    var cameraSpeed: Long = 10L

    @ScriptProperty
    var mouseSensitivity: Double = 0.01

    private var camera: Camera3D? = null
    private var cachedCamera: Camera3D? = null
    private var enabled = false

    @OnReady
    fun ready() {
        if (OS.isDebugBuild()) {
            enabled = true
        }
        self.setProcess(enabled)
        self.setProcessInput(enabled)
    }

    @OnInput
    fun input(event: GodotObject) {
        val keyEvent = InputEventKey.from(event) ?: return
        if (keyEvent.isPressed() && !keyEvent.isEcho() && keyEvent.getKeycode() == InputEventKey.KEY_F10) {
            toggleCameraMode()
        }
    }

    @OnProcess
    fun process(delta: Double) {
        val currentCamera = camera ?: return
        if (!self.isVisible()) {
            return
        }

        var movement = Vector3.ZERO
        if (Input.isKeyPressed(InputEventKey.KEY_W)) movement += Vector3.FORWARD
        if (Input.isKeyPressed(InputEventKey.KEY_A)) movement += Vector3.LEFT
        if (Input.isKeyPressed(InputEventKey.KEY_S)) movement += Vector3.BACK
        if (Input.isKeyPressed(InputEventKey.KEY_D)) movement += Vector3.RIGHT
        if (Input.isKeyPressed(InputEventKey.KEY_Q)) movement += Vector3.DOWN
        if (Input.isKeyPressed(InputEventKey.KEY_E)) movement += Vector3.UP

        val mouseVelocity = Input.getLastMouseVelocity()
        val rotationInput = -mouseVelocity.x.toDouble() * mouseSensitivity
        val tiltInput = -mouseVelocity.y.toDouble() * mouseSensitivity

        var eulerRotation = currentCamera.globalTransform.basis.getEuler()
        eulerRotation = eulerRotation
            .withX((eulerRotation.x.toDouble() + tiltInput * delta).coerceIn(-Mathf.PI + 0.01, Mathf.PI - 0.01))
            .withY(eulerRotation.y.toDouble() + rotationInput * delta)

        currentCamera.globalTransform = currentCamera.globalTransform.withBasis(Basis.fromEuler(eulerRotation))
        currentCamera.globalPosition += currentCamera.globalTransform.basis * movement * delta * cameraSpeed
    }

    private fun toggleCameraMode() {
        if (self.isVisible()) {
            self.getTree().setPaused(false)
            cachedCamera?.setCurrent(true)
            camera?.queueFree()
            camera = null
            self.hide()
            setCameraModeToggleVisible(true)
        } else {
            self.getTree().setPaused(true)
            cachedCamera = self.getViewport()?.getCamera3D()
            val newCamera = Camera3D.create()
            camera = newCamera
            self.addChild(newCamera)
            newCamera.setCurrent(true)
            self.show()

            cachedCamera?.let { previousCamera ->
                newCamera.setFov(previousCamera.getFov())
                newCamera.globalTransform = previousCamera.globalTransform
            }

            setCameraModeToggleVisible(false)
        }
    }

    private fun setCameraModeToggleVisible(visible: Boolean) {
        for (node in self.getTree().getNodesInGroup("camera_mode_toggle")) {
            if (node.isClass("CanvasItem")) {
                if (visible) {
                    CanvasItem(node.handle).show()
                } else {
                    CanvasItem(node.handle).hide()
                }
            }
        }
    }
}
