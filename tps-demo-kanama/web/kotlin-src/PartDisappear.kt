package tps

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.CPUParticles3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.SceneTree
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "CPUParticles3D")
class PartDisappear(godotObject: GodotHandle) :
  KanamaScript<CPUParticles3D>(godotObject, ::CPUParticles3D), KanamaCoroutineOwner {
  override val kanamaScope = KanamaScope()
  private lateinit var miniBlasts: CPUParticles3D

  @OnReady
  fun ready() {
    miniBlasts = self.requireAs("MiniBlasts", ::CPUParticles3D)
    kanamaScope.launch {
      miniBlasts.emitting = true
      SceneTree.delaySeconds(0.2)
      self.emitting = true
      SceneTree.delaySeconds(self.lifetime * 2.0)
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
