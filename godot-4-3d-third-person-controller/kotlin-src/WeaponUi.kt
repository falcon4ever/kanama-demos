package thirdperson

import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Control
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.kotlinScriptInstance
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Control")
class WeaponUi(godotObject: MemorySegment) : KanamaScript<Control>(godotObject, ::Control) {
    private val nodes by lazy {
        mapOf(
            "DEFAULT" to nodeObject("%Flash"),
            "GRENADE" to nodeObject("%Grenade"),
        )
    }

    private var selectedNode = ""

    @RegisterFunction("switch_to")
    fun switchTo(nodeName: String) {
        if (nodeName == selectedNode) return

        if (selectedNode.isNotEmpty()) {
            nodes[selectedNode]?.setState(false)
        }

        nodes[nodeName]?.setState(true)
        selectedNode = nodeName
    }

    private fun nodeObject(path: String): Icone =
        self.requireAs(path, ::Node).kotlinScriptInstance<Icone>()
            ?: error("$path is missing Icone script instance")
}
