package tps

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.CPUParticles3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript

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
