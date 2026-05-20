package thirdperson

import net.multigesture.kanama.annotations.Export
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Animation
import net.multigesture.kanama.api.AnimationMixer
import net.multigesture.kanama.api.AnimationNodeStateMachinePlayback
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.Timer
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node3D")
class BeetlebotSkin(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {

    @Export(name = "_force_loop")
    var forceLoop: List<String> = emptyList()

    private lateinit var animationTree: AnimationMixer
    private lateinit var mainStateMachine: AnimationNodeStateMachinePlayback
    private lateinit var secondaryActionTimer: Timer

    @OnReady
    fun ready() {
        animationTree = self.requireAs("AnimationTree", ::AnimationMixer)
        animationTree.setActive(true)
        mainStateMachine = animationTree.getStateMachinePlayback(STATE_MACHINE_PLAYBACK)
        secondaryActionTimer = self.requireAs("SecondaryActionTimer", ::Timer)

        val animationPlayer = self.requireAs("beetle_bot/AnimationPlayer", ::AnimationPlayer)
        for (animationName in forceLoop) {
            animationPlayer.getAnimation(animationName)?.setLoopMode(Animation.LOOP_LINEAR)
        }
    }

    @RegisterFunction("_on_secondary_action_timer_timeout")
    fun onSecondaryActionTimerTimeout() {
        if (mainStateMachine.getCurrentNode() == States.IDLE) {
            shake()
        }
        secondaryActionTimer.start(GD.randfRange(3.0, 8.0))
    }

    @RegisterFunction
    fun idle() {
        mainStateMachine.travel(States.IDLE)
    }

    @RegisterFunction
    fun walk() {
        mainStateMachine.travel(States.WALK)
    }

    @RegisterFunction
    fun shake() {
        mainStateMachine.travel(States.SHAKE)
    }

    @RegisterFunction
    fun attack() {
        mainStateMachine.travel(States.ATTACK)
    }

    @RegisterFunction("power_off")
    fun powerOff() {
        mainStateMachine.travel(States.POWER_OFF)
        secondaryActionTimer.stop()
    }

    companion object {
        private const val STATE_MACHINE_PLAYBACK = "parameters/StateMachine/playback"

        private object States {
            const val IDLE = "Idle"
            const val WALK = "Walk"
            const val SHAKE = "Shake"
            const val ATTACK = "Attack"
            const val POWER_OFF = "PowerOff"
        }
    }
}
