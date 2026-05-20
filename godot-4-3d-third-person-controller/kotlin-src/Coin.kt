package thirdperson

import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.AudioStreamPlayer3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.PhysicsBody3D
import net.multigesture.kanama.api.RigidBody3D
import net.multigesture.kanama.api.SceneTree
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "RigidBody3D")
class Coin(godotObject: MemorySegment) : KanamaScript<RigidBody3D>(godotObject, ::RigidBody3D), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()

    private lateinit var collectAudio: AudioStreamPlayer3D
    private lateinit var playerDetectionArea: Area3D

    private var initialTweenPosition = Vector3.ZERO
    private var target: PhysicsBody3D? = null

    @OnReady
    fun ready() {
        collectAudio = self.requireAs("CollectAudio", ::AudioStreamPlayer3D)
        playerDetectionArea = self.requireAs("PlayerDetectionArea", ::Area3D)
    }

    @RegisterFunction
    fun spawn(coinDelay: Double = 0.5) {
        val randHeight = MIN_LAUNCH_HEIGHT + (GD.randf() * MAX_LAUNCH_HEIGHT)
        val randDir = Vector3.FORWARD.rotated(Vector3.UP, GD.randf() * 2.0 * Mathf.PI)
        val randDistance = MIN_LAUNCH_RANGE + (GD.randf() * MAX_LAUNCH_RANGE)
        val randPos = (randDir * randDistance).withY(randHeight)
        self.applyCentralImpulse(randPos)

        kanamaScope.launch {
            SceneTree.delaySeconds(coinDelay)
            self.setCollisionLayerValue(3, true)
        }

        playerDetectionArea.signal(Area3D.Signals.bodyEntered)
            .connectObject(self) { body ->
                onBodyEntered(PhysicsBody3D(body.handle))
            }
    }

    private fun setTarget(newTarget: PhysicsBody3D) {
        self.addCollisionExceptionWith(newTarget)

        if (target == null) {
            self.sleeping = true
            self.freeze = true

            initialTweenPosition = self.globalPosition
            target = newTarget
            val tween = self.createTween() ?: return
            tween.tweenMethod(self, "_follow", 0.0, 1.0, FOLLOW_TWEEN_DURATION)
            tween.tweenCallback(self, "_collect")
        }
    }

    @RegisterFunction("_follow")
    fun follow(offset: Double) {
        val currentTarget = target ?: return
        self.globalPosition = initialTweenPosition.lerp(currentTarget.globalPosition, offset)
    }

    private fun onBodyEntered(body: PhysicsBody3D) {
        if (!Node3D(body.handle).isPlayer()) return
        setTarget(body)
    }

    @RegisterFunction("_collect")
    fun collect() {
        collectAudio.setPitchScale(GD.randfn(1.0, 0.1))
        collectAudio.play()
        target?.let {
            it.kotlinScriptInstance<Player>()?.collectCoin()
                ?: error("Coin target is missing Player script instance")
        }
        self.hide()

        kanamaScope.launch {
            collectAudio.signal(AudioStreamPlayer3D.Signals.finished)
                .await(self, argumentCount = 0)
            self.queueFree()
        }
    }

    companion object {
        private const val MIN_LAUNCH_RANGE = 2.0
        private const val MAX_LAUNCH_RANGE = 4.0
        private const val MIN_LAUNCH_HEIGHT = 1.0
        private const val MAX_LAUNCH_HEIGHT = 3.0

        private const val FOLLOW_TWEEN_DURATION = 0.5
    }
}
