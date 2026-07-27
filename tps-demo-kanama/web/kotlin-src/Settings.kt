package tps

import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.ConfigFile
import net.multigesture.kanama.api.DisplayServer
import net.multigesture.kanama.api.Engine
import net.multigesture.kanama.api.Environment
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.InputEvent
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.RenderingServer
import net.multigesture.kanama.api.RenderingServerQuality
import net.multigesture.kanama.api.Viewport
import net.multigesture.kanama.api.Window
import net.multigesture.kanama.api.getCurrentRenderingDriverName
import net.multigesture.kanama.api.getWindow
import net.multigesture.kanama.api.glowEnabled
import net.multigesture.kanama.api.propagateSet
import net.multigesture.kanama.api.ssaoEnabled
import net.multigesture.kanama.api.ssilEnabled
import net.multigesture.kanama.api.volumetricFogEnabled
import net.multigesture.kanama.api.environmentSetSsaoQuality
import net.multigesture.kanama.api.environmentSetSsilQuality
import net.multigesture.kanama.api.setInputAsHandled

object TpsSettings {
  const val SDFGI = 0L
  const val VOXEL_GI = 1L
  const val LIGHTMAP_GI = 2L
  const val GI_DISABLED = 0L
  const val GI_LOW = 1L
  const val GI_HIGH = 2L

  private const val CONFIG_FILE_PATH = "user://settings.ini"

  val configFile: ConfigFile = TpsFactory.configFile()

  private val metalFxSupported: Boolean = RenderingServer.getCurrentRenderingDriverName() == "metal"

  private val defaults =
    mapOf(
      "video" to
        mapOf(
          "display_mode" to Window.MODE_WINDOWED,
          "vsync" to DisplayServer.VSYNC_ENABLED,
          "max_fps" to 0L,
          "resolution_scale" to 1.0,
          "scale_filter" to
            if (metalFxSupported) {
              Viewport.SCALING_3D_MODE_METALFX_TEMPORAL
            } else {
              Viewport.SCALING_3D_MODE_FSR2
            },
        ),
      "rendering" to
        mapOf(
          "taa" to false,
          "msaa" to Viewport.MSAA_DISABLED,
          "fxaa" to false,
          "shadow_mapping" to true,
          "gi_type" to VOXEL_GI,
          "gi_quality" to GI_LOW,
          "ssao_quality" to RenderingServerQuality.ENV_SSAO_QUALITY_MEDIUM,
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
    val mode = videoLong("display_mode")
    if (mode == Window.MODE_FULLSCREEN || mode == Window.MODE_EXCLUSIVE_FULLSCREEN) {
      configFile.setValue("video", "display_mode", Window.MODE_WINDOWED)
    }
  }

  fun saveSettings() {
    configFile.save(CONFIG_FILE_PATH)
  }

  /**
   * Web teardown: the settings ConfigFile is a Kotlin-owned handle held for the whole session
   * (it outlives every scene), so the smoke releases it to drain the live-handle count to zero.
   */
  fun releaseConfigFile() {
    configFile.close()
  }

  fun videoLong(key: String): Long = (configFile.getValue("video", key) as Number).toLong()

  fun videoInt(key: String): Int = (configFile.getValue("video", key) as Number).toInt()

  fun renderLong(key: String): Long = (configFile.getValue("rendering", key) as Number).toLong()

  fun renderBool(key: String): Boolean = configFile.getValue("rendering", key) as Boolean

  fun videoDouble(key: String): Double = (configFile.getValue("video", key) as Number).toDouble()

  fun applyGraphicsSettings(window: Window?, environment: Environment?, sceneRoot: Node) {
    if (DisplayServer.getName() != "headless") {
      window?.mode = videoLong("display_mode")
    }
    DisplayServer.windowSetVsyncMode(videoLong("vsync"))
    Engine.maxFps = videoInt("max_fps")
    window?.scaling3dScale = videoDouble("resolution_scale")
    window?.scaling3dMode = videoLong("scale_filter")

    window?.useTaa = renderBool("taa")
    window?.msaa3d = renderLong("msaa")
    window?.screenSpaceAa =
      if (renderBool("fxaa")) Viewport.SCREEN_SPACE_AA_FXAA else Viewport.SCREEN_SPACE_AA_DISABLED

    if (!renderBool("shadow_mapping")) {
      // Web adaptation: the seam carries the typed (property, value) pair rather than a
      // Variant Array, so the propagated call is spelled propagateSet.
      sceneRoot.propagateSet("shadow_enabled", false)
    }

    val env = environment ?: return
    when (renderLong("ssao_quality")) {
      -1L -> env.ssaoEnabled = false
      RenderingServerQuality.ENV_SSAO_QUALITY_MEDIUM -> {
        env.ssaoEnabled = true
        RenderingServer.environmentSetSsaoQuality(
          RenderingServerQuality.ENV_SSAO_QUALITY_HIGH,
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
          RenderingServerQuality.ENV_SSAO_QUALITY_MEDIUM,
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
      RenderingServerQuality.ENV_SSIL_QUALITY_MEDIUM -> {
        env.ssilEnabled = true
        RenderingServer.environmentSetSsilQuality(
          RenderingServerQuality.ENV_SSIL_QUALITY_MEDIUM,
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
          RenderingServerQuality.ENV_SSIL_QUALITY_HIGH,
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
  fun input(inputEvent: GodotObject) {
    val event = InputEvent(inputEvent.handle)
    if (event.isActionPressed("toggle_fullscreen")) {
      val window = self.getWindow()
      val mode = window.mode
      window.mode =
        if (mode == Window.MODE_EXCLUSIVE_FULLSCREEN || mode == Window.MODE_FULLSCREEN) {
          Window.MODE_WINDOWED
        } else {
          Window.MODE_EXCLUSIVE_FULLSCREEN
        }
      self.getViewport()?.setInputAsHandled()
    }
  }
}
