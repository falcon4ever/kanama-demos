package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.AudioStreamPlayer3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.generated.SmokePuffSignals
import net.multigesture.kanama.generated.full

@ScriptClass(attachTo = "Node3D")
class SmokePuff(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {

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
        launch {
            animationPlayer.animationFinished.await()
            self.queueFree()
        }
    }

    fun smokeAtFullDensity() {
        full.emit()
    }
}
