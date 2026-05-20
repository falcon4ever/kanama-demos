package thirdperson

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AudioStreamPlayer
import net.multigesture.kanama.api.BaseButton
import net.multigesture.kanama.api.Button
import net.multigesture.kanama.api.Control
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.api.SceneTree
import net.multigesture.kanama.api.Tween
import net.multigesture.kanama.api.WorldEnvironment
import net.multigesture.kanama.types.Color
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node")
class DemoPage(godotObject: MemorySegment) : KanamaScript<Node>(godotObject, ::Node) {
    private lateinit var demoPageRoot: Control
    private lateinit var resumeButton: Button
    private lateinit var exitButton: Button
    private lateinit var keyboardButton: Button
    private lateinit var joypadButton: Button
    private lateinit var gridContainerKeyboard: Control
    private lateinit var gridContainerJoypad: Control

    private var demoMouseMode = Input.MOUSE_MODE_VISIBLE
    private var warmupReleased = false
    private var exiting = false
    private var deferredLightingEnabled = false
    private var pageTween: Tween? = null
    // Keep this explicit for Android: nullable callback `?.invoke()` is unsafe
    // with the current source remap because it can be mistaken for MethodHandle.invoke.
    private var hideAfterTween = false

    @OnReady
    fun ready() {
        self.getTree().setPaused(true)
        DemoScenes.warmUp(if (shouldWarmUpInstances()) self else null)

        demoMouseMode = Input.getMouseMode()
        Input.setMouseMode(Input.MOUSE_MODE_VISIBLE)

        demoPageRoot = self.requireAs("CanvasLayer/DemoPageRoot", ::Control)
        resumeButton = self.requireAs("CanvasLayer/DemoPageRoot/Content/MarginContainer/Buttons/Resume", ::Button)
        exitButton = self.requireAs("CanvasLayer/DemoPageRoot/Content/MarginContainer/Buttons/Exit", ::Button)
        keyboardButton = self.requireAs("%KeyboardButton", ::Button)
        joypadButton = self.requireAs("%JoypadButton", ::Button)
        gridContainerKeyboard = self.requireAs("%GridContainerKeyboard", ::Control)
        gridContainerJoypad = self.requireAs("%GridContainerJoypad", ::Control)

        resumeButton.signal(BaseButton.Signals.pressed).connect(self, argumentCount = 0) {
            resumeDemo()
        }
        exitButton.signal(BaseButton.Signals.pressed).connect(self, argumentCount = 0) {
            exitDemo()
        }
        keyboardButton.signal(BaseButton.Signals.pressed).connect(self, argumentCount = 0) {
            changeInstruction(KEYBOARD)
        }
        joypadButton.signal(BaseButton.Signals.pressed).connect(self, argumentCount = 0) {
            changeInstruction(JOYPAD)
        }

        if (Input.getConnectedJoypads().isNotEmpty()) {
            changeInstruction(JOYPAD)
        } else {
            changeInstruction(KEYBOARD)
        }
    }

    @OnExitTree
    fun exitTree() {
        exiting = true
        clearPageTween()
        releaseWarmup()
    }

    @OnInput
    fun input(event: GodotObject) {
        val inputEvent = net.multigesture.kanama.api.InputEvent(event.handle)
        if (inputEvent.isActionPressed("pause") && !inputEvent.isEcho()) {
            if (self.getTree().isPaused()) {
                resumeDemo()
            } else {
                pauseDemo()
            }
        }
    }

    private fun changeInstruction(type: Long) {
        when (type) {
            KEYBOARD -> {
                keyboardButton.modulate = keyboardButton.modulate.withAlpha(1f)
                joypadButton.modulate = joypadButton.modulate.withAlpha(0.3f)
                gridContainerKeyboard.show()
                gridContainerJoypad.hide()
            }
            JOYPAD -> {
                keyboardButton.modulate = keyboardButton.modulate.withAlpha(0.3f)
                joypadButton.modulate = joypadButton.modulate.withAlpha(1f)
                gridContainerKeyboard.hide()
                gridContainerJoypad.show()
            }
        }

        keyboardButton.releaseFocus()
        joypadButton.releaseFocus()
    }

    private fun pauseDemo() {
        demoMouseMode = Input.getMouseMode()
        self.getTree().setPaused(true)
        demoPageRoot.show()
        tweenDemoPage(Color(1f, 1f, 1f, 1f))
        Input.setMouseMode(Input.MOUSE_MODE_VISIBLE)
    }

    private fun resumeDemo() {
        self.getTree().setPaused(false)
        clearPageTween()
        hideAfterTween = false
        // Transparent controls still receive touch input, so hide the overlay
        // before restoring gameplay controls.
        demoPageRoot.modulate = Color(1f, 1f, 1f, 0f)
        demoPageRoot.hide()
        enableDeferredLightingAfterResume()
        Input.setMouseMode(demoMouseMode)
    }

    private fun exitDemo() {
        if (exiting) return
        exiting = true
        clearPageTween()
        releaseWarmup()
        self.getTree().setPaused(false)
        demoPageRoot.hide()
        stopStageMusic()
        SceneTree.unloadCurrentScene()
        MainThread.postAfterFrames(QUIT_AFTER_UNLOAD_FRAMES) {
            SceneTree.quit()
        }
    }

    private fun stopStageMusic() {
        self.getParent()
            ?.getAsOrNull("StageMusic", ::AudioStreamPlayer)
            ?.stop()
    }

    private fun releaseWarmup() {
        if (!warmupReleased) {
            DemoScenes.releaseWarmUp()
            warmupReleased = true
        }
    }

    private fun tweenDemoPage(target: Color) {
        clearPageTween()
        val tween = self.createTween()
        if (tween == null) {
            demoPageRoot.modulate = target
            finishDemoPageTween()
            return
        }

        pageTween = tween
        tween.tweenProperty(demoPageRoot, "modulate", target, DEMO_PAGE_FADE_SECONDS)
        tween.signal(Tween.Signals.finished).connect(self, argumentCount = 0, flags = GodotObject.CONNECT_ONE_SHOT) {
            if (pageTween === tween) {
                pageTween = null
            }
            finishDemoPageTween()
        }
    }

    private fun finishDemoPageTween() {
        if (hideAfterTween && !exiting && !self.getTree().isPaused()) {
            hideAfterTween = false
            demoPageRoot.hide()
            enableDeferredLightingAfterResume()
        }
    }

    private fun clearPageTween() {
        pageTween?.let { tween ->
            tween.kill()
        }
        pageTween = null
        hideAfterTween = false
    }

    private fun enableDeferredLightingAfterResume() {
        if (deferredLightingEnabled) return
        deferredLightingEnabled = true
        MainThread.postAfterFrames(ENABLE_LIGHTING_AFTER_RESUME_FRAMES) {
            if (!exiting) {
                enableDeferredLighting()
            }
        }
    }

    private fun enableDeferredLighting() {
        val environment = self.getParent()
            ?.getAsOrNull("WorldEnvironment", ::WorldEnvironment)
            ?.environment
            ?: return

        environment.ssilEnabled = true
        environment.sdfgiEnabled = true
    }

    private fun shouldWarmUpInstances(): Boolean =
        OS.hasFeature("mobile") || OS.hasFeature("android") || OS.hasFeature("Android")

    private fun Color.withAlpha(alpha: Float): Color =
        Color(r, g, b, alpha)

    companion object {
        private const val KEYBOARD = 0L
        private const val JOYPAD = 1L
        private const val DEMO_PAGE_FADE_SECONDS = 0.3
        private const val ENABLE_LIGHTING_AFTER_RESUME_FRAMES = 600
        private const val QUIT_AFTER_UNLOAD_FRAMES = 8
    }
}
