package citybuilder

import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Resource

@ScriptClass(attachTo = "Resource")
@GlobalClass
class DataMap(godotObject: GodotHandle) :
  KanamaScript<Resource>(godotObject, Resource::fromHandle) {
  @ScriptProperty var cash: Long = 10000

  @ScriptProperty var structures: List<DataStructure> = emptyList()
}
