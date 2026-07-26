package charactercontroller

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript

/**
 * Web adaptation: the desktop freelook camera is an editor/debug tool (camera switching, FOV
 * scroll, tree pausing) outside the tutorial's gameplay; it stays inert on Web so the scene's
 * script slot resolves. The gameplay camera is the player's own pivot rig.
 */
@ScriptClass(attachTo = "Camera3D")
class FreelookCamera3D(godotObject: GodotHandle) :
  KanamaScript<Camera3D>(godotObject, ::Camera3D) {
  @OnReady fun ready() = Unit
}
