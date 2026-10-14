package racing

import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node

/**
 * Web variant of the smoke autoload. The desktop Smoke is an env-gated QUIT PROBE (three physics
 * frames, one angularVelocity readability check, quit — it asserts nothing about gameplay),
 * so there is no desktop self-test to mirror here; gameplay coverage on Web comes from the
 * browser harness driving the run from outside (kanama scripts/web/drivers/demos/racing.mjs).
 * This override only provides [smokeTeardown], which frees the scene root and this autoload so live
 * handles drain to zero.
 */
@ScriptClass(attachTo = "Node")
class Smoke(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
  fun smokeTeardown() {
    val main = self.getParent()?.getNodeOrNull("Main") ?: error("Smoke could not find the Main scene root")
    main.queueFree()
    self.queueFree()
  }
}
