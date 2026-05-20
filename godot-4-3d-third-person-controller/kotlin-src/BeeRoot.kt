package thirdperson

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AnimationMixer
import net.multigesture.kanama.api.AnimationNodeStateMachinePlayback
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MeshInstance3D
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node3D")
class BeeRoot(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {

    private lateinit var stateMachine: AnimationNodeStateMachinePlayback

    @OnReady
    fun ready() {
        val animationTree = self.requireAs("AnimationTree", ::AnimationMixer)
        animationTree.setActive(true)
        stateMachine = animationTree.getStateMachinePlayback(STATE_MACHINE_PLAYBACK)
        playIdle()
    }

    @RegisterFunction("play_idle")
    fun playIdle() {
        stateMachine.travel(States.IDLE)
    }

    @RegisterFunction("play_spit_attack")
    fun playSpitAttack() {
        stateMachine.travel(States.SPIT_ATTACK)
    }

    @RegisterFunction("play_poweroff")
    fun playPoweroff() {
        stateMachine.travel(States.POWER_OFF)
    }

    @OnExitTree
    fun exitTree() {
        val mesh = self.getAsOrNull("bee_bot/Armature/Skeleton3D/bee_bot2", ::MeshInstance3D) ?: return
        mesh.setSurfaceOverrideMaterial(1, null)
        mesh.setSurfaceOverrideMaterial(2, null)
        mesh.setSurfaceOverrideMaterial(3, null)
    }

    companion object {
        private const val STATE_MACHINE_PLAYBACK = "parameters/StateMachine/playback"

        private object States {
            const val IDLE = "idle"
            const val SPIT_ATTACK = "spit_attack"
            const val POWER_OFF = "power_off"
        }
    }
}
