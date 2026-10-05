package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Export
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.Tween
import net.multigesture.kanama.api.createTween
import net.multigesture.kanama.types.Vector3

@ScriptClass(attachTo = "Area3D")
class JumpingPad(godotObject: GodotHandle) : KanamaScript<Area3D>(godotObject, ::Area3D) {

    @Export
    var impulseStrength: Double = 10.0

    private lateinit var mushroom: Node3D

    @OnReady
    fun ready() {
        mushroom = self.requireAs("%mushroom", ::Node3D)
        self.bodyEntered.connect { body ->
            if (!body.isPlayer()) return@connect
            launch(CharacterBody3D(body.handle))
        }
    }

    private fun launch(body: CharacterBody3D) {
        body.velocity = Vector3.UP * PLAYER_JUMP_INITIAL_IMPULSE + self.transform.basis * Vector3.UP * impulseStrength

        mushroom.scale = mushroom.scale.withY(0.4)
        val tween = self.createTween()
        val tweener = tween.tweenProperty(mushroom, "scale:y", 1.0, 1.0)
        tweener.setEase(Tween.EaseType.OUT).setTrans(Tween.TransitionType.ELASTIC)
    }

    companion object {
        private const val PLAYER_JUMP_INITIAL_IMPULSE = 12.0
    }
}
