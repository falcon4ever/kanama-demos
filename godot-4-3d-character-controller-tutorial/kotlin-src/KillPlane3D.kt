package charactercontroller

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.generated.Autoloads
import net.multigesture.kanama.generated.EventsNames
import net.multigesture.kanama.generated.killPlaneTouched
import net.multigesture.kanama.api.PhysicsBody3D
import net.multigesture.kanama.api.cast

@ScriptClass(attachTo = "Area3D")
class KillPlane3D(godotObject: GodotHandle) : KanamaScript<Area3D>(godotObject, ::Area3D) {

    @OnReady
    fun ready() {
        self.bodyEntered.connect { body ->
            launch {
                MainThread.awaitNextFrame()
                Autoloads.Events.killPlaneTouched.emit(body.cast<PhysicsBody3D>())
            }
        }
    }
}
