package thirdperson

import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Export
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.CanvasItem
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEvent
import net.multigesture.kanama.api.InputEventKey
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Key
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.types.Basis
import net.multigesture.kanama.types.Vector3

@ScriptClass(attachTo = "Node3D")
class CameraMode(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {
    @Export
    var cameraSpeed: Long = 10L

    @Export
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
    fun input(event: InputEvent) {
        val keyEvent = InputEventKey.from(event) ?: return
        if (keyEvent.isPressed() && !keyEvent.isEcho() && keyEvent.getKeycode() == Key.F10) {
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
        if (Input.isKeyPressed(Key.W)) movement += Vector3.FORWARD
        if (Input.isKeyPressed(Key.A)) movement += Vector3.LEFT
        if (Input.isKeyPressed(Key.S)) movement += Vector3.BACK
        if (Input.isKeyPressed(Key.D)) movement += Vector3.RIGHT
        if (Input.isKeyPressed(Key.Q)) movement += Vector3.DOWN
        if (Input.isKeyPressed(Key.E)) movement += Vector3.UP

        val mouseVelocity = Input.getLastMouseVelocity()
        val rotationInput = -mouseVelocity.x * mouseSensitivity
        val tiltInput = -mouseVelocity.y * mouseSensitivity

        var eulerRotation = currentCamera.globalTransform.basis.getEuler()
        eulerRotation = eulerRotation
            .withX((eulerRotation.x + tiltInput * delta).coerceIn(-Mathf.PI + 0.01, Mathf.PI - 0.01))
            .withY(eulerRotation.y + rotationInput * delta)

        currentCamera.globalTransform = currentCamera.globalTransform.withBasis(Basis.fromEuler(eulerRotation))
        currentCamera.globalPosition += currentCamera.globalTransform.basis * movement * delta * cameraSpeed
    }

    private fun toggleCameraMode() {
        if (self.isVisible()) {
            requireNotNull(self.getTree()).setPaused(false)
            cachedCamera?.setCurrent(true)
            camera?.queueFree()
            camera = null
            self.hide()
            setCameraModeToggleVisible(true)
        } else {
            requireNotNull(self.getTree()).setPaused(true)
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
        for (node in requireNotNull(self.getTree()).getNodesInGroup("camera_mode_toggle")) {
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
