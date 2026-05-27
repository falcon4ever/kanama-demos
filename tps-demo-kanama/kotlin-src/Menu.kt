package tps

import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.BaseButton
import net.multigesture.kanama.api.Button
import net.multigesture.kanama.api.Control
import net.multigesture.kanama.api.DisplayServer
import net.multigesture.kanama.api.ENetMultiplayerPeer
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.LineEdit
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.MultiplayerPeer
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.ProgressBar
import net.multigesture.kanama.api.RenderingServer
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.SpinBox
import net.multigesture.kanama.api.Timer
import net.multigesture.kanama.api.Viewport
import net.multigesture.kanama.api.Window
import net.multigesture.kanama.api.WorldEnvironment
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node")
class Menu(godotObject: MemorySegment) : KanamaScript<Node>(godotObject, ::Node) {
    private var peer: MultiplayerPeer = TpsFactory.offlineMultiplayerPeer()
    private val metalFxSupported = RenderingServer.getCurrentRenderingDriverName() == "metal"

    private lateinit var worldEnvironment: WorldEnvironment
    private lateinit var main: Control
    private lateinit var playButton: Button
    private lateinit var online: Control
    private lateinit var onlinePort: SpinBox
    private lateinit var onlineAddress: LineEdit
    private lateinit var settingsMenu: Control
    private lateinit var settingsActionCancel: Button
    private lateinit var loading: Control
    private lateinit var loadingProgress: ProgressBar
    private lateinit var loadingDoneTimer: Timer

    private val buttons = mutableMapOf<String, Button>()
    private var levelSceneChangeStarted = false
    private var lastLoggedLoadStatus: Long? = null
    private var lastLoggedProgressBucket = -1

    @OnReady
    fun ready() {
        worldEnvironment = self.requireAs("WorldEnvironment", ::WorldEnvironment)
        main = self.requireAs("UI/Main", ::Control)
        playButton = self.requireAs("UI/Main/Play", ::Button)
        online = self.requireAs("UI/Online", ::Control)
        onlinePort = self.requireAs("UI/Online/Port", ::SpinBox)
        onlineAddress = self.requireAs("UI/Online/Address", ::LineEdit)
        settingsMenu = self.requireAs("UI/Settings", ::Control)
        settingsActionCancel = self.requireAs("UI/Settings/Actions/Cancel", ::Button)
        loading = self.requireAs("UI/Loading", ::Control)
        loadingProgress = self.requireAs("UI/Loading/Progress", ::ProgressBar)
        loadingDoneTimer = self.requireAs("UI/Loading/DoneTimer", ::Timer)
        loadingDoneTimer.signal(Timer.Signals.timeout).connect(self, argumentCount = 0) {
            onLoadingDoneTimerTimeout()
        }

        registerButtons()
        TpsSettings.applyGraphicsSettings(self.getWindow(), worldEnvironment.environment, self)

        if (DisplayServer.getName() == "headless" || System.getenv("KANAMA_TPS_SMOKE_AUTOSTART") == "1") {
            self.callDeferred("_on_host_pressed")
        }

        playButton.grabFocus()
        if (!metalFxSupported) {
            button("ScaleFilter/MetalFXSpatial")?.hide()
            button("ScaleFilter/MetalFXTemporal")?.hide()
        }

        listOf(
            "DisplayMode",
            "VSync",
            "MaxFPS",
            "ResolutionScale",
            "ScaleFilter",
            "TAA",
            "MSAA",
            "FXAA",
            "ShadowMapping",
            "GIType",
            "GIQuality",
            "SSAO",
            "SSIL",
            "Bloom",
            "VolumetricFog",
        ).forEach { makeButtonGroup(self.getNode("UI/Settings/$it")) }
    }

    @OnProcess
    fun process(delta: Double) {
        if (!loading.visible) return
        val load = ResourceLoader.loadThreadedGetStatusWithProgress(TpsScenes.LEVEL)
        when (val status = load.status) {
            ResourceLoader.THREAD_LOAD_IN_PROGRESS -> {
                val progress = (load.progress ?: 0.0) * 100.0
                loadingProgress.value = progress
                val bucket = (progress / 10.0).toInt()
                if (status != lastLoggedLoadStatus || bucket != lastLoggedProgressBucket) {
                    GD.print("TPS load progress: ${progress.toInt()}%")
                    lastLoggedLoadStatus = status
                    lastLoggedProgressBucket = bucket
                }
            }
            ResourceLoader.THREAD_LOAD_LOADED -> {
                loadingProgress.value = 100.0
                if (status != lastLoggedLoadStatus) {
                    GD.print("TPS load complete: ${TpsScenes.LEVEL}; waiting for loading timer")
                    lastLoggedLoadStatus = status
                }
                self.setProcess(false)
                loadingDoneTimer.start()
            }
            ResourceLoader.THREAD_LOAD_FAILED, ResourceLoader.THREAD_LOAD_INVALID_RESOURCE -> {
                GD.pushError("TPS load failed: ${TpsScenes.LEVEL}; status=$status")
                main.show()
                loading.hide()
            }
        }
    }

    private fun registerButtons() {
        listOf(
            "DisplayMode/Windowed", "DisplayMode/Fullscreen", "DisplayMode/ExclusiveFullscreen",
            "VSync/Disabled", "VSync/Enabled", "VSync/Adaptive", "VSync/Mailbox",
            "MaxFPS/30", "MaxFPS/40", "MaxFPS/60", "MaxFPS/72", "MaxFPS/90", "MaxFPS/120", "MaxFPS/144", "MaxFPS/Unlimited",
            "ResolutionScale/UltraPerformance", "ResolutionScale/Performance", "ResolutionScale/Balanced", "ResolutionScale/Quality", "ResolutionScale/UltraQuality", "ResolutionScale/Native",
            "ScaleFilter/Bilinear", "ScaleFilter/FSR1", "ScaleFilter/MetalFXSpatial", "ScaleFilter/FSR2", "ScaleFilter/MetalFXTemporal",
            "TAA/Disabled", "TAA/Enabled",
            "MSAA/Disabled", "MSAA/2X", "MSAA/4X", "MSAA/8X",
            "FXAA/Disabled", "FXAA/Enabled",
            "ShadowMapping/Disabled", "ShadowMapping/Enabled",
            "GIType/LightmapGI", "GIType/VoxelGI", "GIType/SDFGI",
            "GIQuality/Disabled", "GIQuality/Low", "GIQuality/High",
            "SSAO/Disabled", "SSAO/Medium", "SSAO/High",
            "SSIL/Disabled", "SSIL/Medium", "SSIL/High",
            "Bloom/Disabled", "Bloom/Enabled",
            "VolumetricFog/Disabled", "VolumetricFog/Enabled",
        ).forEach { buttons[it] = self.requireAs("UI/Settings/$it", ::Button) }
    }

    private fun makeButtonGroup(commonParent: Node?) {
        val group = TpsFactory.buttonGroup()
        commonParent?.getChildren()?.forEach { child ->
            if (child.isClass("BaseButton")) {
                BaseButton(child.handle).buttonGroup = group
            }
        }
    }

    private fun button(path: String): Button? = buttons[path]
    private fun pressed(path: String): Boolean = button(path)?.buttonPressed == true
    private fun setPressed(path: String, pressed: Boolean) {
        button(path)?.buttonPressed = pressed
    }

    private fun setOneLong(prefix: String, values: List<Pair<String, Long>>, value: Long) {
        values.forEach { (name, expected) -> setPressed("$prefix/$name", expected == value) }
    }

    private fun setOneBool(prefix: String, value: Boolean) {
        setPressed("$prefix/Disabled", !value)
        setPressed("$prefix/Enabled", value)
    }

    private fun approximately(value: Double, expected: Double): Boolean = Mathf.abs(value - expected) < 0.0001

    @RegisterFunction("_on_loading_done_timer_timeout")
    fun onLoadingDoneTimerTimeout() {
        GD.print("TPS loading timer fired")
        if (levelSceneChangeStarted) {
            GD.print("TPS scene change already started; ignoring duplicate loading timer")
            return
        }
        levelSceneChangeStarted = true
        self.getMultiplayer()?.multiplayerPeer = peer
        val scene = ResourceLoader.loadThreadedGetPackedScene(TpsScenes.LEVEL)
            ?: ResourceLoader.loadPackedScene(TpsScenes.LEVEL)
            ?: run {
                GD.pushError("TPS load failed: loaded level resource was not a PackedScene: ${TpsScenes.LEVEL}")
                main.show()
                loading.hide()
                return
            }
        GD.print("TPS calling parent replace_main_scene for ${TpsScenes.LEVEL}")
        self.getParent()?.callDeferred("replace_main_scene", scene)
    }

    @RegisterFunction("_on_play_pressed")
    fun onPlayPressed() {
        main.hide()
        loading.show()
        loadingProgress.value = 0.0
        levelSceneChangeStarted = false
        lastLoggedLoadStatus = null
        lastLoggedProgressBucket = -1
        GD.print("TPS load request started: ${TpsScenes.LEVEL}")
        self.setProcess(true)
        ResourceLoader.loadThreadedRequest(TpsScenes.LEVEL, "", true)
    }

    @RegisterFunction("_on_settings_pressed")
    fun onSettingsPressed() {
        main.hide()
        settingsMenu.show()
        settingsActionCancel.grabFocus()

        val displayMode = TpsSettings.videoLong("display_mode")
        setPressed("DisplayMode/Windowed", displayMode == Window.MODE_WINDOWED || displayMode == Window.MODE_MAXIMIZED)
        setPressed("DisplayMode/Fullscreen", displayMode == Window.MODE_FULLSCREEN)
        setPressed("DisplayMode/ExclusiveFullscreen", displayMode == Window.MODE_EXCLUSIVE_FULLSCREEN)

        setOneLong("VSync", listOf("Disabled" to DisplayServer.VSYNC_DISABLED, "Enabled" to DisplayServer.VSYNC_ENABLED, "Adaptive" to DisplayServer.VSYNC_ADAPTIVE, "Mailbox" to DisplayServer.VSYNC_MAILBOX), TpsSettings.videoLong("vsync"))
        setOneLong("MaxFPS", listOf("30" to 30L, "40" to 40L, "60" to 60L, "72" to 72L, "90" to 90L, "120" to 120L, "144" to 144L, "Unlimited" to 0L), TpsSettings.videoLong("max_fps"))

        val scale = TpsSettings.videoDouble("resolution_scale")
        setPressed("ResolutionScale/UltraPerformance", approximately(scale, 1.0 / 3.0))
        setPressed("ResolutionScale/Performance", approximately(scale, 1.0 / 2.0))
        setPressed("ResolutionScale/Balanced", approximately(scale, 1.0 / 1.7))
        setPressed("ResolutionScale/Quality", approximately(scale, 1.0 / 1.5))
        setPressed("ResolutionScale/UltraQuality", approximately(scale, 1.0 / 1.3))
        setPressed("ResolutionScale/Native", approximately(scale, 1.0))

        val scaleFilters = listOf("Bilinear" to Viewport.SCALING_3D_MODE_BILINEAR, "FSR1" to Viewport.SCALING_3D_MODE_FSR, "MetalFXSpatial" to Viewport.SCALING_3D_MODE_METALFX_SPATIAL, "FSR2" to Viewport.SCALING_3D_MODE_FSR2, "MetalFXTemporal" to Viewport.SCALING_3D_MODE_METALFX_TEMPORAL)
        val scaleFilter = TpsSettings.videoLong("scale_filter")
        if (scaleFilters.any { (_, value) -> value == scaleFilter }) {
            setOneLong("ScaleFilter", scaleFilters, scaleFilter)
        } else {
            setPressed(if (metalFxSupported) "ScaleFilter/MetalFXTemporal" else "ScaleFilter/FSR2", true)
        }
        setOneLong("GIType", listOf("LightmapGI" to TpsSettings.LIGHTMAP_GI, "VoxelGI" to TpsSettings.VOXEL_GI, "SDFGI" to TpsSettings.SDFGI), TpsSettings.renderLong("gi_type"))
        setOneLong("GIQuality", listOf("Disabled" to TpsSettings.GI_DISABLED, "Low" to TpsSettings.GI_LOW, "High" to TpsSettings.GI_HIGH), TpsSettings.renderLong("gi_quality"))
        setOneBool("TAA", TpsSettings.renderBool("taa"))
        setOneLong("MSAA", listOf("Disabled" to Viewport.MSAA_DISABLED, "2X" to Viewport.MSAA_2X, "4X" to Viewport.MSAA_4X, "8X" to Viewport.MSAA_8X), TpsSettings.renderLong("msaa"))
        setOneBool("FXAA", TpsSettings.renderBool("fxaa"))
        setOneBool("ShadowMapping", TpsSettings.renderBool("shadow_mapping"))
        setOneLong("SSAO", listOf("Disabled" to -1L, "Medium" to RenderingServer.ENV_SSAO_QUALITY_MEDIUM, "High" to RenderingServer.ENV_SSAO_QUALITY_HIGH), TpsSettings.renderLong("ssao_quality"))
        setOneLong("SSIL", listOf("Disabled" to -1L, "Medium" to RenderingServer.ENV_SSIL_QUALITY_MEDIUM, "High" to RenderingServer.ENV_SSIL_QUALITY_HIGH), TpsSettings.renderLong("ssil_quality"))
        setOneBool("Bloom", TpsSettings.renderBool("bloom"))
        setOneBool("VolumetricFog", TpsSettings.renderBool("volumetric_fog"))
    }

    @RegisterFunction("_on_quit_pressed")
    fun onQuitPressed() {
        self.getTree().quit()
    }

    @RegisterFunction("_on_apply_pressed")
    fun onApplyPressed() {
        main.show()
        playButton.grabFocus()
        settingsMenu.hide()

        val config = TpsSettings.configFile
        when {
            pressed("DisplayMode/Windowed") -> config.setValue("video", "display_mode", Window.MODE_WINDOWED)
            pressed("DisplayMode/Fullscreen") -> config.setValue("video", "display_mode", Window.MODE_FULLSCREEN)
            pressed("DisplayMode/ExclusiveFullscreen") -> config.setValue("video", "display_mode", Window.MODE_EXCLUSIVE_FULLSCREEN)
        }
        firstPressedValue("VSync", listOf("Disabled" to DisplayServer.VSYNC_DISABLED, "Enabled" to DisplayServer.VSYNC_ENABLED, "Adaptive" to DisplayServer.VSYNC_ADAPTIVE, "Mailbox" to DisplayServer.VSYNC_MAILBOX))?.let { config.setValue("video", "vsync", it) }
        firstPressedValue("MaxFPS", listOf("30" to 30L, "40" to 40L, "60" to 60L, "72" to 72L, "90" to 90L, "120" to 120L, "144" to 144L, "Unlimited" to 0L))?.let { config.setValue("video", "max_fps", it) }
        firstPressedValue("ResolutionScale", listOf("UltraPerformance" to 1.0 / 3.0, "Performance" to 1.0 / 2.0, "Balanced" to 1.0 / 1.7, "Quality" to 1.0 / 1.5, "UltraQuality" to 1.0 / 1.3, "Native" to 1.0))?.let { config.setValue("video", "resolution_scale", it) }
        firstPressedValue("ScaleFilter", listOf("Bilinear" to Viewport.SCALING_3D_MODE_BILINEAR, "FSR1" to Viewport.SCALING_3D_MODE_FSR, "MetalFXSpatial" to Viewport.SCALING_3D_MODE_METALFX_SPATIAL, "FSR2" to Viewport.SCALING_3D_MODE_FSR2, "MetalFXTemporal" to Viewport.SCALING_3D_MODE_METALFX_TEMPORAL))?.let { config.setValue("video", "scale_filter", it) }
        firstPressedValue("GIType", listOf("LightmapGI" to TpsSettings.LIGHTMAP_GI, "VoxelGI" to TpsSettings.VOXEL_GI, "SDFGI" to TpsSettings.SDFGI))?.let { config.setValue("rendering", "gi_type", it) }
        firstPressedValue("GIQuality", listOf("Disabled" to TpsSettings.GI_DISABLED, "Low" to TpsSettings.GI_LOW, "High" to TpsSettings.GI_HIGH))?.let { config.setValue("rendering", "gi_quality", it) }
        config.setValue("rendering", "taa", pressed("TAA/Enabled"))
        firstPressedValue("MSAA", listOf("Disabled" to Viewport.MSAA_DISABLED, "2X" to Viewport.MSAA_2X, "4X" to Viewport.MSAA_4X, "8X" to Viewport.MSAA_8X))?.let { config.setValue("rendering", "msaa", it) }
        config.setValue("rendering", "fxaa", pressed("FXAA/Enabled"))
        config.setValue("rendering", "shadow_mapping", pressed("ShadowMapping/Enabled"))
        firstPressedValue("SSAO", listOf("Disabled" to -1L, "Medium" to RenderingServer.ENV_SSAO_QUALITY_MEDIUM, "High" to RenderingServer.ENV_SSAO_QUALITY_HIGH))?.let { config.setValue("rendering", "ssao_quality", it) }
        firstPressedValue("SSIL", listOf("Disabled" to -1L, "Medium" to RenderingServer.ENV_SSIL_QUALITY_MEDIUM, "High" to RenderingServer.ENV_SSIL_QUALITY_HIGH))?.let { config.setValue("rendering", "ssil_quality", it) }
        config.setValue("rendering", "bloom", pressed("Bloom/Enabled"))
        config.setValue("rendering", "volumetric_fog", pressed("VolumetricFog/Enabled"))

        TpsSettings.applyGraphicsSettings(self.getWindow(), worldEnvironment.environment, self)
        TpsSettings.saveSettings()
    }

    private fun <T> firstPressedValue(prefix: String, values: List<Pair<String, T>>): T? =
        values.firstOrNull { (name, _) -> pressed("$prefix/$name") }?.second

    @RegisterFunction("_on_cancel_pressed")
    fun onCancelPressed() {
        main.show()
        playButton.grabFocus()
        settingsMenu.hide()
        online.hide()
    }

    @RegisterFunction("_on_play_online_pressed")
    fun onPlayOnlinePressed() {
        online.show()
        main.hide()
    }

    @RegisterFunction("_on_host_pressed")
    fun onHostPressed() {
        val nextPeer = TpsFactory.enetMultiplayerPeer()
        nextPeer.createServer(onlinePort.value.toInt())
        peer = nextPeer
        onPlayPressed()
        online.hide()
    }

    @RegisterFunction("_on_connect_pressed")
    fun onConnectPressed() {
        val nextPeer: ENetMultiplayerPeer = TpsFactory.enetMultiplayerPeer()
        nextPeer.createClient(onlineAddress.text, onlinePort.value.toInt())
        peer = nextPeer
        onPlayPressed()
        online.hide()
    }

    @Signal("replace_main_scene")
    fun replaceMainScene(scene: PackedScene) = Unit
}
