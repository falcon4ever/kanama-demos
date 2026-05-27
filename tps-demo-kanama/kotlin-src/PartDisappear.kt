package tps

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.CPUParticles3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Timer
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "CPUParticles3D")
class PartDisappear(godotObject: MemorySegment) : KanamaScript<CPUParticles3D>(godotObject, ::CPUParticles3D), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()
    private lateinit var miniBlasts: CPUParticles3D

    @OnReady
    fun ready() {
        miniBlasts = self.requireAs("MiniBlasts", ::CPUParticles3D)
        kanamaScope.launch {
            miniBlasts.emitting = true
            self.getTree().createTimer(0.2)?.signal(Timer.Signals.timeout)?.await(self, argumentCount = 0)
            self.emitting = true
            val smokeQuitAfterParts = System.getenv("KANAMA_TPS_SMOKE_QUIT_AFTER_PARTS") == "1"
            if (smokeQuitAfterParts) {
                GD.print("TPS smoke part disappearance emitted")
                val quitDelay = System.getenv("KANAMA_TPS_SMOKE_QUIT_AFTER_PARTS_DELAY")?.toDoubleOrNull() ?: 4.0
                self.getTree().createTimer(quitDelay)?.signal(Timer.Signals.timeout)?.await(self, argumentCount = 0)
                self.getTree().quit()
                return@launch
            }
            self.getTree().createTimer(self.lifetime * 2.0)?.signal(Timer.Signals.timeout)?.await(self, argumentCount = 0)
            self.queueFree()
        }
    }

    @OnExitTree
    fun exitTree() {
        kanamaScope.cancel()
        if (::miniBlasts.isInitialized) {
            miniBlasts.emitting = false
        }
        self.emitting = false
    }
}
