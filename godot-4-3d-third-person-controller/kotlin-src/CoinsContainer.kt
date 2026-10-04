package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Control
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Timer
import net.multigesture.kanama.api.createTween

@ScriptClass(attachTo = "Control")
class CoinsContainer(godotObject: GodotHandle) : KanamaScript<Control>(godotObject, ::Control) {

    private lateinit var displayTimer: Timer
    private lateinit var coinsLabel: Label

    @OnReady
    fun ready() {
        displayTimer = self.requireAs("Timer", ::Timer)
        coinsLabel = self.requireAs("CoinsLabel", ::Label)
        displayTimer.timeout.connect {
            onTimeout()
        }
    }

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
        val tween = self.createTween()
        tween.tweenProperty(self, "position:y", y, 0.5)
    }

    companion object {
        private const val HIDDEN_Y_POS = -100L
        private const val DISPLAY_Y_POS = 20L
    }
}
