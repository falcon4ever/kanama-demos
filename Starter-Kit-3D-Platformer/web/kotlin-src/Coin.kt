package net.multigesture.kanama.demos.platformer3d

import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D

/** Web port of a collectible coin: spins, and frees itself when a body overlaps (scene body_entered). */
@ScriptClass(attachTo = "Area3D")
class Coin(godotObject: GodotHandle) : KanamaScript<Area3D>(godotObject, ::Area3D) {
  private var grabbed = false
  private var angle = 0.0

  @OnProcess
  fun process(delta: Double) {
    angle += delta * 2.0
    self.rotation = self.rotation.withY(angle)
  }

  @RegisterFunction("_on_body_entered")
  fun onBodyEntered(body: Node3D) {
    if (grabbed) return
    grabbed = true
    self.queueFree()
  }
}
