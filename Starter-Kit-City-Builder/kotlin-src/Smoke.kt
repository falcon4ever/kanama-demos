package citybuilder

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GridMap
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.types.Vector3i
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node")
class Smoke(godotObject: MemorySegment) : KanamaScript<Node>(godotObject, ::Node) {
    @OnReady
    fun ready() {
        if (System.getenv("KANAMA_CITY_BUILDER_SMOKE") != "1") return

        val builderNode = self.requireAs("../Builder", ::Node3D)
        val builder = builderNode.kotlinScriptInstance<Builder>()
            ?: error("City Builder smoke expected Builder script instance")
        val grid = self.requireAs("../GridMap", ::GridMap)

        for (structureIndex in builder.structures.indices) {
            val smokeCell = Vector3i(structureIndex, 0, 3)
            grid.setCellItem(smokeCell, structureIndex, 0)
            check(grid.getCellItem(smokeCell) == structureIndex) {
                "GridMap smoke cell $structureIndex was not placed"
            }
        }
        check(grid.getUsedCells().size >= builder.structures.size) {
            "GridMap smoke cells were not reported as used"
        }
        self.getTree().quit()
    }
}
