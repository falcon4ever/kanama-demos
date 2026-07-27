package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D

/**
 * Web adaptation: the debug fly-camera is debug-build-gated on desktop and the Web export
 * ships the release template, so the script stays inert (the scene's script slot resolves).
 */
@ScriptClass(attachTo = "Node3D")
class CameraMode(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {
  @OnReady fun ready() = Unit
}
