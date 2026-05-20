package thirdperson

import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.AnimationMixer
import net.multigesture.kanama.api.AnimationNodeStateMachinePlayback
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.generated.CharacterSkinSignals
import java.lang.foreign.MemorySegment

@GlobalClass
@ScriptClass(attachTo = "Node3D")
class CharacterSkin(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {

    @ScriptProperty
    var mainAnimationPlayer: AnimationPlayer? = null

    private lateinit var animationTree: AnimationMixer
    private lateinit var stateMachine: AnimationNodeStateMachinePlayback

    private var moving = false
    private var moveSpeed = 0.0

    @OnReady
    fun ready() {
        animationTree = self.requireAs("AnimationTree", ::AnimationMixer)
        animationTree.setActive(true)
        mainAnimationPlayer?.setDefaultBlendTime(0.1)
        stateMachine = animationTree.getStateMachinePlayback(STATE_MACHINE_PLAYBACK)
    }

    @RegisterFunction
    fun setMoving(value: Boolean) {
        moving = value
        stateMachine.travel(if (moving) States.MOVE else States.IDLE)
    }

    @RegisterFunction
    fun setMovingSpeed(value: Double) {
        moveSpeed = value.coerceIn(0.0, 1.0)
        animationTree.setParameter(MOVING_BLEND_PATH, moveSpeed)
    }

    @RegisterFunction
    fun jump() {
        stateMachine.travel(States.JUMP)
    }

    @RegisterFunction
    fun fall() {
        stateMachine.travel(States.FALL)
    }

    @RegisterFunction
    fun punch() {
        animationTree.setParameter(PUNCH_REQUEST_PATH, ONE_SHOT_REQUEST_FIRE)
    }

    @RegisterFunction("_step")
    fun step() {
        CharacterSkinSignals.stepped(this)
    }

    @Signal
    fun stepped() = Unit

    companion object {
        private const val STATE_MACHINE_PLAYBACK = "parameters/StateMachine/playback"
        private const val MOVING_BLEND_PATH = "parameters/StateMachine/move/blend_position"
        private const val PUNCH_REQUEST_PATH = "parameters/PunchOneShot/request"
        private const val ONE_SHOT_REQUEST_FIRE = 1L

        private object States {
            const val IDLE = "idle"
            const val MOVE = "move"
            const val JUMP = "jump"
            const val FALL = "fall"
        }
    }
}
