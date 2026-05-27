package tps

import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.Rpc
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.CollisionShape3D
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
import net.multigesture.kanama.api.ShaderMaterial
import net.multigesture.kanama.api.Timer
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "RigidBody3D")
class Part(godotObject: MemorySegment) : KanamaScript<RigidBody3D>(godotObject, ::RigidBody3D), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()

    @ScriptProperty
    var lifetime = 3.0

    @ScriptProperty(name = "lifetime_random")
    var lifetimeRandom = 3.0

    @ScriptProperty(name = "disappearing_time")
    var disappearingTime = 0.5

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
            val duplicated = Material.fromResource(mesh?.mesh?.surfaceGetMaterial(0)?.duplicate())
            material = duplicated
            if (duplicated != null) {
                mesh?.mesh?.surfaceSetMaterial(0, duplicated)
                val nextPassResource = duplicated.nextPass?.duplicate()
                duplicated.nextPass = Material.fromResource(nextPassResource)
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
            (Vector3(net.multigesture.kanama.api.GD.randf(), net.multigesture.kanama.api.GD.randf(), net.multigesture.kanama.api.GD.randf()).normalized() * 2.0 - Vector3.ONE) * 10.0
        kanamaScope.launch {
            val delay = if (System.getenv("KANAMA_TPS_SMOKE_FAST_PARTS") == "1") {
                0.1
            } else {
                lifetime + lifetimeRandom * net.multigesture.kanama.api.GD.randf()
            }
            self.getTree().createTimer(delay)
                ?.signal(Timer.Signals.timeout)?.await(self, argumentCount = 0)
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
    @Rpc(callLocal = true)
    fun destroy() {
        if (destroying || self.isQueuedForDeletion() || !self.isInsideTree()) return
        destroying = true
        disableCollision()
        self.setProcess(false)
        self.freeze = true
        self.linearVelocity = Vector3.ZERO
        self.angularVelocity = Vector3.ZERO
        meshInstance?.mesh?.surfaceSetMaterial(0, null)
        meshInstance = null
        self.hide()
        material = null
        fadeMaterial = null
        if (System.getenv("KANAMA_TPS_SMOKE_QUIT_AFTER_PARTS_DESTROYED") == "1") {
            net.multigesture.kanama.api.GD.print("TPS smoke part destroyed")
            kanamaScope.launch {
                val delay = System.getenv("KANAMA_TPS_SMOKE_QUIT_AFTER_PARTS_DESTROYED_DELAY")?.toDoubleOrNull() ?: 4.0
                self.getTree().createTimer(delay)?.signal(Timer.Signals.timeout)?.await(self, argumentCount = 0)
                self.getTree().quit()
            }
        }
    }

    @OnExitTree
    fun exitTree() {
        kanamaScope.cancel()
        self.setProcess(false)
        disableCollision()
        meshInstance = null
        fadeMaterial = null
        material = null
    }

    private fun disableCollision() {
        self.collisionLayer = 0
        self.collisionMask = 0
        if (::col1.isInitialized) col1.disabled = true
        if (::col2.isInitialized) col2.disabled = true
    }
}
