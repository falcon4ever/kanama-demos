package citybuilder

import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.generated.Autoloads

/**
 * Web variant of the smoke autoload: the desktop Smoke drives an env-gated in-editor GridMap check;
 * the browser harness drives gameplay from outside and calls [smokeTeardown] to drain
 * every live handle to zero — including the Audio autoload, which lives outside the scene root
 * and would otherwise survive the root free.
 */
@ScriptClass(attachTo = "Node")
class Smoke(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
  fun smokeTeardown() {
    // Structure resources persist in Godot's cache; release their hydrated PackedScene
    // handles so the live-handle count can drain to zero.
    val main = self.getParent()?.getNodeOrNull("Main") ?: error("Smoke could not find the Main scene root")
    main.getNodeOrNull("Builder")?.kotlinScriptInstance<Builder>()?.structures?.forEach {
      it.model?.close()
      it.model = null
    }
    val audio = Autoloads.Audio
    audio.stopAll()
    audio.self.queueFree()
    main.queueFree()
    self.queueFree()
  }
}
