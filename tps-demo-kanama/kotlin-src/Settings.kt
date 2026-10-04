package tps

import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.ConfigFile
import net.multigesture.kanama.api.DisplayServer
import net.multigesture.kanama.api.Engine
import net.multigesture.kanama.api.Environment
import net.multigesture.kanama.api.InputEvent
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.RenderingServer
import net.multigesture.kanama.api.Viewport
import net.multigesture.kanama.api.Window

object TpsSettings {
    const val SDFGI = 0L
    const val VOXEL_GI = 1L
    const val LIGHTMAP_GI = 2L
    const val GI_DISABLED = 0L
    const val GI_LOW = 1L
    const val GI_HIGH = 2L

    private const val CONFIG_FILE_PATH = "user://settings.ini"

    val configFile: ConfigFile = TpsFactory.configFile()

    private val metalFxSupported: Boolean =
        RenderingServer.getCurrentRenderingDriverName() == "metal"

    private val defaults = mapOf(
        "video" to mapOf(
            "display_mode" to Window.Mode.WINDOWED,
            "vsync" to DisplayServer.VSyncMode.ENABLED,
            "max_fps" to 0L,
            "resolution_scale" to 1.0,
            "scale_filter" to if (metalFxSupported) {
                Viewport.Scaling3DMode.METALFX_TEMPORAL
            } else {
                Viewport.Scaling3DMode.FSR2
            },
        ),
        "rendering" to mapOf(
            "taa" to false,
            "msaa" to Viewport.MSAA.DISABLED,
            "fxaa" to false,
            "shadow_mapping" to true,
            "gi_type" to VOXEL_GI,
            "gi_quality" to GI_LOW,
            "ssao_quality" to RenderingServer.EnvironmentSSAOQuality.MEDIUM,
            "ssil_quality" to -1L,
            "bloom" to true,
            "volumetric_fog" to true,
        ),
    )

    fun loadSettings() {
        configFile.load(CONFIG_FILE_PATH)
        for ((section, keys) in defaults) {
            for ((key, value) in keys) {
                if (!configFile.hasSectionKey(section, key)) {
                    configFile.setValue(section, key, value)
                }
            }
        }
        val mode = Window.Mode(videoLong("display_mode"))
        if (mode == Window.Mode.FULLSCREEN || mode == Window.Mode.EXCLUSIVE_FULLSCREEN) {
            configFile.setValue("video", "display_mode", Window.Mode.WINDOWED)
        }
    }

    fun saveSettings() {
        configFile.save(CONFIG_FILE_PATH)
    }

    fun videoLong(key: String): Long = (configFile.getValue("video", key) as Number).toLong()
    fun videoInt(key: String): Int = (configFile.getValue("video", key) as Number).toInt()
    fun renderLong(key: String): Long = (configFile.getValue("rendering", key) as Number).toLong()
    fun renderBool(key: String): Boolean = configFile.getValue("rendering", key) as Boolean
    fun videoDouble(key: String): Double = (configFile.getValue("video", key) as Number).toDouble()

    fun applyGraphicsSettings(window: Window?, environment: Environment?, sceneRoot: Node) {
        if (DisplayServer.getName() != "headless") {
            window?.mode = Window.Mode(videoLong("display_mode"))
        }
        DisplayServer.windowSetVsyncMode(DisplayServer.VSyncMode(videoLong("vsync")))
        Engine.maxFps = videoInt("max_fps")
        window?.scaling3dScale = videoDouble("resolution_scale")
        window?.scaling3dMode = Viewport.Scaling3DMode(videoLong("scale_filter"))

        window?.useTaa = renderBool("taa")
        window?.msaa3d = Viewport.MSAA(renderLong("msaa"))
        window?.screenSpaceAa =
            if (renderBool("fxaa")) Viewport.ScreenSpaceAA.FXAA else Viewport.ScreenSpaceAA.DISABLED

        if (!renderBool("shadow_mapping")) {
            sceneRoot.propagateCall("set", listOf("shadow_enabled", false))
        }

        val env = environment ?: return
        when (renderLong("ssao_quality")) {
            -1L -> env.ssaoEnabled = false
            RenderingServer.EnvironmentSSAOQuality.MEDIUM.value -> {
                env.ssaoEnabled = true
                RenderingServer.environmentSetSsaoQuality(
                    RenderingServer.EnvironmentSSAOQuality.HIGH,
                    false,
                    0.5,
                    2,
                    50.0,
                    300.0,
                )
            }
            else -> {
                env.ssaoEnabled = true
                RenderingServer.environmentSetSsaoQuality(
                    RenderingServer.EnvironmentSSAOQuality.MEDIUM,
                    true,
                    0.5,
                    2,
                    50.0,
                    300.0,
                )
            }
        }

        when (renderLong("ssil_quality")) {
            -1L -> env.ssilEnabled = false
            RenderingServer.EnvironmentSSILQuality.MEDIUM.value -> {
                env.ssilEnabled = true
                RenderingServer.environmentSetSsilQuality(
                    RenderingServer.EnvironmentSSILQuality.MEDIUM,
                    false,
                    0.5,
                    2,
                    50.0,
                    300.0,
                )
            }
            else -> {
                env.ssilEnabled = true
                RenderingServer.environmentSetSsilQuality(
                    RenderingServer.EnvironmentSSILQuality.HIGH,
                    true,
                    0.5,
                    2,
                    50.0,
                    300.0,
                )
            }
        }

        env.glowEnabled = renderBool("bloom")
        env.volumetricFogEnabled = renderBool("volumetric_fog")
    }
}

@ScriptClass(attachTo = "Node")
class Settings(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
    @OnReady
    fun ready() {
        TpsSettings.loadSettings()
    }

    @OnInput
    fun input(inputEvent: InputEvent) {
        val event = inputEvent
        if (event.isActionPressed("toggle_fullscreen")) {
            val window = self.getWindow()
            val mode = window?.mode ?: Window.Mode.WINDOWED
            window?.mode =
                if (mode == Window.Mode.EXCLUSIVE_FULLSCREEN || mode == Window.Mode.FULLSCREEN) {
                    Window.Mode.WINDOWED
                } else {
                    Window.Mode.EXCLUSIVE_FULLSCREEN
                }
            self.getViewport()?.setInputAsHandled()
        }
    }
}
