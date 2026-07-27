package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.TextureRect
import net.multigesture.kanama.types.Color

@ScriptClass(attachTo = "TextureRect")
class Icone(godotObject: GodotHandle) : KanamaScript<TextureRect>(godotObject, ::TextureRect) {

  private var disabledAlpha = 0.2f

  @OnReady
  fun ready() {
    self.modulate = Color(1f, 1f, 1f, disabledAlpha)
  }

  @RegisterFunction
  fun setState(state: Boolean) {
    val disabled = Color(1f, 1f, 1f, disabledAlpha)
    val enabled = Color(1f, 1f, 1f, 1f)
    val target = if (state) enabled else disabled
    val tween = self.createTween() ?: return
    tween.tweenProperty(self, "modulate", target, 0.2)
  }
}
