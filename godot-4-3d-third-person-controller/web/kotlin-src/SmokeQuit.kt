package thirdperson

import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.kotlinScriptInstance

/**
 * Web variant of the smoke root: the desktop SmokeQuit drives an env-gated scripted self-test;
 * the browser harness drives gameplay from outside instead. Method#1 (smoke_resume) presses
 * through the pause page; method#2 (smoke_teardown) releases the scene caches and frees the
 * root so live handles drain to zero.
 */
@ScriptClass(attachTo = "Node")
class SmokeQuit(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
  @RegisterFunction("smoke_resume")
  fun smokeResume() {
    // The DemoPage boots the tree paused; resume through its own flow so page state stays
    // consistent (falls back to a raw unpause if the page is missing).
    val page =
      self.getParent()?.let { Node(it.handle) }?.getNodeOrNull("DemoPage")
        ?.kotlinScriptInstance<DemoPage>()
    if (page != null) {
      page.resumeFromSmoke()
    } else {
      self.getTree().setPaused(false)
    }
  }

  @RegisterFunction("smoke_teardown")
  fun smokeTeardown() {
    DemoScenes.releaseWarmUp()
    val root = self.getParent() ?: error("SmokeQuit has no parent to tear down")
    Node(root.handle).queueFree()
  }
}
