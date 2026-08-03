package net.multigesture.kanama.demos.platformer3d

import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Control
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label

/** Web port of the HUD: updates the coin count label (handler wired via the scene signal). */
@ScriptClass(attachTo = "Control")
class Hud(godotObject: GodotHandle) : KanamaScript<Control>(godotObject, ::Control) {
  @RegisterFunction("_on_coin_collected")
  fun onCoinCollected(coins: Long) {
    self.getAsOrNull("Coins", ::Label)?.let { it.text = coins.toString() }
  }
}
