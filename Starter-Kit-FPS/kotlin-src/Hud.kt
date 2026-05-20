package fps

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.CanvasLayer
import net.multigesture.kanama.api.Label
import java.lang.foreign.MemorySegment
import net.multigesture.kanama.api.KanamaScript

@ScriptClass(attachTo = "CanvasLayer")
class Hud(godotObject: MemorySegment) : KanamaScript<CanvasLayer>(godotObject, ::CanvasLayer) {
    private lateinit var healthLabel: Label

    @OnReady
    fun ready() {
        healthLabel = self.requireAs("Health", ::Label)
    }

    @RegisterFunction("_on_health_updated")
    fun onHealthUpdated(health: Long) {
        healthLabel.text = "$health%"
    }
}
