package thirdperson

import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEventKey
import net.multigesture.kanama.api.InputEventMouseButton
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.api.Window
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node")
class FullScreenHandler(godotObject: MemorySegment) : KanamaScript<Node>(godotObject, ::Node) {

    // Upstream sets this in _init() unconditionally (full_screen_handler.gd), so the handler
    // keeps processing while the tree is paused -- which is the whole point: F11 / alt-enter
    // must still toggle fullscreen from a pause menu. The port moved it to _ready and lost
    // the annotation, so it has never run.
    @OnReady
    fun ready() {
        self.setProcessMode(Node.PROCESS_MODE_ALWAYS)
    }

    @OnInput
    fun input(event: GodotObject) {
        if (OS.hasFeature("HTML5")) {
            val mouseButton = InputEventMouseButton.from(event) ?: return
            if (mouseButton.isPressed() && Input.getMouseMode() != Input.MOUSE_MODE_CAPTURED) {
                Input.setMouseMode(Input.MOUSE_MODE_CAPTURED)
            }
            return
        }

        val keyEvent = InputEventKey.from(event) ?: return
        if (!keyEvent.isPressed() || keyEvent.isEcho()) return

        val togglesFullscreen = keyEvent.getKeycode() == InputEventKey.KEY_F11 ||
            (keyEvent.getKeycode() == InputEventKey.KEY_ENTER && keyEvent.isAltPressed())
        if (!togglesFullscreen) return

        val root = Window(self.getTree().getRoot())
        root.setMode(
            if (root.getMode() == Window.MODE_FULLSCREEN) {
                Window.MODE_WINDOWED
            } else {
                Window.MODE_FULLSCREEN
            },
        )
    }
}
