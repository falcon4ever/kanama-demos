package tps

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AnimationMixer
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.CPUParticles3D
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.getCamera3d
import net.multigesture.kanama.api.globalTransform
import net.multigesture.kanama.api.isInsideTree
import net.multigesture.kanama.api.isQueuedForDeletion
import net.multigesture.kanama.api.lookAt
import net.multigesture.kanama.api.root
import net.multigesture.kanama.api.setProcess
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "Node3D")
class Blast(godotObject: GodotHandle) :
  KanamaScript<Node3D>(godotObject, ::Node3D), KanamaCoroutineOwner {
  override val kanamaScope = KanamaScope()

  private lateinit var lightRays: CPUParticles3D
  private lateinit var animationPlayer: AnimationPlayer
  private var camera: Camera3D? = null

  @OnReady
  fun ready() {
    lightRays = self.requireAs("LightRays", ::CPUParticles3D)
    animationPlayer = self.requireAs("AnimationPlayer", ::AnimationPlayer)
    camera = self.getTree().root.getCamera3d()
    kanamaScope.launch {
      animationPlayer
        .signal(AnimationMixer.Signals.animationFinished)
        .await(self, argumentCount = 1)
      self.queueFree()
    }
  }

  @OnProcess
  fun process(delta: Double) {
    val target = camera ?: return
    if (
      self.isQueuedForDeletion() ||
        !self.isInsideTree() ||
        target.isQueuedForDeletion() ||
        !target.isInsideTree()
    )
      return
    lightRays.lookAt(target.globalTransform.origin)
  }

  @OnExitTree
  fun exitTree() {
    kanamaScope.cancel()
    self.setProcess(false)
    camera = null
  }
}
