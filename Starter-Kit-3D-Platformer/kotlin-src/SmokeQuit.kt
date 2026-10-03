package net.multigesture.kanama.demos.platformer3d

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.SceneTree

@ScriptClass(attachTo = "Node")
class SmokeQuit(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {

    @OnReady
    fun ready() {
        if (System.getenv("KANAMA_DEMO_SMOKE_QUIT") != "1") return
        launch {
            SceneTree.delaySeconds(0.2)
            // One line every smoke prints once its checks ran; the iOS runner requires it (task 111).
            println("[kanama:smoke] SmokeQuit complete")
            val tree = requireNotNull(self.getTree())
            MainThread.post {
                tree.quit()
            }
        }
    }
}
