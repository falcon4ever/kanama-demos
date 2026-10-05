package charactercontroller

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.SceneTree
import net.multigesture.kanama.generated.Autoloads
import net.multigesture.kanama.generated.EventsNames

/**
 * Web adaptation: the desktop one-frame MainThread.awaitNextFrame becomes a zero-second
 * frame-scheduler delay (same "emit after the physics callback unwinds" effect).
 */
@ScriptClass(attachTo = "Area3D")
class KillPlane3D(godotObject: GodotHandle) :
  KanamaScript<Area3D>(godotObject, ::Area3D) {

  @OnReady
  fun ready() {
    self.signal(Area3D.Signals.bodyEntered).connectObject(self) { body ->
      launch {
        SceneTree.delaySeconds(0.0)
        Autoloads.Events.self.emitSignal(EventsNames.Signals.killPlaneTouched, body)
      }
    }
  }
}
