package net.multigesture.kanama.demos.match3

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GPUParticles2D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.SceneTree

@ScriptClass(attachTo = "GPUParticles2D")
class Particles(godotObject: GodotHandle) :
  KanamaScript<GPUParticles2D>(godotObject, ::GPUParticles2D) {

  // Functions
  @OnReady
  fun ready() {
    self.emitting = true
    launch {
      SceneTree.delaySeconds(self.lifetime)
      self.queueFree()
    }
  }

  @OnExitTree
  fun exitTree() {
    cancelCoroutines()
  }
}
