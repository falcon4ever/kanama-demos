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
import net.multigesture.kanama.api.NavigationAgent3D
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.PhysicsBody3D
import net.multigesture.kanama.api.RigidBody3D
import net.multigesture.kanama.api.SignalConnection
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.generated.SmokePuffNames
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "RigidBody3D")
class BeetleBot(godotObject: MemorySegment) : KanamaScript<RigidBody3D>(godotObject, ::RigidBody3D), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()

    @ScriptProperty
    var coinsCount: Long = 5

    @ScriptProperty
    var stoppingDistance: Double = 0.0

    private lateinit var reactionAnimationPlayer: AnimationPlayer
    private lateinit var detectionArea: Area3D
    private lateinit var beetleSkin: BeetlebotSkin
    private lateinit var navigationAgent: NavigationAgent3D
    private lateinit var deathCollisionShape: CollisionShape3D
    private lateinit var defeatSound: AudioStreamPlayer3D

    private var bodyEnteredConnection: SignalConnection? = null
    private var bodyExitedConnection: SignalConnection? = null
    private var target: Node3D? = null
    private var alive = true

    @OnReady
    fun ready() {
        reactionAnimationPlayer = self.requireAs("ReactionLabel/AnimationPlayer", ::AnimationPlayer)
        detectionArea = self.requireAs("PlayerDetectionArea", ::Area3D)
        beetleSkin = self.requireAs("BeetlebotSkin", ::Node3D).kotlinScriptInstance<BeetlebotSkin>()
            ?: error("BeetlebotSkin node is missing BeetlebotSkin script instance")
        navigationAgent = self.requireAs("NavigationAgent3D", ::NavigationAgent3D)
        deathCollisionShape = self.requireAs("DeathCollisionShape", ::CollisionShape3D)
        defeatSound = self.requireAs("DefeatSound", ::AudioStreamPlayer3D)

        bodyEnteredConnection = detectionArea.signal(Area3D.Signals.bodyEntered)
            .connectObject(self) { body -> onBodyEntered(Node3D(body.handle)) }
        bodyExitedConnection = detectionArea.signal(Area3D.Signals.bodyExited)
            .connectObject(self) { body -> onBodyExited(Node3D(body.handle)) }

        beetleSkin.idle()
    }

    @OnPhysicsProcess
    fun physicsProcess(delta: Double) {
        if (!alive) return

        val currentTarget = target ?: return
        beetleSkin.walk()

        val lookPosition = currentTarget.globalPosition.withY(self.globalPosition.y)
        if (self.globalPosition.distanceSquaredTo(lookPosition) > LOOK_AT_EPSILON) {
            self.lookAt(lookPosition)
        }

        navigationAgent.targetPosition = currentTarget.globalPosition
        val nextLocation = navigationAgent.getNextPathPosition()

        if (!navigationAgent.isTargetReached()) {
            val direction = (nextLocation - self.globalPosition).withY(0.0).normalized()
            val collision = self.moveAndCollide(direction * delta * SPEED)
            if (collision != null) {
                try {
                    val collider = collision.getCollider()
                    if (collider != null && Node3D(collider.handle).isPlayer()) {
                        val collider3d = Node3D(collider.handle)
                        val impactPoint = self.globalPosition - collider3d.globalPosition
                        val force = (-impactPoint).withY(0.5) * 10.0
                        collider.call("damage", impactPoint, force)
                        beetleSkin.attack()
                    }
                } finally {
                    collision.close()
                }
            }
        }
    }

    @RegisterFunction
    fun damage(impactPoint: Vector3, force: Vector3) {
        self.lockRotation = false
        self.applyImpulse(force.limitLength(3.0), impactPoint)

        if (!alive) {
            return
        }

        defeatSound.play()
        alive = false
        beetleSkin.powerOff()

        bodyEnteredConnection?.close()
        bodyExitedConnection?.close()
        target = null
        deathCollisionShape.setDeferred("disabled", false)

        self.setAxisLock(PhysicsBody3D.BODY_AXIS_ANGULAR_X, false)
        self.setAxisLock(PhysicsBody3D.BODY_AXIS_ANGULAR_Y, false)
        self.setAxisLock(PhysicsBody3D.BODY_AXIS_ANGULAR_Z, false)
        self.gravityScale = 1.0

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
        target = body
        reactionAnimationPlayer.play("found_player")
    }

    private fun onBodyExited(body: Node3D) {
        if (target?.handle?.address() != body.handle.address()) return
        target = null
        reactionAnimationPlayer.play("lost_player")
        beetleSkin.idle()
    }

    companion object {
        private const val SPEED = 3.0
        private const val LOOK_AT_EPSILON = 0.000001
    }
}
