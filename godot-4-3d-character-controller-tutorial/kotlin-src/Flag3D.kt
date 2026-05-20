package charactercontroller

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.generated.EventsNames
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node3D")
class Flag3D(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {

    @OnReady
    fun ready() {
        val area = self.requireAs("Area3D", ::Area3D)
        val events = self.eventsNode()
        area.signal(Area3D.Signals.bodyEntered).connect(self, argumentCount = 1) {
            events.emitSignal(EventsNames.Signals.flagReached)
        }
    }
}
