package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.TextureRect
import net.multigesture.kanama.api.createTween
import net.multigesture.kanama.types.Color

@ScriptClass(attachTo = "TextureRect")
class Icone(godotObject: GodotHandle) : KanamaScript<TextureRect>(godotObject, ::TextureRect) {

    private var disabledAlpha = 0.2

    @OnReady
    fun ready() {
        self.modulate = Color(1.0, 1.0, 1.0, disabledAlpha)
    }

    fun setState(state: Boolean) {
        val disabled = Color(1.0, 1.0, 1.0, disabledAlpha)
        val enabled = Color(1.0, 1.0, 1.0, 1.0)
        val target = if (state) enabled else disabled
        val source = if (state) disabled else enabled
        val tween = self.createTween()
        val tweener = tween.tweenProperty(self, "modulate", target, 0.2)
        tweener.from(source)
    }
}
