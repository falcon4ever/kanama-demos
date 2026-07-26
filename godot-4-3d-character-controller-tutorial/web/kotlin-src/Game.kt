package charactercontroller

import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.InputEvent
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node

/**
 * Web adaptation: the desktop fullscreen toggle drives DisplayServer window modes, which a
 * browser page does not own (fullscreen belongs to the browser chrome). The action is consumed
 * as a documented no-op.
 */
@ScriptClass(attachTo = "Node")
class Game(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
  @OnInput
  fun input(event: GodotObject) {
    if (!InputEvent(event.handle).isActionPressed("toggle_fullscreen")) return
    // Browser no-op: window-mode toggling is not part of the Web surface.
  }
}
