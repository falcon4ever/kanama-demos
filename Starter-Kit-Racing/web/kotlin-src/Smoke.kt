package racing

import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D

/**
 * Web variant of the smoke node. The desktop Smoke is an env-gated QUIT PROBE (three physics
 * frames, one angularVelocity readability check, quit — it asserts nothing about gameplay),
 * so there is no desktop self-test to mirror here; gameplay coverage on Web comes from the
 * browser harness driving the run from outside (kanama scripts/web/drivers/demos/racing.mjs).
 * This override only provides [smokeTeardown] (method#1), which frees the scene root so live
 * handles drain to zero.
 */
@ScriptClass(attachTo = "Node3D")
class Smoke(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {
  @RegisterFunction("smoke_teardown")
  fun smokeTeardown() {
    val root = self.getParent() ?: error("Smoke has no parent to tear down")
    Node(root.handle).queueFree()
  }
}
