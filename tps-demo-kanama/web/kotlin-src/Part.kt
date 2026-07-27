package tps

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.CollisionShape3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Material
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.MeshInstance3D
import net.multigesture.kanama.api.MultiplayerSynchronizer
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.api.RigidBody3D
import net.multigesture.kanama.api.SceneTree
import net.multigesture.kanama.api.ShaderMaterial
import net.multigesture.kanama.api.getMesh
import net.multigesture.kanama.api.getMultiplayer
import net.multigesture.kanama.api.isInsideTree
import net.multigesture.kanama.api.isQueuedForDeletion
import net.multigesture.kanama.api.linearVelocity
import net.multigesture.kanama.api.setProcess
import net.multigesture.kanama.api.surfaceGetMaterial
import net.multigesture.kanama.api.surfaceSetMaterial
import net.multigesture.kanama.api.collisionLayer
import net.multigesture.kanama.api.collisionMask
import net.multigesture.kanama.api.disabled
import net.multigesture.kanama.api.getChild
import net.multigesture.kanama.types.Vector3
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "RigidBody3D")
class Part(godotObject: GodotHandle) :
  KanamaScript<RigidBody3D>(godotObject, ::RigidBody3D), KanamaCoroutineOwner {
  override val kanamaScope = KanamaScope()

  @ScriptProperty var lifetime = 3.0

  @ScriptProperty(name = "lifetime_random") var lifetimeRandom = 3.0

  @ScriptProperty(name = "disappearing_time") var disappearingTime = 0.5

  @ScriptProperty(name = "fade_value")
  var fadeValue = 0.0
    set(value) {
      field = value
      fadeMaterial?.setShaderParameter("emission_cutout", value)
    }

  private var material: Material? = null
  private var fadeMaterial: ShaderMaterial? = null
  private var meshInstance: MeshInstance3D? = null
  private var disappearingCounter = 0.0
  private var exploded = false
  private var destroying = false
  private lateinit var multiplayerSynchronizer: MultiplayerSynchronizer
  private lateinit var col1: CollisionShape3D
  private lateinit var col2: CollisionShape3D

  @OnReady
  fun ready() {
    self.setProcess(false)
    multiplayerSynchronizer = self.requireAs("MultiplayerSynchronizer", ::MultiplayerSynchronizer)
    col1 = self.requireAs("Col1", ::CollisionShape3D)
    col2 = self.requireAs("Col2", ::CollisionShape3D)
    if (!OS.hasFeature("dedicated_server")) {
      val mesh = self.requireAs("Model", ::Node3D).getChild(0)?.let { MeshInstance3D(it.handle) }
      meshInstance = mesh
      val duplicated = mesh?.getMesh()?.surfaceGetMaterial(0)?.duplicate()
      material = duplicated
      if (duplicated != null) {
        mesh.getMesh()?.surfaceSetMaterial(0, duplicated)
        val nextPassResource = duplicated.nextPass?.duplicate()
        duplicated.nextPass = nextPassResource
        if (nextPassResource != null) {
          fadeMaterial = ShaderMaterial.fromResource(nextPassResource)
        }
      }
    }
  }

  @RegisterFunction
  fun explode() {
    if (exploded || self.isQueuedForDeletion() || !self.isInsideTree()) return
    exploded = true
    if (self.getMultiplayer()?.isServer() != true) return
    multiplayerSynchronizer.publicVisibility = true
    self.freeze = false
    col1.disabled = false
    col2.disabled = false
    self.linearVelocity = Vector3.UP * 3.0
    self.angularVelocity =
      (Vector3(GD.randf(), GD.randf(), GD.randf()).normalized() * 2.0 - Vector3.ONE) * 10.0
    kanamaScope.launch {
      SceneTree.delaySeconds(lifetime + lifetimeRandom * GD.randf())
      if (!self.isQueuedForDeletion() && self.isInsideTree()) {
        self.setProcess(true)
      }
    }
  }

  @OnProcess
  fun process(delta: Double) {
    fadeValue = Mathf.pow(disappearingCounter / disappearingTime, 2.0)
    disappearingCounter += delta
    if (disappearingCounter >= disappearingTime - 0.2) {
      self.setProcess(false)
      destroy()
    }
  }

  @RegisterFunction
  fun destroy() {
    if (destroying || self.isQueuedForDeletion() || !self.isInsideTree()) return
    destroying = true
    disableCollision()
    self.setProcess(false)
    self.freeze = true
    self.linearVelocity = Vector3.ZERO
    self.angularVelocity = Vector3.ZERO
    self.hide()
    // Web adaptation: the desktop clears the surface override to drop its reference to the
    // duplicated chain. Here the duplicates are tracked Kotlin-side handles, so releasing them
    // is both the reference drop and the handle-count drain.
    releaseDuplicatedMaterials()
    meshInstance = null
  }

  @OnExitTree
  fun exitTree() {
    kanamaScope.cancel()
    self.setProcess(false)
    disableCollision()
    releaseDuplicatedMaterials()
    meshInstance = null
  }

  private fun releaseDuplicatedMaterials() {
    fadeMaterial?.close()
    fadeMaterial = null
    material?.close()
    material = null
  }

  private fun disableCollision() {
    self.collisionLayer = 0
    self.collisionMask = 0
    if (::col1.isInitialized) col1.disabled = true
    if (::col2.isInitialized) col2.disabled = true
  }
}
