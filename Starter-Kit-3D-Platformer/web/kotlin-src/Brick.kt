package net.multigesture.kanama.demos.platformer3d

import kotlinx.coroutines.launch
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.CollisionShape3D
import net.multigesture.kanama.api.GPUParticles3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.SceneTree
import net.multigesture.kanama.api.StaticBody3D

/**
 * Web port of a breakable brick: hitting it from below (BottomDetector body_entered, connected
 * programmatically like desktop) explodes it — break sound, particle burst, mesh hidden,
 * collision disabled, monitoring deferred off, freed after a one-second delay.
 */
@ScriptClass(attachTo = "StaticBody3D")
class Brick(godotObject: GodotHandle) :
  KanamaScript<StaticBody3D>(godotObject, ::StaticBody3D), KanamaCoroutineOwner {
  override val kanamaScope = KanamaScope()

  private var exploded = false

  private lateinit var bottomDetector: Area3D
  private lateinit var mesh: Node3D
  private lateinit var particles: GPUParticles3D
  private lateinit var collisionShape: CollisionShape3D
  private lateinit var audio: Node

  @OnReady
  fun ready() {
    bottomDetector = self.requireAs("BottomDetector", ::Area3D)
    mesh = self.requireAs("Mesh", ::Node3D)
    particles = self.requireAs("Particles", ::GPUParticles3D)
    collisionShape = self.requireAs("CollisionShape3D", ::CollisionShape3D)
    audio = self.getNodeOrNull("/root/Audio")?.let { Node(it.handle) }
      ?: error("Brick requires the Audio autoload")

    bottomDetector.signal("body_entered").connect(self, "_on_bottom_hit")
  }

  @RegisterFunction("_on_bottom_hit")
  fun onBottomHit(body: Node3D) {
    if (body.isInGroup("player")) explode()
  }

  private fun explode() {
    if (exploded) return
    exploded = true

    audio.call("play", "res://sounds/break.ogg")

    particles.restart(keepSeed = true)
    mesh.hide()
    collisionShape.setDisabled(true)
    bottomDetector.setDeferred("monitoring", false)

    kanamaScope.launch {
      SceneTree.delaySeconds(1.0)
      self.queueFree()
    }
  }
}
