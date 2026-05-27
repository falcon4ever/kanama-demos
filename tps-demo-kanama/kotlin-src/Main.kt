package tps

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Engine
import net.multigesture.kanama.api.DisplayServer
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.SceneMultiplayer
import net.multigesture.kanama.generated.LevelNames
import net.multigesture.kanama.generated.MenuNames
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node")
class Main(godotObject: MemorySegment) : KanamaScript<Node>(godotObject, ::Node) {
    @OnReady
    fun ready() {
        if (DisplayServer.getName() == "headless") {
            Engine.maxFps = 60
        }
        SceneMultiplayer.fromApi(self.getMultiplayer())?.serverRelay = false
        net.multigesture.kanama.api.GD.randomize()
        goToMainMenu()
    }

    @RegisterFunction("go_to_main_menu")
    fun goToMainMenu() {
        val menu = TpsScenes.scene(TpsScenes.MENU) ?: return
        self.getMultiplayer()?.getMultiplayerPeer()?.closeConnection()
        self.getMultiplayer()?.multiplayerPeer = TpsFactory.offlineMultiplayerPeer()
        changeSceneToPacked(menu)
    }

    @RegisterFunction("replace_main_scene")
    fun replaceMainScene(resource: net.multigesture.kanama.api.PackedScene) {
        GD.print("TPS Main received replace_main_scene")
        self.callDeferred("change_scene_to_packed", resource)
    }

    @RegisterFunction("change_scene_to_packed")
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
            node.signal(LevelNames.Signals.quit).connect(self, argumentCount = 0) { goToMainMenu() }
        }
        if (node.hasSignal(MenuNames.Signals.replaceMainScene)) {
            node.signal(MenuNames.Signals.replaceMainScene).connect(self, argumentCount = 1) { args ->
                val scene = args.firstOrNull() as? net.multigesture.kanama.api.PackedScene ?: run {
                    GD.pushError("TPS Main replace_main_scene signal did not provide a PackedScene")
                    return@connect
                }
                replaceMainScene(scene)
            }
        }
    }
}
