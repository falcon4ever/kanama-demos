package thirdperson

import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEventMouseButton
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node

/**
 * Web port: only the browser branch survives — any click recaptures the mouse when it is not
 * captured (the desktop F11 fullscreen toggle drives window modes a page does not own).
 * NOTE: the desktop source checks the Godot-3-era "HTML5" feature tag, which is FALSE on 4.x
 * web exports; this port checks capture state directly.
 */
@ScriptClass(attachTo = "Node")
class FullScreenHandler(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {

  @OnInput
  fun input(event: GodotObject) {
    val mouseButton = InputEventMouseButton.from(event) ?: return
    if (mouseButton.isPressed() && Input.getMouseMode() != Input.MOUSE_MODE_CAPTURED) {
      Input.setMouseMode(Input.MOUSE_MODE_CAPTURED)
    }
  }
}
