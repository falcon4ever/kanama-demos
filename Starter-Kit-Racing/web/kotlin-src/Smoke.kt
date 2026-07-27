package racing

import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D

/**
 * Web variant of the smoke node: the desktop Smoke drives an env-gated in-editor check; the
 * browser harness drives gameplay from outside and calls [smokeTeardown] (method#1) to free
 * the scene root so live handles drain to zero.
 */
@ScriptClass(attachTo = "Node3D")
class Smoke(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {
  @RegisterFunction("smoke_teardown")
  fun smokeTeardown() {
    val root = self.getParent() ?: error("Smoke has no parent to tear down")
    Node(root.handle).queueFree()
  }
}
