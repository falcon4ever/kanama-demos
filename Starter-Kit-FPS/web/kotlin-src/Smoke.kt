package fps

import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.generated.Autoloads

/**
 * Web variant of the FPS smoke autoload. The desktop Smoke drives an env-gated HEADLESS self-test
 * (KANAMA_FPS_SMOKE: rotation math, Player.damage → HUD "95%", Enemy.damage → queued deletion,
 * change_weapon → crosshair); on Web the browser driver fights for real instead — kanama
 * scripts/web/drivers/demos/fps.mjs shoots an enemy dead, and the exercised-member census
 * gates Enemy.damage / Hud._on_health_updated / Player.change_weapon. This override only
 * provides [smokeTeardown], which drains every live handle to zero — including the
 * Audio autoload, which lives outside the scene root and would otherwise survive the root free.
 */
@ScriptClass(attachTo = "Node")
class Smoke(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
  fun smokeTeardown() {
    // Weapon resources persist in Godot's cache; release their hydrated asset handles so
    // the live-handle count can drain to zero.
    val main = self.getParent()?.getNodeOrNull("Main") ?: error("Smoke could not find the Main scene root")
    main.getNodeOrNull("Player")?.kotlinScriptInstance<Player>()?.weapons?.forEach {
      it.releaseHydratedAssets()
    }
    val audio = Autoloads.Audio
    audio.stopAll()
    audio.self.queueFree()
    main.queueFree()
    self.queueFree()
  }
}
