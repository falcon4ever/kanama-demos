package citybuilder

import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Export
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Resource

@ScriptClass(attachTo = "Resource")
@GlobalClass
class DataMap(godotObject: GodotHandle) :
  KanamaScript<Resource>(godotObject, Resource::fromHandle) {
  @Export var cash: Long = 10000

  @Export var structures: List<DataStructure> = emptyList()
}
