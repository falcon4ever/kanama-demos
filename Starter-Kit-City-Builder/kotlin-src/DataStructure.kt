package citybuilder

import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Resource
import net.multigesture.kanama.types.Vector2i
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Resource")
@GlobalClass
class DataStructure(godotObject: MemorySegment) : KanamaScript<Resource>(godotObject, Resource::fromHandle) {
    @ScriptProperty
    var position: Vector2i = Vector2i.ZERO

    @ScriptProperty
    var orientation: Long = 0

    @ScriptProperty
    var structure: Long = 0
}
