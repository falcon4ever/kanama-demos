package tps

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.DisplayServer
import net.multigesture.kanama.api.Engine
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.SceneMultiplayer
import net.multigesture.kanama.api.Window
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.generated.Autoloads
import net.multigesture.kanama.generated.LevelNames
import net.multigesture.kanama.generated.MenuNames
import net.multigesture.kanama.generated.LevelSignals
import net.multigesture.kanama.generated.MenuSignals

@ScriptClass(attachTo = "Node")
class Main(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
    /**
     * Browser-harness entry points. Main is the persistent scene root — it outlives the menu/level
     * swap — so the Web smoke drives play and teardown through it. Declared first so their Web
     * method ids stay stable: [smokeStartGame] is method#1 and [smokeTeardown] is method#2.
     */
    fun smokeStartGame() {
        val menu = self.getChildren().firstNotNullOfOrNull { it.kotlinScriptInstance<Menu>() }
            ?: error("TPS smoke could not find the Menu script to start a game")
        menu.onPlayPressed()
    }

    fun smokeTeardown() {
        // Godot's resource cache keeps the loaded scenes alive past the scene-root free, and the
        // settings ConfigFile is a Kotlin-owned handle: both must be released for the live-handle
        // count to drain to zero.
        TpsScenes.releaseCachedScenes()
        Autoloads.Settings.releaseConfigFile()
        self.queueFree()
    }

    @OnReady
    fun ready() {
        if (DisplayServer.getName() == "headless") {
            Engine.maxFps = 60
        }
        self.withMultiplayer { SceneMultiplayer.fromApi(it)?.serverRelay = false }
        net.multigesture.kanama.api.GD.randomize()
        self.getWindow()?.mode = Window.Mode(Autoloads.Settings.videoLong("display_mode"))
        goToMainMenu()
    }

    fun goToMainMenu() {
        val menu = TpsScenes.scene(TpsScenes.MENU) ?: return
        self.withMultiplayer { api -> api.getMultiplayerPeer()?.use { it.closeConnection() } }
        // close what you create (Kanama task 61): the engine keeps its own reference once assigned.
        TpsFactory.offlineMultiplayerPeer().use { peer -> self.withMultiplayer { it.multiplayerPeer = peer } }
        changeSceneToPacked(menu)
    }

    fun replaceMainScene(resource: net.multigesture.kanama.api.PackedScene) {
        GD.print("TPS Main received replace_main_scene")
        self.callDeferred("change_scene_to_packed", resource)
    }

    fun changeSceneToPacked(resource: net.multigesture.kanama.api.PackedScene) {
        GD.print("TPS Main changing scene")
        val node = resource.instantiate() ?: run {
            GD.pushError("TPS Main failed to instantiate PackedScene")
            return
        }
        for (child in self.getChildren()) {
            self.removeChild(child)
            child.queueFree()
        }
        self.addChild(node)
        GD.print("TPS Main added scene root: ${node.getName()}")
        if (node.hasSignal(LevelNames.Signals.quit)) {
            LevelSignals.quit(node).connect { goToMainMenu() }
        }
        if (node.hasSignal(MenuNames.Signals.replaceMainScene)) {
            // The emitted PackedScene rides back through call_deferred.
            MenuSignals.replaceMainScene(node).connect { emitted ->
                GD.print("TPS Main received replace_main_scene")
                self.callDeferred("change_scene_to_packed", emitted)
            }
        }
    }
}
