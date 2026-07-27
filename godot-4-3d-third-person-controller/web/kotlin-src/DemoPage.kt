package thirdperson

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.BaseButton
import net.multigesture.kanama.api.Button
import net.multigesture.kanama.api.Control
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEvent
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.types.Color

/**
 * Web port of the pause/instructions page (the game boots paused behind it). Adaptations:
 * pause state is tracked locally (this script is the only pauser), Exit hides the page and
 * resumes (a browser page has no app quit), keyboard instructions are always shown, and the
 * desktop deferred-lighting upgrade (SSIL/SDFGI) is skipped — the Compatibility renderer
 * does not support it.
 */
@ScriptClass(attachTo = "Node")
class DemoPage(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
  private lateinit var demoPageRoot: Control
  private lateinit var resumeButton: Button
  private lateinit var exitButton: Button
  private lateinit var keyboardButton: Button
  private lateinit var joypadButton: Button
  private lateinit var gridContainerKeyboard: Control
  private lateinit var gridContainerJoypad: Control

  private var demoMouseMode = Input.MOUSE_MODE_VISIBLE
  private var paused = false
  private var exiting = false

  @OnReady
  fun ready() {
    setPaused(true)

    demoMouseMode = Input.getMouseMode()
    Input.setMouseMode(Input.MOUSE_MODE_VISIBLE)

    demoPageRoot = self.requireAs("CanvasLayer/DemoPageRoot", ::Control)
    resumeButton =
      self.requireAs("CanvasLayer/DemoPageRoot/Content/MarginContainer/Buttons/Resume", ::Button)
    exitButton =
      self.requireAs("CanvasLayer/DemoPageRoot/Content/MarginContainer/Buttons/Exit", ::Button)
    keyboardButton = self.requireAs("%KeyboardButton", ::Button)
    joypadButton = self.requireAs("%JoypadButton", ::Button)
    gridContainerKeyboard = self.requireAs("%GridContainerKeyboard", ::Control)
    gridContainerJoypad = self.requireAs("%GridContainerJoypad", ::Control)

    resumeButton.signal(BaseButton.Signals.pressed).connect(self, argumentCount = 0) {
      resumeDemo()
    }
    exitButton.signal(BaseButton.Signals.pressed).connect(self, argumentCount = 0) { exitDemo() }
    keyboardButton.signal(BaseButton.Signals.pressed).connect(self, argumentCount = 0) {
      changeInstruction(KEYBOARD)
    }
    joypadButton.signal(BaseButton.Signals.pressed).connect(self, argumentCount = 0) {
      changeInstruction(JOYPAD)
    }

    changeInstruction(KEYBOARD)
  }

  @OnExitTree
  fun exitTree() {
    exiting = true
    DemoScenes.releaseWarmUp()
  }

  @OnInput
  fun input(event: GodotObject) {
    val inputEvent = InputEvent(event.handle)
    if (inputEvent.isActionPressed("pause")) {
      if (paused) {
        resumeDemo()
      } else {
        pauseDemo()
      }
    }
  }

  private fun changeInstruction(type: Long) {
    when (type) {
      KEYBOARD -> {
        gridContainerKeyboard.setVisible(true)
        gridContainerJoypad.setVisible(false)
      }
      JOYPAD -> {
        gridContainerKeyboard.setVisible(false)
        gridContainerJoypad.setVisible(true)
      }
    }
  }

  private fun pauseDemo() {
    demoMouseMode = Input.getMouseMode()
    setPaused(true)
    demoPageRoot.setVisible(true)
    demoPageRoot.modulate = Color(1f, 1f, 1f, 1f)
    Input.setMouseMode(Input.MOUSE_MODE_VISIBLE)
  }

  private fun resumeDemo() {
    setPaused(false)
    // Transparent controls still receive touch input, so hide the overlay before
    // restoring gameplay controls.
    demoPageRoot.modulate = Color(1f, 1f, 1f, 0f)
    demoPageRoot.setVisible(false)
    Input.setMouseMode(demoMouseMode)
  }

  private fun exitDemo() {
    // Browser adaptation: there is no app to quit; Exit just resumes gameplay.
    if (exiting) return
    resumeDemo()
  }

  /** Harness entry: the smoke presses Resume the same way the button does. */
  internal fun resumeFromSmoke() {
    resumeDemo()
  }

  private fun setPaused(value: Boolean) {
    paused = value
    self.getTree().setPaused(value)
  }

  companion object {
    private const val KEYBOARD = 0L
    private const val JOYPAD = 1L
  }
}
