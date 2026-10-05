package net.multigesture.kanama.demos.match3

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.SceneTree
import net.multigesture.kanama.api.kotlinScriptInstance

@ScriptClass(attachTo = "Node")
class SmokeQuit(godotObject: GodotHandle) :
  KanamaScript<Node>(godotObject, ::Node) {

  @OnReady
  fun ready() {
    if (System.getenv("KANAMA_DEMO_SMOKE_QUIT") != "1") return
    launch {
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
      // Kanama task 129 A: one swipe made of touch events, through Godot's touch -> mouse
      // emulation (SmokeTouchSwipe). The release must reach Main._input and become one move;
      // otherwise the completion line below is withheld, which fails the desktop and iOS runners.
      val touch = SmokeTouchSwipe.run(main)
      if (touch == null) {
        println("[kanama:smoke] match3 touch: skipped (this platform cannot inject input events)")
      } else {
        println("[kanama:smoke] match3 touch: $touch")
        if (main.dragReleases != 1 || main.moves != 1) {
          println("[kanama:smoke] FAIL match3 touch: expected releases=1 moves=1 ($touch)")
          val tree = requireNotNull(self.getTree())
          MainThread.post { tree.quit(1) }
          return@launch
        }
      }
      // One line every smoke prints once its checks ran; the iOS runner requires it (task 111).
      println("[kanama:smoke] SmokeQuit complete")
      val tree = requireNotNull(self.getTree())
      MainThread.post { tree.quit() }
    }
  }
}
