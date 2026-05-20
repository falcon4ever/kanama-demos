package dodge

import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.Button
import net.multigesture.kanama.api.CanvasLayer
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.Timer
import net.multigesture.kanama.generated.HUDSignals
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "CanvasLayer")
class HUD(godotObject: MemorySegment) : KanamaScript<CanvasLayer>(godotObject, ::CanvasLayer), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()

    /** Emitted when the player presses the start button. */
    @Signal
    fun startGame() = Unit

    private val messageLabel: Label get() = self.requireAs("MessageLabel", ::Label)
    private val scoreLabel: Label get() = self.requireAs("ScoreLabel", ::Label)
    private val startButton: Button get() = self.requireAs("StartButton", ::Button)
    private val messageTimer: Timer get() = self.requireAs("MessageTimer", ::Timer)

    @RegisterFunction("show_message")
    fun showMessage(text: String) {
        messageLabel.text = text
        messageLabel.show()
        messageTimer.start()
    }

    @RegisterFunction("show_game_over")
    fun showGameOver() {
        kanamaScope.launch {
            showMessage("Game Over")
            messageTimer.signal(Timer.Signals.timeout).await(self, argumentCount = 0)
            messageLabel.text = "Dodge the\nCreeps"
            messageLabel.show()
            self.getTree().delaySeconds(1.0)
            startButton.show()
        }
    }

    @RegisterFunction("update_score")
    fun updateScore(score: Long) {
        scoreLabel.text = score.toString()
    }

    @RegisterFunction("_on_StartButton_pressed")
    fun onStartButtonPressed() {
        startButton.hide()
        HUDSignals.startGame(this)
    }

    @RegisterFunction("_on_MessageTimer_timeout")
    fun onMessageTimerTimeout() {
        messageLabel.hide()
    }
}
