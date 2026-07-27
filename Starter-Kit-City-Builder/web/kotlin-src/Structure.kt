package citybuilder

import net.multigesture.kanama.annotations.ExportSubgroup
import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.Resource

@ScriptClass(attachTo = "Resource")
@GlobalClass
class Structure(godotObject: GodotHandle) : KanamaScript<Resource>(godotObject, ::Resource) {
  @ExportSubgroup("Model") @ScriptProperty var model: PackedScene? = null

  @ExportSubgroup("Gameplay") @ScriptProperty var price: Long = 0
}
