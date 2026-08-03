package net.multigesture.kanama.demos.platformer3d

import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.GPUParticles3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.kotlinScriptInstance

/**
 * Web port of a collectible coin: spins + bobs, and on the scene body_entered signal awards the
 * coin to the Player script (cross-script call via the script-handle body arg), plays the pickup
 * sound through the native GDScript Audio autoload, frees the mesh, and stops the sparkle.
 */
@ScriptClass(attachTo = "Area3D")
class Coin(godotObject: GodotHandle) : KanamaScript<Area3D>(godotObject, ::Area3D) {
  private var time = 0.0
  private var grabbed = false
  private lateinit var mesh: Node3D
  private lateinit var particles: GPUParticles3D
  private lateinit var audio: Node

  @OnReady
  fun ready() {
    mesh = self.requireAs("Mesh", ::Node3D)
    particles = self.requireAs("Particles", ::GPUParticles3D)
    audio = self.getNodeOrNull("/root/Audio")?.let { Node(it.handle) }
      ?: error("Coin requires the Audio autoload")
  }

  @RegisterFunction("_on_body_entered")
  fun onBodyEntered(body: Node3D) {
    if (grabbed) return
    val player = body.kotlinScriptInstance<Player>() ?: return
    player.collectCoin()

    audio.call("play", "res://sounds/coin.ogg")
    mesh.queueFree()
    particles.setEmitting(false)
    grabbed = true
  }

  @OnProcess
  fun process(delta: Double) {
    val rot = self.rotation
    self.rotation = rot.withY(rot.y + 2.0 * delta)

    val pos = self.position
    self.position = pos.withY(pos.y + Mathf.cos(time * 5.0) * delta)

    time += delta
  }
}
