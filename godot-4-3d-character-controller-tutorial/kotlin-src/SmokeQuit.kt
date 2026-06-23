package charactercontroller

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.api.Node
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "Node")
class SmokeQuit(godotObject: MemorySegment) : KanamaScript<Node>(godotObject, ::Node), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()

    @OnReady
    fun ready() {
        if (System.getenv("KANAMA_DEMO_SMOKE_QUIT") != "1") return
        kanamaScope.launch {
            repeat(20) {
                MainThread.awaitNextFrame()
            }
            val tree = self.getTree()
            tree.unloadCurrentScene()
            MainThread.postAfterFrames(QUIT_AFTER_UNLOAD_FRAMES) {
                tree.quit()
            }
        }
    }

    companion object {
        private const val QUIT_AFTER_UNLOAD_FRAMES = 8
    }
}
