package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Export
import net.multigesture.kanama.annotations.GodotName
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.api.TextureButton

@ScriptClass(attachTo = "TextureButton")
class LinkButton(godotObject: GodotHandle) : KanamaScript<TextureButton>(godotObject, ::TextureButton) {

    @Export
    var link: String = ""

    @OnReady
    fun ready() {
        self.pressed.connect {
            onButtonPressed()
        }
    }

    @GodotName("_on_button_pressed")
    fun onButtonPressed() {
        OS.shellOpen(link)
    }
}
