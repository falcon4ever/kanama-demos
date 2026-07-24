package dodge

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.SceneTree
import net.multigesture.kanama.api.kotlinScriptInstance
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "Node")
class SmokeQuit(godotObject: GodotHandle) :
    KanamaScript<Node>(godotObject, ::Node), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()

    @OnReady
    fun ready() {
        if (System.getenv("KANAMA_DEMO_SMOKE_QUIT") != "1") return
        // Web smoke drives gameplay deterministically from the script (the browser
        // driver asserts + tears down): start a game, let mobs spawn and the score
        // tick, then quit. Movement/input is not simulated here (the desktop smoke's
        // KANAMA_DODGE_SMOKE_MOVE path is not needed for the Web gameplay assertion).
        kanamaScope.launch {
            self.getParent()?.kotlinScriptInstance<Main>()?.newGame()
                ?: error("SmokeQuit parent is missing Main script")
            SceneTree.delaySeconds(2.5)
            val tree = self.getTree()
            MainThread.post { tree.quit() }
        }
    }
}
