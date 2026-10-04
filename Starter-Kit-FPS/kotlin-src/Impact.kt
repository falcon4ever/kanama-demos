package fps

import net.multigesture.kanama.annotations.GodotName
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AnimatedSprite3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript

@ScriptClass(attachTo = "AnimatedSprite3D")
class Impact(godotObject: GodotHandle) :
  KanamaScript<AnimatedSprite3D>(godotObject, ::AnimatedSprite3D) {
  @GodotName("_on_animation_finished")
  fun onAnimationFinished() {
    self.queueFree()
  }
}
