package charactercontroller

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.generated.EventsNames

@ScriptClass(attachTo = "Area3D")
class KillPlane3D(godotObject: GodotHandle) : KanamaScript<Area3D>(godotObject, ::Area3D) {

    @OnReady
    fun ready() {
        self.signal(Area3D.Signals.bodyEntered).connectObject(self) { body ->
            launch {
                MainThread.awaitNextFrame()
                self.eventsNode().emitSignal(EventsNames.Signals.killPlaneTouched, body)
            }
        }
    }
}
