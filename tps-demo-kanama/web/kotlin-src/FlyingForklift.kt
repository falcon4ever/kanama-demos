package tps

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.SpotLight3D
import net.multigesture.kanama.api.getChild
import net.multigesture.kanama.api.getChildren
import net.multigesture.kanama.api.shadowEnabled

@ScriptClass(attachTo = "Node3D")
class FlyingForklift(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {
  @OnReady
  fun ready() {
    if (!TpsSettings.renderBool("shadow_mapping")) {
      self.requireAs("SpotLight3D", ::SpotLight3D).shadowEnabled = false
    }
    GD.randomize()
    val models = self.getChild(0)?.let { Node3D(it.handle).getChildren() } ?: return
    val enabled = (GD.randf() * models.size).toInt().coerceIn(0, models.lastIndex)
    models.forEachIndexed { index, node -> Node3D(node.handle).visible = index == enabled }
  }
}
