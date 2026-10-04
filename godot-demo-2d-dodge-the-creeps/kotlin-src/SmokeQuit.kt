package dodge

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.kotlinScriptInstance

@ScriptClass(attachTo = "Node")
class SmokeQuit(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {

    @OnReady
    fun ready() {
        if (System.getenv("KANAMA_DEMO_SMOKE_QUIT") != "1") return
        launch {
            self.getParent()
                ?.kotlinScriptInstance<Main>()
                ?.newGame()
                ?: error("SmokeQuit parent is missing Main script")
            if (System.getenv("KANAMA_DODGE_SMOKE_MOVE") == "1") {
                Input.actionPress("move_right")
            }
            val frames = if (System.getenv("KANAMA_DODGE_SMOKE_MOVE") == "1") 180 else 45
            repeat(frames) {
                MainThread.awaitNextFrame()
            }
            if (System.getenv("KANAMA_DODGE_SMOKE_MOVE") == "1") {
                Input.actionRelease("move_right")
            }
            // One line every smoke prints once its checks ran; the iOS runner requires it (task 111).
            println("[kanama:smoke] SmokeQuit complete")
            val tree = requireNotNull(self.getTree())
            MainThread.post { tree.quit() }
        }
    }
}
