package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.AnimationMixer
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.AudioStreamPlayer3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.generated.SmokePuffSignals
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "Node3D")
class SmokePuff(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()

    @Signal
    fun full() = Unit

    @OnReady
    fun ready() {
        val smokeSounds = self.requireAs("SmokeSounds", ::Node).getChildren()
        if (smokeSounds.isNotEmpty()) {
            val index = GD.randiRange(0, smokeSounds.lastIndex.toLong()).toInt()
            AudioStreamPlayer3D(smokeSounds[index].handle).play()
        }

        val animationPlayer = self.requireAs("AnimationPlayer", ::AnimationPlayer)
        animationPlayer.play("poof")
        kanamaScope.launch {
            animationPlayer.signal(AnimationMixer.Signals.animationFinished)
                .await(self, argumentCount = 1)
            self.queueFree()
        }
    }

    @RegisterFunction("smoke_at_full_density")
    fun smokeAtFullDensity() {
        SmokePuffSignals.full(this)
    }
}
