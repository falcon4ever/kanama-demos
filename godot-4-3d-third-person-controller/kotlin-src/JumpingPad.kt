package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.Tween
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Area3D")
class JumpingPad(godotObject: MemorySegment) : KanamaScript<Area3D>(godotObject, ::Area3D) {

    @ScriptProperty
    var impulseStrength: Double = 10.0

    private lateinit var mushroom: Node3D

    @OnReady
    fun ready() {
        mushroom = self.requireAs("%mushroom", ::Node3D)
        self.signal(Area3D.Signals.bodyEntered).connectObject(self) { body ->
            if (!Node3D(body.handle).isPlayer()) return@connectObject
            launch(CharacterBody3D(body.handle))
        }
    }

    private fun launch(body: CharacterBody3D) {
        body.velocity = Vector3.UP * PLAYER_JUMP_INITIAL_IMPULSE + self.transform.basis * Vector3.UP * impulseStrength

        mushroom.scale = mushroom.scale.withY(0.4)
        val tween = self.createTween() ?: return
        val tweener = tween.tweenProperty(mushroom, "scale:y", 1.0, 1.0)
        tweener?.setEase(Tween.EASE_OUT)?.setTrans(Tween.TRANS_ELASTIC)
    }

    companion object {
        private const val PLAYER_JUMP_INITIAL_IMPULSE = 12.0
    }
}
