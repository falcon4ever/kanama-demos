package charactercontroller

import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node

/**
 * Web variant of the smoke root: the desktop SmokeQuit drives an env-gated in-editor check; the
 * browser harness drives gameplay from outside instead and calls [smokeTeardown] (method#1) to
 * drain every live handle to zero — including the Events autoload, which lives outside the
 * scene root and would otherwise survive the root free.
 */
@ScriptClass(attachTo = "Node")
class SmokeQuit(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
  @RegisterFunction("smoke_teardown")
  fun smokeTeardown() {
    self.getNodeOrNull("/root/Events")?.let { events -> Node(events.handle).queueFree() }
    val root = self.getParent() ?: error("SmokeQuit has no parent to tear down")
    Node(root.handle).queueFree()
  }
}
