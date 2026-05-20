import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GPUParticles2D
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.SceneTree
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.launch
import net.multigesture.kanama.api.KanamaScript

@ScriptClass(attachTo = "GPUParticles2D")
class Particles(godotObject: MemorySegment) : KanamaScript<GPUParticles2D>(godotObject, ::GPUParticles2D), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()


    // Functions
    @OnReady
    fun ready() {
        self.emitting = true
        kanamaScope.launch {
            SceneTree.delaySeconds(self.lifetime)
            self.queueFree()
        }
    }

    @OnExitTree
    fun exitTree() {
        kanamaScope.cancel()
    }
}
