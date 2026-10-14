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

/**
 * Every smoke and probe hook of this demo, as an autoload (Kanama task 131 item 17). Gameplay
 * classes keep no smoke code; this node reaches them through the scene root's children.
 *
 * Desktop: [ready] runs the env-gated self-test (`KANAMA_DEMO_SMOKE_QUIT=1`). Web: the browser
 * driver CALLS [differentialProbe] through the bridge, which captures every `*.Smoke` handle.
 */
@ScriptClass(attachTo = "Node")
class Smoke(godotObject: GodotHandle) :
  KanamaScript<Node>(godotObject, ::Node) {

  private fun main(): Main =
    self.getParent()?.getChildren()?.firstNotNullOfOrNull { it.kotlinScriptInstance<Main>() }
      ?: error("Match3 smoke could not find the Main script among the scene root's children")

  /**
   * Web harness teardown: the autoload outlives the scene root like Audio does, and the node
   * handles [differentialProbe] looked up are released only with this script, so the driver
   * frees it last to reach the live-handle baseline.
   */
  fun smokeTeardown() {
    self.queueFree()
  }

  /**
   * Task 80 slice 6: a deterministic dump of Main's HYDRATED state.
   *
   * The same Kotlin compiles for desktop and Web, so identical output is expected and
   * any difference is by definition a bug. It deliberately samples the three hydration
   * paths the task-88 audit found bugs in: plain scalar exports (the literal path),
   * an exported ARRAY of objects (the path where a null element used to mint a live
   * handle) and object exports.
   *
   * Object fields report presence, not identity: a handle number legitimately
   * differs between platforms, so comparing it would report a difference that is not a defect.
   *
   * Web reads it by CALLING differential_probe through the bridge; desktop reads the
   * `KANAMA-DIFF match3.Smoke ...` line that [ready] prints under its smoke gate.
   */
  fun differentialProbe(): String {
    val main = main()
    return listOf(
        "width=${main.width}",
        "height=${main.height}",
        "offset=${main.offset}",
        "textures=${main.textures.size}",
        "tileScene=${if (main.tileScene != null) 1 else 0}",
        "sparklesScene=${if (main.sparklesScene != null) 1 else 0}",
        "openHandCursor=${if (main.openHandCursor != null) 1 else 0}",
        "closedHandCursor=${if (main.closedHandCursor != null) 1 else 0}",
      )
      .joinToString(";")
  }

  @OnReady
  fun ready() {
    if (System.getenv("KANAMA_DEMO_SMOKE_QUIT") != "1") return
    launch {
      // Task 80 slice 6, desktop half of the differential probe: Web CALLS differential_probe
      // through the bridge; desktop reads this line from the smoke log (kanama
      // scripts/web/differential_diff.py). Sample AFTER the board settles, not at _ready --
      // a _ready sample once reported a timing difference as a platform difference
      // (textures/cursors read empty at _ready, populated later).
      SceneTree.delaySeconds(0.1)
      GD.print("KANAMA-DIFF match3.Smoke ${differentialProbe()}")
      SceneTree.delaySeconds(0.1)
      // Kanama task 129 A: one swipe made of touch events, through Godot's touch -> mouse
      // emulation (SmokeTouchSwipe). The release must reach Main._input and become one move;
      // otherwise the completion line below is withheld, which fails the desktop and iOS runners.
      val main = main()
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
