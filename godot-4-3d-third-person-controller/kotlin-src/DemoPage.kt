package thirdperson

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AudioStreamPlayer
import net.multigesture.kanama.api.Button
import net.multigesture.kanama.api.Control
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEvent
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.api.SceneTree
import net.multigesture.kanama.api.Tween
import net.multigesture.kanama.api.WorldEnvironment
import net.multigesture.kanama.types.Color

@ScriptClass(attachTo = "Node")
class DemoPage(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
    private lateinit var demoPageRoot: Control
    private lateinit var resumeButton: Button
    private lateinit var exitButton: Button
    private lateinit var keyboardButton: Button
    private lateinit var joypadButton: Button
    private lateinit var gridContainerKeyboard: Control
    private lateinit var gridContainerJoypad: Control

    private var demoMouseMode = Input.MouseMode.VISIBLE
    private var warmupReleased = false
    private var exiting = false
    private var deferredLightingEnabled = false
    private var pageTween: Tween? = null
    // Keep this explicit for Android: nullable callback `?.invoke()` is unsafe
    // with the current source remap because it can be mistaken for MethodHandle.invoke.
    private var hideAfterTween = false

    @OnReady
    fun ready() {
        requireNotNull(self.getTree()).setPaused(true)
        // Desktop warms the enemy/player instances only where the first instantiation hitches
        // (mobile); Web always warms its bullet pool against this page, as its former override did.
        DemoScenes.warmUp(if (shouldWarmUpInstances() || isWeb()) self else null)

        demoMouseMode = Input.getMouseMode()
        Input.setMouseMode(Input.MouseMode.VISIBLE)

        demoPageRoot = self.requireAs("CanvasLayer/DemoPageRoot", ::Control)
        resumeButton = self.requireAs("CanvasLayer/DemoPageRoot/Content/MarginContainer/Buttons/Resume", ::Button)
        exitButton = self.requireAs("CanvasLayer/DemoPageRoot/Content/MarginContainer/Buttons/Exit", ::Button)
        keyboardButton = self.requireAs("%KeyboardButton", ::Button)
        joypadButton = self.requireAs("%JoypadButton", ::Button)
        gridContainerKeyboard = self.requireAs("%GridContainerKeyboard", ::Control)
        gridContainerJoypad = self.requireAs("%GridContainerJoypad", ::Control)

        resumeButton.pressed.connect {
            resumeDemo()
        }
        exitButton.pressed.connect {
            exitDemo()
        }
        keyboardButton.pressed.connect {
            changeInstruction(KEYBOARD)
        }
        joypadButton.pressed.connect {
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
    fun input(event: InputEvent) {
        val inputEvent = event
        if (inputEvent.isActionPressed("pause") && !inputEvent.isEcho()) {
            if (requireNotNull(self.getTree()).isPaused()) {
                resumeDemo()
            } else {
                pauseDemo()
            }
        }
    }

    private fun changeInstruction(type: Long) {
        when (type) {
            KEYBOARD -> {
                keyboardButton.modulate = keyboardButton.modulate.withAlpha(1.0)
                joypadButton.modulate = joypadButton.modulate.withAlpha(0.3)
                gridContainerKeyboard.show()
                gridContainerJoypad.hide()
            }
            JOYPAD -> {
                keyboardButton.modulate = keyboardButton.modulate.withAlpha(0.3)
                joypadButton.modulate = joypadButton.modulate.withAlpha(1.0)
                gridContainerKeyboard.hide()
                gridContainerJoypad.show()
            }
        }

        keyboardButton.releaseFocus()
        joypadButton.releaseFocus()
    }

    private fun pauseDemo() {
        demoMouseMode = Input.getMouseMode()
        requireNotNull(self.getTree()).setPaused(true)
        demoPageRoot.show()
        tweenDemoPage(Color(1.0, 1.0, 1.0, 1.0))
        Input.setMouseMode(Input.MouseMode.VISIBLE)
    }

    internal fun resumeDemo() {
        requireNotNull(self.getTree()).setPaused(false)
        clearPageTween()
        hideAfterTween = false
        // Transparent controls still receive touch input, so hide the overlay
        // before restoring gameplay controls.
        demoPageRoot.modulate = Color(1.0, 1.0, 1.0, 0.0)
        demoPageRoot.hide()
        enableDeferredLightingAfterResume()
        Input.setMouseMode(demoMouseMode)
    }

    private fun exitDemo() {
        if (exiting) return
        if (isWeb()) {
            // A browser page has no app to quit: Exit just resumes gameplay (the former Web override).
            resumeDemo()
            return
        }
        exiting = true
        clearPageTween()
        releaseWarmup()
        requireNotNull(self.getTree()).setPaused(false)
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
        tween.finished.connect(GodotObject.ConnectFlags.ONE_SHOT) {
            if (pageTween === tween) {
                pageTween = null
            }
            finishDemoPageTween()
        }
    }

    private fun finishDemoPageTween() {
        if (hideAfterTween && !exiting && !requireNotNull(self.getTree()).isPaused()) {
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
        // The Compatibility renderer (Web) has no SSIL/SDFGI: skip the upgrade rather than queue
        // no-op toggles every resume.
        if (isWeb()) return
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

    private fun isWeb(): Boolean = OS.hasFeature("web")

    private fun Color.withAlpha(alpha: Double): Color =
        Color(r, g, b, alpha)

    companion object {
        private const val KEYBOARD = 0L
        private const val JOYPAD = 1L
        private const val DEMO_PAGE_FADE_SECONDS = 0.3
        private const val ENABLE_LIGHTING_AFTER_RESUME_FRAMES = 600
        private const val QUIT_AFTER_UNLOAD_FRAMES = 8
    }
}
