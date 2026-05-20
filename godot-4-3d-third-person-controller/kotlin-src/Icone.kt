package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.TextureRect
import net.multigesture.kanama.types.Color
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "TextureRect")
class Icone(godotObject: MemorySegment) : KanamaScript<TextureRect>(godotObject, ::TextureRect) {

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
        val source = if (state) disabled else enabled
        val tween = self.createTween() ?: return
        val tweener = tween.tweenProperty(self, "modulate", target, 0.2)
        tweener?.from(source)
    }
}
