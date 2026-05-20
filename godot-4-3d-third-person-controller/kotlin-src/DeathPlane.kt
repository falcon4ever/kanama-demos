package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.kotlinScriptInstance
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Area3D")
class DeathPlane(godotObject: MemorySegment) : KanamaScript<Area3D>(godotObject, ::Area3D) {
    @OnReady
    fun ready() {
        self.signal(Area3D.Signals.bodyEntered).connectObject(self) { body ->
            if (!Node3D(body.handle).isPlayer()) return@connectObject
            body.kotlinScriptInstance<Player>()?.resetPosition()
                ?: error("DeathPlane body is marked Player but missing Player script instance")
        }
    }
}
