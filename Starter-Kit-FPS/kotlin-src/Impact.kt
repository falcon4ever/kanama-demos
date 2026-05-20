package fps

import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AnimatedSprite3D
import java.lang.foreign.MemorySegment
import net.multigesture.kanama.api.KanamaScript

@ScriptClass(attachTo = "AnimatedSprite3D")
class Impact(godotObject: MemorySegment) : KanamaScript<AnimatedSprite3D>(godotObject, ::AnimatedSprite3D) {

    @RegisterFunction("_on_animation_finished")
    fun onAnimationFinished() {
        self.queueFree()
    }
}
