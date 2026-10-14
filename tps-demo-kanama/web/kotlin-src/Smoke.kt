package tps

import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.generated.Autoloads

/**
 * Web variant of the smoke autoload (Kanama task 131 item 17). The desktop Smoke drives the
 * menu, the level and the parts from `KANAMA_TPS_SMOKE_*` variables the browser has none of, so
 * this override only holds the browser harness entry points. The driver finds them by name in
 * the export manifest (`smoke_start_game`, `smoke_teardown`), never by position.
 */
@ScriptClass(attachTo = "Node")
class Smoke(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
    /** Presses Play through the menu's own path; the scene root outlives the menu/level swap. */
    fun smokeStartGame() {
        val menu = sceneRoot().getChildren().firstNotNullOfOrNull { it.kotlinScriptInstance<Menu>() }
            ?: error("TPS smoke could not find the Menu script to start a game")
        menu.onPlayPressed()
    }

    fun smokeTeardown() {
        // Godot's resource cache keeps the loaded scenes alive past the scene-root free, and the
        // settings ConfigFile is a Kotlin-owned handle: both must be released for the live-handle
        // count to drain to zero. This autoload sits outside the scene root, so it frees itself too.
        TpsScenes.releaseCachedScenes()
        Autoloads.Settings.releaseConfigFile()
        sceneRoot().queueFree()
        self.queueFree()
    }

    private fun sceneRoot(): Node =
        self.getParent()?.getChildren()?.firstOrNull { it.kotlinScriptInstance<Main>() != null }
            ?: error("TPS smoke could not find the Main scene root")
}
