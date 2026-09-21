package charactercontroller

import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.InputEvent
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Window

@ScriptClass(attachTo = "Node")
class Game(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
    @OnInput
    fun input(event: GodotObject) {
        if (!InputEvent(event.handle).isActionPressed("toggle_fullscreen")) return

        val viewport = self.getViewport()?.let { Window(it.handle) } ?: requireNotNull(self.getTree().getRoot())
        viewport.setMode(
            if (viewport.getMode() != Window.MODE_FULLSCREEN) {
                Window.MODE_FULLSCREEN
            } else {
                Window.MODE_WINDOWED
            },
        )
    }
}
