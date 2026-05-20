package charactercontroller

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.generated.EventsNames
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "Area3D")
class KillPlane3D(godotObject: MemorySegment) : KanamaScript<Area3D>(godotObject, ::Area3D), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()

    @OnReady
    fun ready() {
        self.signal(Area3D.Signals.bodyEntered).connectObject(self) { body ->
            kanamaScope.launch {
                MainThread.awaitNextFrame()
                self.eventsNode().emitSignal(EventsNames.Signals.killPlaneTouched, body)
            }
        }
    }
}
