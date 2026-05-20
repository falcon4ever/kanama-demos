package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.BaseButton
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.api.TextureButton
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "TextureButton")
class LinkButton(godotObject: MemorySegment) : KanamaScript<TextureButton>(godotObject, ::TextureButton) {

    @ScriptProperty
    var link: String = ""

    @OnReady
    fun ready() {
        self.signal(BaseButton.Signals.pressed).connect(self, argumentCount = 0) {
            onButtonPressed()
        }
    }

    @RegisterFunction("_on_button_pressed")
    fun onButtonPressed() {
        OS.shellOpen(link)
    }
}
