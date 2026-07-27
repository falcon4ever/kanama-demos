package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MultiMeshInstance3D

/**
 * Web adaptation: procedural grass scattering needs MeshDataTool/MultiMesh introspection that
 * the Web backend does not model yet — the node stays inert (no grass; level plays the same).
 */
@ScriptClass(attachTo = "MultiMeshInstance3D")
class GrassScatter(godotObject: GodotHandle) :
  KanamaScript<MultiMeshInstance3D>(godotObject, ::MultiMeshInstance3D) {
  @OnReady fun ready() = Unit
}
