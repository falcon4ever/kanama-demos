package tps

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.CollisionShape3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.api.OmniLight3D
import net.multigesture.kanama.api.getMultiplayer
import net.multigesture.kanama.api.isInsideTree
import net.multigesture.kanama.api.isQueuedForDeletion
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.api.moveAndCollide
import net.multigesture.kanama.api.postNextFrame
import net.multigesture.kanama.api.setPhysicsProcess
import net.multigesture.kanama.api.shadowEnabled
import net.multigesture.kanama.api.transform
import net.multigesture.kanama.api.disabled

@ScriptClass(attachTo = "CharacterBody3D")
class Bullet(godotObject: GodotHandle) :
  KanamaScript<CharacterBody3D>(godotObject, ::CharacterBody3D) {
  private var timeAlive = 5.0
  private var hit = false
  private var exploded = false
  private lateinit var animationPlayer: AnimationPlayer
  private lateinit var collisionShape: CollisionShape3D
  private lateinit var omniLight: OmniLight3D

  @OnReady
  fun ready() {
    animationPlayer = self.requireAs("AnimationPlayer", ::AnimationPlayer)
    collisionShape = self.requireAs("CollisionShape3D", ::CollisionShape3D)
    omniLight = self.requireAs("OmniLight3D", ::OmniLight3D)
    if (self.getMultiplayer()?.isServer() != true) {
      self.setPhysicsProcess(false)
      collisionShape.disabled = true
    }
  }

  @OnPhysicsProcess
  fun physicsProcess(delta: Double) {
    if (hit) return
    timeAlive -= delta
    if (timeAlive < 0.0) {
      hit = true
      explode()
    }
    val displacement = -(self.transform.basis.z * (delta * BULLET_VELOCITY))
    val collision = self.moveAndCollide(displacement)
    if (collision != null) {
      hit = true
      val collider = collision.getCollider()
      val node = collider?.asNode3DOrNull()
      if (node != null) {
        MainThread.postNextFrame {
          if (node.isQueuedForDeletion() || !node.isInsideTree()) return@postNextFrame
          node.kotlinScriptInstance<RedRobot>()?.hit() ?: node.kotlinScriptInstance<Player>()?.hit()
        }
      }
      collisionShape.disabled = true
      explode()
    }
    collision?.close()
  }

  @RegisterFunction
  fun explode() {
    if (exploded) return
    exploded = true
    hit = true
    self.setPhysicsProcess(false)
    collisionShape.disabled = true
    animationPlayer.play("explode")
    if (TpsSettings.renderBool("shadow_mapping")) {
      omniLight.shadowEnabled = true
    }
  }

  @RegisterFunction
  fun destroy() {
    if (self.getMultiplayer()?.isServer() == true) {
      self.queueFree()
    }
  }

  @OnExitTree
  fun exitTree() {
    hit = true
    exploded = true
    self.setPhysicsProcess(false)
  }

  private companion object {
    const val BULLET_VELOCITY = 20.0
  }
}
