package citybuilder

import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.kotlinScriptInstance

/**
 * Web variant of the smoke node: the desktop Smoke drives an env-gated in-editor GridMap check;
 * the browser harness drives gameplay from outside and calls [smokeTeardown] (method#1) to drain
 * every live handle to zero — including the Audio autoload, which lives outside the scene root
 * and would otherwise survive the root free.
 */
@ScriptClass(attachTo = "Node")
class Smoke(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
  @RegisterFunction("smoke_teardown")
  fun smokeTeardown() {
    // Structure resources persist in Godot's cache; release their hydrated PackedScene
    // handles so the live-handle count can drain to zero.
    self.getNodeOrNull("../Builder")?.kotlinScriptInstance<Builder>()?.structures?.forEach {
      it.releaseHydratedAssets()
    }
    self.getNodeOrNull("/root/Audio")?.let { audio ->
      audio.kotlinScriptInstance<Audio>()?.stopAll()
      Node(audio.handle).queueFree()
    }
    val root = self.getParent() ?: error("Smoke has no parent to tear down")
    Node(root.handle).queueFree()
  }
}
