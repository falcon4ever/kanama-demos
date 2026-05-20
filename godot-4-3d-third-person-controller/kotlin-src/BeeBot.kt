package thirdperson

import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.AudioStreamPlayer3D
import net.multigesture.kanama.api.CollisionShape3D
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.RigidBody3D
import net.multigesture.kanama.api.SignalConnection
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.generated.SmokePuffNames
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "RigidBody3D")
class BeeBot(godotObject: MemorySegment) : KanamaScript<RigidBody3D>(godotObject, ::RigidBody3D), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()

    @ScriptProperty
    var shootTimer: Double = 1.5

    @ScriptProperty
    var bulletSpeed: Double = 6.0

    @ScriptProperty
    var coinsCount: Long = 5

    private lateinit var reactionAnimationPlayer: AnimationPlayer
    private lateinit var flyingAnimationPlayer: AnimationPlayer
    private lateinit var detectionArea: Area3D
    private lateinit var deathMeshCollider: CollisionShape3D
    private lateinit var beeRoot: BeeRoot
    private lateinit var defeatSound: AudioStreamPlayer3D

    private var bodyEnteredConnection: SignalConnection? = null
    private var bodyExitedConnection: SignalConnection? = null
    private var shootCount = 0.0
    private var target: Node3D? = null
    private var alive = true

    @OnReady
    fun ready() {
        reactionAnimationPlayer = self.requireAs("ReactionLabel/AnimationPlayer", ::AnimationPlayer)
        flyingAnimationPlayer = self.requireAs("MeshRoot/AnimationPlayer", ::AnimationPlayer)
        detectionArea = self.requireAs("PlayerDetectionArea", ::Area3D)
        deathMeshCollider = self.requireAs("DeathMeshCollider", ::CollisionShape3D)
        beeRoot = self.requireAs("MeshRoot/bee_root", ::Node3D).kotlinScriptInstance<BeeRoot>()
            ?: error("MeshRoot/bee_root is missing BeeRoot script instance")
        defeatSound = self.requireAs("DefeatSound", ::AudioStreamPlayer3D)

        bodyEnteredConnection = detectionArea.signal(Area3D.Signals.bodyEntered)
            .connectObject(self) { body -> onBodyEntered(Node3D(body.handle)) }
        bodyExitedConnection = detectionArea.signal(Area3D.Signals.bodyExited)
            .connectObject(self) { body -> onBodyExited(Node3D(body.handle)) }

        beeRoot.playIdle()
    }

    @OnPhysicsProcess
    fun physicsProcess(delta: Double) {
        val currentTarget = target
        if (currentTarget != null && alive) {
            if (self.globalPosition.distanceSquaredTo(currentTarget.globalPosition) > LOOK_AT_EPSILON) {
                val targetTransform = self.transform.lookingAt(currentTarget.globalPosition)
                self.transform = self.transform.interpolateWith(targetTransform, 0.1)
            }

            shootCount += delta
            if (shootCount > shootTimer) {
                beeRoot.playSpitAttack()
                shootCount -= shootTimer

                val origin = self.globalPosition
                val targetPosition = currentTarget.globalPosition + Vector3.UP
                val aimDirection = (targetPosition - self.globalPosition).normalized()
                DemoScenes.launchBullet(self.getParent(), self, origin, aimDirection * bulletSpeed, 14.0)
            }
        }
    }

    @RegisterFunction
    fun damage(impactPoint: Vector3, force: Vector3) {
        self.applyImpulse(force.limitLength(3.0), impactPoint)

        if (!alive) {
            return
        }

        defeatSound.play()
        alive = false

        flyingAnimationPlayer.stop()
        flyingAnimationPlayer.seek(0.0, update = true)
        bodyEnteredConnection?.close()
        bodyExitedConnection?.close()
        target = null
        deathMeshCollider.setDeferred("disabled", false)

        self.gravityScale = 1.0
        beeRoot.playPoweroff()

        kanamaScope.launch {
            self.getTree().delaySeconds(2.0)

            val puff = DemoScenes.instantiate(DemoScenes.SMOKE_PUFF)
            if (puff != null) {
                self.getParent()?.addChild(puff)
                Node3D(puff.handle).globalPosition = self.globalPosition
                puff.signal(SmokePuffNames.Signals.full).await(self, argumentCount = 0)
            }

            repeat(coinsCount.toInt()) {
                val coinNode = DemoScenes.instantiate(DemoScenes.COIN) ?: return@repeat
                val coin = coinNode.kotlinScriptInstance<Coin>()
                    ?: error("Coin scene is missing Coin script instance")
                self.getParent()?.addChild(coinNode)
                Node3D(coinNode.handle).globalPosition = self.globalPosition
                coin.spawn()
            }
            self.queueFree()
        }
    }

    private fun onBodyEntered(body: Node3D) {
        if (!body.isPlayer()) return
        shootCount = 0.0
        target = body
        reactionAnimationPlayer.play("found_player")
    }

    private fun onBodyExited(body: Node3D) {
        if (!body.isPlayer()) return
        if (target?.handle?.address() == body.handle.address()) {
            target = null
            reactionAnimationPlayer.play("lost_player")
        }
    }

    companion object {
        private const val LOOK_AT_EPSILON = 0.000001
    }
}
