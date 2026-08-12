package charactercontroller

import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node

/**
 * Web variant of the smoke root. The desktop SmokeQuit is an env-gated QUIT TIMER (twenty
 * frames, unload, quit — it asserts nothing), so there is no desktop self-test to mirror here;
 * gameplay coverage on Web comes from the browser harness driving the run from outside
 * (kanama scripts/web/drivers/demos/charactercontroller.mjs, incl. the task-82 kill-plane
 * reset gate). This override only provides [smokeTeardown] (method#1), which drains every
 * live handle to zero — including the Events autoload, which lives outside the scene root
 * and would otherwise survive the root free.
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
