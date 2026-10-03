package dodge

import net.multigesture.kanama.annotations.GodotName
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.Button
import net.multigesture.kanama.api.CanvasLayer
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.Timer
import net.multigesture.kanama.generated.HUDSignals
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "CanvasLayer")
class HUD(godotObject: GodotHandle) : KanamaScript<CanvasLayer>(godotObject, ::CanvasLayer), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()

    /** Emitted when the player presses the start button. */
    @Signal
    fun startGame() = Unit

    private val messageLabel: Label get() = self.requireAs("MessageLabel", ::Label)
    private val scoreLabel: Label get() = self.requireAs("ScoreLabel", ::Label)
    private val startButton: Button get() = self.requireAs("StartButton", ::Button)
    private val messageTimer: Timer get() = self.requireAs("MessageTimer", ::Timer)

    fun showMessage(text: String) {
        messageLabel.text = text
        messageLabel.show()
        messageTimer.start()
    }

    fun showGameOver() {
        kanamaScope.launch {
            showMessage("Game Over")
            messageTimer.signal(Timer.Signals.timeout).await(self, argumentCount = 0)
            messageLabel.text = "Dodge the\nCreeps"
            messageLabel.show()
            requireNotNull(self.getTree()).delaySeconds(1.0)
            startButton.show()
        }
    }

    fun updateScore(score: Long) {
        scoreLabel.text = score.toString()
    }

    @GodotName("_on_StartButton_pressed")
    fun onStartButtonPressed() {
        startButton.hide()
        HUDSignals.startGame(this)
    }

    @GodotName("_on_MessageTimer_timeout")
    fun onMessageTimerTimeout() {
        messageLabel.hide()
    }
}
