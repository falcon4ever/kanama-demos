package tps

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.DisplayServer
import net.multigesture.kanama.api.Engine
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.SceneMultiplayer
import net.multigesture.kanama.api.getMultiplayer
import net.multigesture.kanama.api.getName
import net.multigesture.kanama.api.getChildren
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.api.hasSignal
import net.multigesture.kanama.api.pushError
import net.multigesture.kanama.api.use
import net.multigesture.kanama.generated.LevelNames
import net.multigesture.kanama.generated.MenuNames

@ScriptClass(attachTo = "Node")
class Main(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
  /**
   * Browser-harness entry points. Main is the persistent scene root — it outlives the menu/level
   * swap — so the smoke drives play and teardown through it. Declared first so their method ids
   * stay stable: [smokeStartGame] is method#1 and [smokeTeardown] is method#2.
   */
  @RegisterFunction("smoke_start_game")
  fun smokeStartGame() {
    val menu =
      self.getChildren().firstNotNullOfOrNull { Node(it.handle).kotlinScriptInstance<Menu>() }
        ?: error("TPS smoke could not find the Menu script to start a game")
    menu.onPlayPressed()
  }

  @RegisterFunction("smoke_teardown")
  fun smokeTeardown() {
    // Godot's resource cache keeps the loaded scenes alive past the scene-root free, and the
    // settings ConfigFile is a Kotlin-owned handle: both must be released for the live-handle
    // count to drain to zero.
    TpsScenes.releaseCachedScenes()
    TpsSettings.releaseConfigFile()
    self.queueFree()
  }

  @OnReady
  fun ready() {
    if (DisplayServer.getName() == "headless") {
      Engine.maxFps = 60
    }
    SceneMultiplayer.fromApi(self.getMultiplayer())?.serverRelay = false
    GD.randomize()
    goToMainMenu()
  }

  @RegisterFunction("go_to_main_menu")
  fun goToMainMenu() {
    val menu = TpsScenes.scene(TpsScenes.MENU) ?: return
    self.getMultiplayer()?.getMultiplayerPeer()?.closeConnection()
    // close what you create (Kanama task 61): the engine keeps its own reference once assigned.
    TpsFactory.offlineMultiplayerPeer().use { self.getMultiplayer()?.multiplayerPeer = it }
    changeSceneToPacked(menu)
  }

  @RegisterFunction("replace_main_scene")
  fun replaceMainScene(resource: PackedScene) {
    GD.print("TPS Main received replace_main_scene")
    // The menu already reaches this through a deferred call, so the scene swap runs on an idle
    // frame with no menu script on the stack; deferring a second time would only delay it a frame.
    changeSceneToPacked(resource)
  }

  @RegisterFunction("change_scene_to_packed")
  fun changeSceneToPacked(resource: PackedScene) {
    GD.print("TPS Main changing scene")
    val node =
      resource.instantiate()
        ?: run {
          GD.pushError("TPS Main failed to instantiate PackedScene")
          return
        }
    for (child in self.getChildren()) {
      // Web adaptation: remove_child runs the child's _exit_tree synchronously, which tears its
      // script instance down in the middle of this command batch — the queued free that follows
      // could no longer resolve it. queue_free alone detaches and frees the old scene.
      Node(child.handle).queueFree()
    }
    val sceneRoot = Node(node.handle)
    self.addChild(sceneRoot)
    GD.print("TPS Main added scene root: ${sceneRoot.getName()}")
    if (sceneRoot.hasSignal(LevelNames.Signals.quit)) {
      sceneRoot.signal(LevelNames.Signals.quit).connect(self, argumentCount = 0) { goToMainMenu() }
    }
    if (sceneRoot.hasSignal(MenuNames.Signals.replaceMainScene)) {
      // The emitted PackedScene arrives as a tracked handle through the object-signal channel.
      sceneRoot.signal(MenuNames.Signals.replaceMainScene).connectObject(self) { emitted ->
        replaceMainScene(PackedScene(emitted.handle))
      }
    }
  }
}
