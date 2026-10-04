package charactercontroller

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.generated.Autoloads
import net.multigesture.kanama.generated.EventsNames

@ScriptClass(attachTo = "Node3D")
class Flag3D(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {

  @OnReady
  fun ready() {
    val area = self.requireAs("Area3D", ::Area3D)
    val events = Autoloads.Events.self
    area.bodyEntered.connect {
      events.emitSignal(EventsNames.Signals.flagReached)
    }
  }
}
