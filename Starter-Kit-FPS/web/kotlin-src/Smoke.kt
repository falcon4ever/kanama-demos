package fps

import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.kotlinScriptInstance

/**
 * Web variant of the FPS smoke root. The desktop Smoke drives an env-gated HEADLESS self-test
 * (KANAMA_FPS_SMOKE: rotation math, Player.damage → HUD "95%", Enemy.damage → queued deletion,
 * change_weapon → crosshair); on Web the browser driver fights for real instead — kanama
 * scripts/web/drivers/demos/fps.mjs shoots an enemy dead, and the exercised-member census
 * gates Enemy.damage / Hud._on_health_updated / Player.change_weapon. This override only
 * provides [smokeTeardown] (method#1), which drains every live handle to zero — including the
 * Audio autoload, which lives outside the scene root and would otherwise survive the root free.
 */
@ScriptClass(attachTo = "Node3D")
class Smoke(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {
  @RegisterFunction("smoke_teardown")
  fun smokeTeardown() {
    // Weapon resources persist in Godot's cache; release their hydrated asset handles so
    // the live-handle count can drain to zero.
    self.getNodeOrNull("Player")?.kotlinScriptInstance<Player>()?.weapons?.forEach {
      it.releaseHydratedAssets()
    }
    self.getNodeOrNull("/root/Audio")?.let { audio ->
      audio.kotlinScriptInstance<Audio>()?.stopAll()
      Node(audio.handle).queueFree()
    }
    self.queueFree()
  }
}
