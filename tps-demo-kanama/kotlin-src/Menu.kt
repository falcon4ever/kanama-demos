package tps

import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.Rpc
import net.multigesture.kanama.annotations.RpcMode
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.BaseButton
import net.multigesture.kanama.api.Button
import net.multigesture.kanama.api.Control
import net.multigesture.kanama.api.DisplayServer
import net.multigesture.kanama.api.ENetMultiplayerPeer
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.IP
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.LineEdit
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.MultiplayerAPI
import net.multigesture.kanama.api.MultiplayerPeer
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.ProgressBar
import net.multigesture.kanama.api.RenderingServer
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.SpinBox
import net.multigesture.kanama.api.Timer
import net.multigesture.kanama.api.Viewport
import net.multigesture.kanama.api.Window
import net.multigesture.kanama.api.WorldEnvironment
import net.multigesture.kanama.generated.MenuRpcs
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
    private lateinit var onlineHost: Button
    private lateinit var onlineConnect: Button
    private lateinit var onlineStatus: Label
    private lateinit var settingsMenu: Control
    private lateinit var settingsActionCancel: Button
    private lateinit var loading: Control
    private lateinit var loadingProgress: ProgressBar
    private lateinit var loadingDoneTimer: Timer

    private val buttons = mutableMapOf<String, Button>()
    private var levelSceneChangeStarted = false
    private var lastLoggedLoadStatus: Long? = null
    private var lastLoggedProgressBucket = -1
    private var connectingAsClient = false
    private var joinedLobby = false
    private var hostingLobby = false
    private var lobbyStarting = false
    private var autoStartHostedGame = false
    private var autoStartAfterPeer = false
    private var loadedLevelScene: PackedScene? = null
    private val readyPeers = mutableSetOf<Long>()

    @OnReady
    fun ready() {
        worldEnvironment = self.requireAs("WorldEnvironment", ::WorldEnvironment)
        main = self.requireAs("UI/Main", ::Control)
        playButton = self.requireAs("UI/Main/Play", ::Button)
        online = self.requireAs("UI/Online", ::Control)
        onlinePort = self.requireAs("UI/Online/Port", ::SpinBox)
        onlineAddress = self.requireAs("UI/Online/Address", ::LineEdit)
        onlineHost = self.requireAs("UI/Online/Host", ::Button)
        onlineConnect = self.requireAs("UI/Online/Connect", ::Button)
        onlineStatus = self.requireAs("UI/Online/Status", ::Label)
        settingsMenu = self.requireAs("UI/Settings", ::Control)
        settingsActionCancel = self.requireAs("UI/Settings/Actions/Cancel", ::Button)
        loading = self.requireAs("UI/Loading", ::Control)
        loadingProgress = self.requireAs("UI/Loading/Progress", ::ProgressBar)
        loadingDoneTimer = self.requireAs("UI/Loading/DoneTimer", ::Timer)
        loadingDoneTimer.signal(Timer.Signals.timeout).connect(self, argumentCount = 0) {
            onLoadingDoneTimerTimeout()
        }
        self.getMultiplayer()?.signal(MultiplayerAPI.Signals.connectedToServer)
            ?.connect(self, argumentCount = 0) { onConnectedToServer() }
        self.getMultiplayer()?.signal(MultiplayerAPI.Signals.connectionFailed)
            ?.connect(self, argumentCount = 0) { showConnectionFailure("Could not reach the host. Check the address, port, and Wi-Fi network.") }
        self.getMultiplayer()?.signal(MultiplayerAPI.Signals.serverDisconnected)
            ?.connect(self, argumentCount = 0) {
                if (joinedLobby) showConnectionFailure("The host disconnected.")
            }
        self.getMultiplayer()?.signal(MultiplayerAPI.Signals.peerConnected)
            ?.connect(self, argumentCount = 1) { args -> onLobbyPeerConnected((args.firstOrNull() as Number).toLong()) }
        self.getMultiplayer()?.signal(MultiplayerAPI.Signals.peerDisconnected)
            ?.connect(self, argumentCount = 1) { args -> onLobbyPeerDisconnected((args.firstOrNull() as Number).toLong()) }

        registerButtons()
        SafeArea.applyInsets(self.requireAs("UI", ::Control))
        if (isMobile() && onlineAddress.text == "127.0.0.1") {
            onlineAddress.text = ""
        }
        TpsSettings.applyGraphicsSettings(self.getWindow(), worldEnvironment.environment, self)

        System.getenv("KANAMA_TPS_SMOKE_PORT")?.toDoubleOrNull()?.let { onlinePort.value = it }
        val smokeJoinAddress = System.getenv("KANAMA_TPS_SMOKE_JOIN_ADDRESS")
        if (!smokeJoinAddress.isNullOrBlank()) {
            onlineAddress.text = smokeJoinAddress
            self.callDeferred("_on_connect_pressed")
        } else if (DisplayServer.getName() == "headless" || System.getenv("KANAMA_TPS_SMOKE_AUTOSTART") == "1") {
            autoStartHostedGame = true
            autoStartAfterPeer = System.getenv("KANAMA_TPS_SMOKE_WAIT_FOR_PEER") == "1"
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
        // close what you create (Kanama task 61): each button keeps its own reference to the group.
        TpsFactory.buttonGroup().use { group ->
            commonParent?.getChildren()?.forEach { child ->
                if (child.isClass("BaseButton")) {
                    BaseButton(child.handle).buttonGroup = group
                }
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
        if (loadedLevelScene != null) {
            GD.print("TPS level already reported ready; ignoring duplicate loading timer")
            return
        }
        val scene = ResourceLoader.loadThreadedGetPackedScene(TpsScenes.LEVEL)
            ?: ResourceLoader.loadPackedScene(TpsScenes.LEVEL)
            ?: run {
                GD.pushError("TPS load failed: loaded level resource was not a PackedScene: ${TpsScenes.LEVEL}")
                main.show()
                loading.hide()
                return
            }
        loadedLevelScene = scene
        if (!joinedLobby) {
            enterLoadedLevel()
        } else if (self.getMultiplayer()?.isServer() == true) {
            readyPeers += 1L
            enterLobbyWhenReady()
        } else {
            GD.print("TPS lobby client ready")
            MenuRpcs.rpcIdReadyForGame(this, 1L)
        }
    }

    @RegisterFunction("_on_play_pressed")
    fun onPlayPressed() {
        main.hide()
        loading.show()
        loadingProgress.value = 0.0
        levelSceneChangeStarted = false
        loadedLevelScene = null
        lastLoggedLoadStatus = null
        lastLoggedProgressBucket = -1
        GD.print("TPS load request started: ${TpsScenes.LEVEL}")
        self.setProcess(true)
        ResourceLoader.loadThreadedRequest(TpsScenes.LEVEL, "", false)
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
        if (online.visible) resetOnlinePeer()
        main.show()
        playButton.grabFocus()
        settingsMenu.hide()
        online.hide()
    }

    @RegisterFunction("_on_play_online_pressed")
    fun onPlayOnlinePressed() {
        resetOnlinePeer()
        setOnlineBusy(false)
        onlineStatus.text = lobbyInstructions()
        online.show()
        main.hide()
    }

    @RegisterFunction("_on_host_pressed")
    fun onHostPressed() {
        if (hostingLobby) {
            if (!lobbyStarting) {
                lobbyStarting = true
                readyPeers.clear()
                onlineHost.disabled = true
                onlineStatus.text = "Starting when every player finishes loading..."
                GD.print("TPS lobby start requested peers=${self.getMultiplayer()?.getPeers()?.size ?: 0}")
                self.getMultiplayer()?.getPeers()?.forEach { MenuRpcs.rpcIdPrepareGame(this, it.toLong()) }
                prepareGame()
            }
            return
        }
        val nextPeer = TpsFactory.enetMultiplayerPeer()
        val port = onlinePort.value.toInt()
        val error = nextPeer.createServer(port)
        if (error != 0L) {
            nextPeer.closeConnection()
            nextPeer.close() // discard the created-but-unused peer (task 61)
            onlineStatus.text = "Could not host on port $port (Error $error). Try another port."
            return
        }
        replacePeer(nextPeer)
        self.getMultiplayer()?.multiplayerPeer = peer
        connectingAsClient = false
        joinedLobby = true
        hostingLobby = true
        onlineConnect.disabled = true
        onlineAddress.editable = false
        onlineHost.text = "START GAME"
        onlineStatus.text = "Hosting on ${preferredLanAddress() ?: "this device"}:$port. Waiting for players; tap Start Game when ready."
        GD.print("TPS lobby hosting port=$port")
        if (autoStartHostedGame && !autoStartAfterPeer) self.callDeferred("_on_host_pressed")
    }

    @RegisterFunction("_on_connect_pressed")
    fun onConnectPressed() {
        val address = onlineAddress.text.trim()
        val port = onlinePort.value.toInt()
        if (address.isEmpty()) {
            onlineStatus.text = "Enter the host device's LAN address first."
            onlineAddress.grabFocus()
            return
        }
        val nextPeer: ENetMultiplayerPeer = TpsFactory.enetMultiplayerPeer()
        val error = nextPeer.createClient(address, port)
        if (error != 0L) {
            nextPeer.closeConnection()
            nextPeer.close() // discard the created-but-unused peer (task 61)
            onlineStatus.text = "Could not start a connection to $address:$port (Error $error)."
            return
        }
        replacePeer(nextPeer)
        connectingAsClient = true
        joinedLobby = true
        setOnlineBusy(true)
        onlineStatus.text = "Connecting to $address:$port..."
        GD.print("TPS lobby connecting address=$address port=$port")
        self.getMultiplayer()?.multiplayerPeer = peer
    }

    private fun onConnectedToServer() {
        if (!connectingAsClient) return
        connectingAsClient = false
        onlineStatus.text = "Connected. Waiting for the host to start the game..."
        GD.print("TPS lobby connected")
    }

    private fun showConnectionFailure(message: String) {
        if (!joinedLobby) return
        GD.print("TPS lobby connection failed: $message")
        resetOnlinePeer()
        self.setProcess(false)
        loadingDoneTimer.stop()
        loading.hide()
        online.show()
        main.hide()
        onlineStatus.text = message
        setOnlineBusy(false)
    }

    // close what you create (Kanama task 61): TpsFactory.*MultiplayerPeer() returns an owned
    // wrapper. Release the previous peer's owning reference when swapping it out; the engine keeps
    // its own reference via multiplayerPeer until that is reassigned, so this never frees a live peer.
    private fun replacePeer(next: MultiplayerPeer) {
        if (next !== peer) peer.close()
        peer = next
    }

    private fun resetOnlinePeer() {
        peer.closeConnection()
        replacePeer(TpsFactory.offlineMultiplayerPeer())
        self.getMultiplayer()?.multiplayerPeer = peer
        connectingAsClient = false
        joinedLobby = false
        hostingLobby = false
        lobbyStarting = false
        loadedLevelScene = null
        readyPeers.clear()
        onlineHost.text = "HOST GAME"
        setOnlineBusy(false)
    }

    private fun onLobbyPeerConnected(id: Long) {
        if (!hostingLobby) return
        GD.print("TPS lobby peer connected id=$id")
        if (autoStartHostedGame && autoStartAfterPeer && !lobbyStarting) {
            self.callDeferred("_on_host_pressed")
        }
        if (lobbyStarting) {
            MenuRpcs.rpcIdPrepareGame(this, id)
        } else {
            val players = (self.getMultiplayer()?.getPeers()?.size ?: 0) + 1
            onlineStatus.text = "$players players connected. Share ${preferredLanAddress() ?: "this device"}:${onlinePort.value.toInt()}, or tap Start Game."
        }
    }

    private fun onLobbyPeerDisconnected(id: Long) {
        if (!hostingLobby) return
        GD.print("TPS lobby peer disconnected id=$id")
        readyPeers -= id
        if (lobbyStarting) enterLobbyWhenReady()
    }

    @RegisterFunction("prepare_game")
    @Rpc
    fun prepareGame() {
        joinedLobby = true
        lobbyStarting = true
        readyPeers.clear()
        online.hide()
        GD.print("TPS lobby preparing game")
        onPlayPressed()
    }

    @RegisterFunction("ready_for_game")
    @Rpc(mode = RpcMode.ANY_PEER)
    fun readyForGame() {
        if (!hostingLobby || !lobbyStarting) return
        val sender = self.getMultiplayer()?.getRemoteSenderId()?.toLong() ?: 0L
        if (sender <= 0L) return
        readyPeers += sender
        GD.print("TPS lobby peer ready id=$sender")
        enterLobbyWhenReady()
    }

    private fun enterLobbyWhenReady() {
        if (!hostingLobby || loadedLevelScene == null) return
        val expected = buildSet {
            add(1L)
            self.getMultiplayer()?.getPeers()?.forEach { add(it.toLong()) }
        }
        if (!readyPeers.containsAll(expected)) {
            GD.print("TPS lobby waiting ready=${readyPeers.size}/${expected.size}")
            return
        }
        GD.print("TPS lobby all players ready count=${expected.size}")
        self.getMultiplayer()?.getPeers()?.forEach { MenuRpcs.rpcIdEnterGame(this, it.toLong()) }
        enterGame()
    }

    @RegisterFunction("enter_game")
    @Rpc
    fun enterGame() {
        enterLoadedLevel()
    }

    private fun enterLoadedLevel() {
        if (levelSceneChangeStarted) return
        val scene = loadedLevelScene ?: return
        levelSceneChangeStarted = true
        self.getMultiplayer()?.multiplayerPeer = peer
        GD.print("TPS calling parent replace_main_scene for ${TpsScenes.LEVEL}")
        self.getParent()?.callDeferred("replace_main_scene", scene)
    }

    private fun setOnlineBusy(busy: Boolean) {
        onlineHost.disabled = busy
        onlineConnect.disabled = busy
        onlineAddress.editable = !busy
    }

    private fun lobbyInstructions(): String {
        val host = preferredLanAddress()
        return buildString {
            append("HOST: tap Host, then share ")
            append(if (host != null) "$host:${onlinePort.value.toInt()}" else "this device's Wi-Fi address")
            append(".\nJOIN: enter that address on a device using the same Wi-Fi network.")
        }
    }

    private fun preferredLanAddress(): String? =
        IP.getLocalAddresses().firstOrNull(::isPrivateIpv4Address)

    private fun isPrivateIpv4Address(address: String): Boolean {
        val octets = address.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }

    private fun isMobile(): Boolean = OS.getName() == "iOS" || OS.getName() == "Android"

    @Signal("replace_main_scene")
    fun replaceMainScene(scene: PackedScene) = Unit
}
