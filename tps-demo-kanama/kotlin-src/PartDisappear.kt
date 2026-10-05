package tps

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.CPUParticles3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import kotlinx.coroutines.cancel

@ScriptClass(attachTo = "CPUParticles3D")
class PartDisappear(godotObject: GodotHandle) : KanamaScript<CPUParticles3D>(godotObject, ::CPUParticles3D) {
    private lateinit var miniBlasts: CPUParticles3D

    @OnReady
    fun ready() {
        miniBlasts = self.requireAs("MiniBlasts", ::CPUParticles3D)
        launch {
            miniBlasts.emitting = true
            wait(0.2)
            self.emitting = true
            val smokeQuitAfterParts = System.getenv("KANAMA_TPS_SMOKE_QUIT_AFTER_PARTS") == "1"
            if (smokeQuitAfterParts) {
                GD.print("TPS smoke part disappearance emitted")
                val quitDelay = System.getenv("KANAMA_TPS_SMOKE_QUIT_AFTER_PARTS_DELAY")?.toDoubleOrNull() ?: 4.0
                wait(quitDelay)
                requireNotNull(self.getTree()).quit()
                return@launch
            }
            wait(self.lifetime * 2.0)
            self.queueFree()
        }
    }

    @OnExitTree
    fun exitTree() {
        cancelCoroutines()
        if (::miniBlasts.isInitialized) {
            miniBlasts.emitting = false
        }
        self.emitting = false
    }
}
