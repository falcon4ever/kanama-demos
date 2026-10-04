package dodge

import net.multigesture.kanama.annotations.GodotName
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.Button
import net.multigesture.kanama.api.CanvasLayer
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.Timer
import net.multigesture.kanama.generated.HUDSignals

@ScriptClass(attachTo = "CanvasLayer")
class HUD(godotObject: GodotHandle) : KanamaScript<CanvasLayer>(godotObject, ::CanvasLayer) {
    /** Emitted when the player presses the start button. */
    @Signal
    fun startGame() = Unit

    private val messageLabel by node<Label>("MessageLabel")
    private val scoreLabel by node<Label>("ScoreLabel")
    private val startButton by node<Button>("StartButton")
    private val messageTimer by node<Timer>("MessageTimer")

    fun showMessage(text: String) {
        messageLabel.text = text
        messageLabel.show()
        messageTimer.start()
    }

    fun showGameOver() {
        launch {
            showMessage("Game Over")
            messageTimer.timeout.await()
            messageLabel.text = "Dodge the\nCreeps"
            messageLabel.show()
            wait(1.0)
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
