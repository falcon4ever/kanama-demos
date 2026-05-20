import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.Node
import java.lang.foreign.MemorySegment
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Control

@ScriptClass(attachTo = "Control")
class Hud(godotObject: MemorySegment) : KanamaScript<Control>(godotObject, ::Control) {

	@RegisterFunction("_on_coin_collected")
	fun onCoinCollected(coins: Long) {
		self.getNodeAsOrNull("Coins", "Label", ::Label)?.setText(coins.toString())
	}
}
