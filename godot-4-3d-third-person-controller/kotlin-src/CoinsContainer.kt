package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Control
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Timer
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Control")
class CoinsContainer(godotObject: MemorySegment) : KanamaScript<Control>(godotObject, ::Control) {

    private lateinit var displayTimer: Timer
    private lateinit var coinsLabel: Label

    @OnReady
    fun ready() {
        displayTimer = self.requireAs("Timer", ::Timer)
        coinsLabel = self.requireAs("CoinsLabel", ::Label)
        displayTimer.signal(Timer.Signals.timeout).connect(self, argumentCount = 0) {
            onTimeout()
        }
    }

    @RegisterFunction("update_coins_amount")
    fun updateCoinsAmount(amount: Long) {
        if (displayTimer.isStopped()) {
            tweenPosition(DISPLAY_Y_POS)
        }
        displayTimer.start()
        coinsLabel.text = amount.toString()
    }

    private fun onTimeout() {
        tweenPosition(HIDDEN_Y_POS)
    }

    private fun tweenPosition(y: Long) {
        val tween = self.createTween() ?: return
        tween.tweenProperty(self, "position:y", y, 0.5)
    }

    companion object {
        private const val HIDDEN_Y_POS = -100L
        private const val DISPLAY_Y_POS = 20L
    }
}
