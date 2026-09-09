package net.multigesture.kanama.demos.match3

import kotlinx.coroutines.launch
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.SceneTree
import net.multigesture.kanama.api.kotlinScriptInstance

@ScriptClass(attachTo = "Node")
class SmokeQuit(godotObject: GodotHandle) :
  KanamaScript<Node>(godotObject, ::Node), KanamaCoroutineOwner {
  override val kanamaScope = KanamaScope()

  @OnReady
  fun ready() {
    if (System.getenv("KANAMA_DEMO_SMOKE_QUIT") != "1") return
    kanamaScope.launch {
      // Task 80 slice 6, desktop half of the differential probe: Web CALLS
      // Main.differential_probe through the bridge; desktop reads this line from the smoke
      // log (kanama scripts/web/differential_diff.py). Sample AFTER the board settles, not
      // at _ready -- a _ready sample once reported a timing difference as a platform
      // difference (textures/cursors read empty at _ready, populated later). The env gate
      // lives here, not in Main, so no smoke logic rides a player's default path.
      SceneTree.delaySeconds(0.1)
      val main =
        self.getParent()?.kotlinScriptInstance<Main>()
          ?: error("SmokeQuit parent is missing the Main script")
      GD.print("KANAMA-DIFF match3.Main ${main.differentialProbe()}")
      SceneTree.delaySeconds(0.1)
      val tree = self.getTree()
      MainThread.post { tree.quit() }
    }
  }
}
