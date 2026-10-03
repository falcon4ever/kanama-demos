package citybuilder

import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Export
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Resource
import net.multigesture.kanama.types.Vector2i

@ScriptClass(attachTo = "Resource")
@GlobalClass
class DataStructure(godotObject: GodotHandle) :
  KanamaScript<Resource>(godotObject, Resource::fromHandle) {
  @Export var position: Vector2i = Vector2i.ZERO

  @Export var orientation: Long = 0

  @Export var structure: Long = 0
}
