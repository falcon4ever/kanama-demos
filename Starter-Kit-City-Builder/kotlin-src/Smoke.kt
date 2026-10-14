package citybuilder

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.GridMap
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.types.Vector3i

/** Desktop smoke of the City Builder demo, as an autoload (Kanama task 131 item 17). */
@ScriptClass(attachTo = "Node")
class Smoke(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
    @OnReady
    fun ready() {
        if (System.getenv("KANAMA_CITY_BUILDER_SMOKE") != "1") return
        launch {
            MainThread.awaitNextFrame() // the scene root is added after the autoloads' ready
            runChecks(self.getParent()?.getNodeOrNull("Main") ?: error("City Builder smoke could not find the Main scene root"))
        }
    }

    private fun runChecks(main: Node) {
        val builderNode = main.requireAs("Builder", ::Node3D)
        val builder = builderNode.kotlinScriptInstance<Builder>()
            ?: error("City Builder smoke expected Builder script instance")
        val grid = main.requireAs("GridMap", ::GridMap)

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
        // The completion line scripts/desktop_smoke_all.sh and the iOS runner require (task 111/131).
        println("[kanama:smoke] SmokeQuit complete")
        requireNotNull(self.getTree()).quit()
    }
}
