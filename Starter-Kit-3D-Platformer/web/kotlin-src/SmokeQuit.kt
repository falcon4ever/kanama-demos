package net.multigesture.kanama.demos.platformer3d

import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node

/** Harness-only teardown for the Web smoke: frees the scene root so all handles drain to zero. */
@ScriptClass(attachTo = "Node")
class SmokeQuit(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
  @RegisterFunction("smoke_teardown")
  fun smokeTeardown() {
    val root = self.getParent() ?: error("SmokeQuit has no parent to tear down")
    Node(root.handle).queueFree()
  }
}
