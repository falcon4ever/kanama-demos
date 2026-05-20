package charactercontroller

import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.PhysicsBody3D
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node")
class Events(godotObject: MemorySegment) : KanamaScript<Node>(godotObject, ::Node) {
    @Signal
    fun killPlaneTouched(body: PhysicsBody3D) = Unit

    @Signal
    fun flagReached() = Unit
}
