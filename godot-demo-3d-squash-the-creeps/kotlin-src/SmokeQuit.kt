package squash

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.SceneTree
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "Node")
class SmokeQuit(godotObject: MemorySegment) : KanamaScript<Node>(godotObject, ::Node), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()

    @OnReady
    fun ready() {
        if (System.getenv("KANAMA_DEMO_SMOKE_QUIT") != "1") return
        kanamaScope.launch {
            if (System.getenv("KANAMA_SQUASH_SMOKE_MOVE") == "1") {
                Input.actionPress("move_right")
            }
            val frames = if (System.getenv("KANAMA_SQUASH_SMOKE_MOVE") == "1") 180 else 30
            repeat(frames) {
                MainThread.awaitNextFrame()
            }
            if (System.getenv("KANAMA_SQUASH_SMOKE_MOVE") == "1") {
                Input.actionRelease("move_right")
            }
            SceneTree.quit()
        }
    }
}
