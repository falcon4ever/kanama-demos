package net.multigesture.kanama.demos.platformer3d

import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node3D

/** Web port of a drifting cloud: bobs slowly along Y. */
@ScriptClass(attachTo = "Node3D")
class Cloud(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {
  private var time = 0.0
  private var randomVelocity = 1.0
  private var randomTime = 1.0

  @OnReady
  fun ready() {
    randomVelocity = GD.randfRange(0.1, 2.0)
    randomTime = GD.randfRange(0.1, 2.0)
  }

  @OnProcess
  fun process(delta: Double) {
    val pos = self.position
    self.position = pos.withY(pos.y + Mathf.cos(time * randomTime) * randomVelocity * delta)
    time += delta
  }
}
