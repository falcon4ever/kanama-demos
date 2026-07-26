package charactercontroller

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.AnimationMixer
import net.multigesture.kanama.api.AnimationNodeStateMachinePlayback
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.Timer

/**
 * Web port of the Sophia skin driver. Adaptation: the eye-blink material swap
 * (surface-override material + UV offset) is not ported — the blink timers still cycle so the
 * cadence is preserved if the material families land later, but eyes stay open on Web.
 */
@ScriptClass(attachTo = "Node3D")
class SophiaSkin(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {

  @ScriptProperty var blink = true

  private lateinit var animationTree: AnimationMixer
  private lateinit var stateMachine: AnimationNodeStateMachinePlayback
  private lateinit var blinkTimer: Timer
  private lateinit var closedEyesTimer: Timer
  private var runTilt = 0.0

  @OnReady
  fun ready() {
    animationTree = self.requireAs("AnimationTree", ::AnimationMixer)
    stateMachine = animationTree.getStateMachinePlayback(STATE_MACHINE_PLAYBACK)
    blinkTimer = self.requireAs("BlinkTimer", ::Timer)
    closedEyesTimer = self.requireAs("ClosedEyesTimer", ::Timer)

    blinkTimer.signal(Timer.Signals.timeout).connect(self, argumentCount = 0) {
      closedEyesTimer.start(0.2)
    }
    closedEyesTimer.signal(Timer.Signals.timeout).connect(self, argumentCount = 0) {
      blinkTimer.start(GD.randfRange(1.0, 4.0))
    }

    if (!blink) {
      blinkTimer.stop()
      closedEyesTimer.stop()
    }
  }

  @RegisterFunction("set_blink")
  fun applyBlink(state: Boolean) {
    if (blink == state) return
    blink = state
    if (blink) {
      blinkTimer.start(0.2)
    } else {
      blinkTimer.stop()
      closedEyesTimer.stop()
    }
  }

  @RegisterFunction("_set_run_tilt")
  fun setRunTilt(value: Double) {
    runTilt = value.coerceIn(-1.0, 1.0)
    animationTree.setParameter(MOVE_TILT_PATH, runTilt)
  }

  @RegisterFunction
  fun idle() {
    stateMachine.travel(States.IDLE)
  }

  @RegisterFunction
  fun move() {
    stateMachine.travel(States.MOVE)
  }

  @RegisterFunction
  fun fall() {
    stateMachine.travel(States.FALL)
  }

  @RegisterFunction
  fun jump() {
    stateMachine.travel(States.JUMP)
  }

  @RegisterFunction("edge_grab")
  fun edgeGrab() {
    stateMachine.travel(States.EDGE_GRAB)
  }

  @RegisterFunction("wall_slide")
  fun wallSlide() {
    stateMachine.travel(States.WALL_SLIDE)
  }

  companion object {
    private const val STATE_MACHINE_PLAYBACK = "parameters/StateMachine/playback"
    private const val MOVE_TILT_PATH = "parameters/StateMachine/Move/tilt/add_amount"

    private object States {
      const val IDLE = "Idle"
      const val MOVE = "Move"
      const val FALL = "Fall"
      const val JUMP = "Jump"
      const val EDGE_GRAB = "EdgeGrab"
      const val WALL_SLIDE = "WallSlide"
    }
  }
}
