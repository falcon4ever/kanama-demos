import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
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
            SceneTree.delaySeconds(0.2)
            val tree = self.getTree()
            MainThread.post {
                tree.quit()
            }
        }
    }
}
