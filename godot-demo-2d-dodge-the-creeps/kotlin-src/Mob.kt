package dodge

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AnimatedSprite2D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.RigidBody2D
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "RigidBody2D")
class Mob(godotObject: MemorySegment) : KanamaScript<RigidBody2D>(godotObject, ::RigidBody2D) {

    @OnReady
    fun ready() {
        val animatedSprite = self.requireAs("AnimatedSprite2D", ::AnimatedSprite2D)
        val mobTypes = animatedSprite.getSpriteFrames()
            ?.getAnimationNames()
            ?: error("Mob AnimatedSprite2D has no SpriteFrames")
        check(mobTypes.isNotEmpty()) { "Mob AnimatedSprite2D has no animations" }
        animatedSprite.animation = mobTypes[(GD.randi() % mobTypes.size.toLong()).toInt()]
        animatedSprite.play()
    }

    @RegisterFunction("_on_VisibilityNotifier2D_screen_exited")
    fun onVisibilityNotifier2DScreenExited() {
        self.queueFree()
    }
}
