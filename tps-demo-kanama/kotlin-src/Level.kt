package tps

import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.CollisionShape3D
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEvent
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.LightmapGI
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.api.Marker3D
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.RenderingServer
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.Timer
import net.multigesture.kanama.api.WorldEnvironment
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.generated.RedRobotNames
import java.io.File
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "Node3D")
class Level(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()

    private var lightmapGi: LightmapGI? = null
    private lateinit var worldEnvironment: WorldEnvironment
    private lateinit var robotSpawnPoints: Node3D
    private lateinit var playerSpawnPoints: Node3D
    private lateinit var spawnedNodes: Node3D
    private var exiting = false

    @OnReady
    fun ready() {
        GD.print("TPS Level ready: start")
        val shouldQuitAfterReady = observeSmokeLevelReady()
        worldEnvironment = self.requireAs("WorldEnvironment", ::WorldEnvironment)
        robotSpawnPoints = self.requireAs("RobotSpawnpoints", ::Node3D)
        playerSpawnPoints = self.requireAs("PlayerSpawnpoints", ::Node3D)
        spawnedNodes = self.requireAs("SpawnedNodes", ::Node3D)
        GD.print("TPS Level ready: nodes resolved")

        TpsSettings.applyGraphicsSettings(self.getWindow(), worldEnvironment.environment, self)
        GD.print("TPS Level ready: graphics settings applied")
        when (TpsSettings.renderLong("gi_type")) {
            TpsSettings.SDFGI -> setupSdfgi()
            TpsSettings.VOXEL_GI -> setupVoxelgi()
            else -> setupLightmapgi()
        }
        GD.print("TPS Level ready: GI configured")

        if (self.getMultiplayer()?.isServer() == true) {
            GD.print("TPS Level ready: server spawning robots")
            for (child in robotSpawnPoints.getChildren()) {
                spawnRobot(Node3D(child.handle))
            }
            val spawnPoints = playerSpawnPoints.getChildren().map { Marker3D(it.handle) }.shuffled()
            GD.print("TPS Level ready: adding local player")
            addPlayer(1, spawnPoints.firstOrNull())
            for ((index, id) in (self.getMultiplayer()?.getPeers() ?: emptyList()).withIndex()) {
                addPlayer(id.toLong(), spawnPoints.getOrNull(index + 1))
            }
            self.getMultiplayer()?.signal(net.multigesture.kanama.api.MultiplayerAPI.Signals.peerConnected)
                ?.connect(self, argumentCount = 1) { args -> addPlayer((args.firstOrNull() as Number).toLong(), null) }
            self.getMultiplayer()?.signal(net.multigesture.kanama.api.MultiplayerAPI.Signals.peerDisconnected)
                ?.connect(self, argumentCount = 1) { args -> delPlayer((args.firstOrNull() as Number).toLong()) }
            if (!shouldQuitAfterReady) {
                runSmokeRobotDeathCheckIfRequested()
            }
        }
        GD.print("TPS Level ready: complete")
        if (shouldQuitAfterReady) {
            kanamaScope.launch {
                self.getTree().createTimer(5.0)?.signal(Timer.Signals.timeout)?.await(self, argumentCount = 0)
                if (!self.isQueuedForDeletion() && self.isInsideTree()) {
                    GD.print("TPS smoke second level ready; quitting")
                    self.getTree().quit()
                }
            }
        }
    }

    private fun observeSmokeLevelReady(): Boolean {
        if (System.getenv("KANAMA_TPS_SMOKE_QUIT_AFTER_SECOND_LEVEL_READY") != "1") return false
        val markerPath = System.getenv("KANAMA_TPS_SMOKE_RELOAD_MARKER")
        val readyCount = if (markerPath.isNullOrBlank()) {
            smokeLevelReadyCount += 1
            smokeLevelReadyCount
        } else {
            val marker = File(markerPath)
            marker.parentFile?.mkdirs()
            val nextCount = (marker.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: 0) + 1
            marker.writeText(nextCount.toString())
            nextCount
        }
        return readyCount >= 2
    }

    private fun runSmokeRobotDeathCheckIfRequested() {
        if (System.getenv("KANAMA_TPS_SMOKE_KILL_ROBOT") != "1") return
        val robotCount = System.getenv("KANAMA_TPS_SMOKE_KILL_ROBOTS")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val delayBetweenKills = System.getenv("KANAMA_TPS_SMOKE_KILL_ROBOT_DELAY")?.toDoubleOrNull() ?: 3.0
        val useRealBullets = System.getenv("KANAMA_TPS_SMOKE_REAL_BULLETS") == "1"
        kanamaScope.launch {
            repeat(robotCount) { index ->
                if (index > 0) {
                    self.getTree().createTimer(delayBetweenKills)?.signal(Timer.Signals.timeout)?.await(self, argumentCount = 0)
                }
                if (exiting || self.isQueuedForDeletion() || !self.isInsideTree()) return@launch
                MainThread.postNextFrame {
                    if (exiting || self.isQueuedForDeletion() || !self.isInsideTree()) return@postNextFrame
                    val robot = spawnedNodes.getChildren()
                        .map { Node3D(it.handle) }
                        .firstOrNull {
                            !it.isQueuedForDeletion() &&
                                it.isInsideTree() &&
                                it.kotlinScriptInstance<RedRobot>()?.dead != true
                        }
                    if (robot == null) {
                        GD.print("TPS smoke robot death skipped: no live robot found for kill ${index + 1}")
                        return@postNextFrame
                    }
                    if (useRealBullets) {
                        kanamaScope.launch {
                            repeat(5) { shot ->
                                if (exiting || robot.isQueuedForDeletion() || !robot.isInsideTree()) return@launch
                                MainThread.postNextFrame {
                                    if (!exiting && !robot.isQueuedForDeletion() && robot.isInsideTree()) {
                                        spawnSmokeBullet(robot)
                                    }
                                }
                                if (shot < 4) {
                                    self.getTree().createTimer(0.35)?.signal(Timer.Signals.timeout)?.await(self, argumentCount = 0)
                                }
                            }
                            self.getTree().createTimer(2.0)?.signal(Timer.Signals.timeout)?.await(self, argumentCount = 0)
                            GD.print("TPS smoke robot death complete ${index + 1}/$robotCount")
                        }
                    } else {
                        val script = robot.kotlinScriptInstance<RedRobot>()
                        if (script == null) {
                            GD.print("TPS smoke robot death skipped: robot script not ready for kill ${index + 1}")
                            return@postNextFrame
                        }
                        repeat(5) {
                            script.hit()
                        }
                        GD.print("TPS smoke robot death complete ${index + 1}/$robotCount")
                    }
                }
                if (index == 0 && System.getenv("KANAMA_TPS_SMOKE_RETURN_TO_MENU_AFTER_FIRST_KILL") == "1") {
                    self.getTree().createTimer(1.0)?.signal(Timer.Signals.timeout)?.await(self, argumentCount = 0)
                    if (!exiting && !self.isQueuedForDeletion() && self.isInsideTree()) {
                        GD.print("TPS smoke returning to menu after first kill")
                        self.emitSignal("quit")
                    }
                    return@launch
                }
            }
        }
    }

    private fun spawnSmokeBullet(target: Node3D) {
        val bulletNode = TpsScenes.instantiate(TpsScenes.BULLET) ?: return
        val bullet = CharacterBody3D(bulletNode.handle)
        val targetPosition = target.globalTransform.origin + net.multigesture.kanama.types.Vector3.UP
        val origin = targetPosition + net.multigesture.kanama.types.Vector3(0.0, 0.0, 4.0)
        spawnedNodes.addChild(bulletNode, true)
        bullet.lookAtFromPosition(origin, targetPosition)
    }

    private fun setupSdfgi() {
        GD.print("TPS Level GI: SDFGI")
        worldEnvironment.environment?.sdfgiEnabled = true
        self.requireAs("VoxelGI", ::Node3D).hide()
        self.requireAs("ReflectionProbes", ::Node3D).hide()
        lightmapGi?.queueFree()
        when (TpsSettings.renderLong("gi_quality")) {
            TpsSettings.GI_HIGH -> RenderingServer.environmentSetSdfgiRayCount(RenderingServer.ENV_SDFGI_RAY_COUNT_96)
            TpsSettings.GI_LOW -> RenderingServer.environmentSetSdfgiRayCount(RenderingServer.ENV_SDFGI_RAY_COUNT_32)
            else -> worldEnvironment.environment?.sdfgiEnabled = false
        }
    }

    private fun setupVoxelgi() {
        GD.print("TPS Level GI: VoxelGI")
        worldEnvironment.environment?.sdfgiEnabled = false
        self.requireAs("VoxelGI", ::Node3D).show()
        self.requireAs("ReflectionProbes", ::Node3D).hide()
        lightmapGi?.queueFree()
        when (TpsSettings.renderLong("gi_quality")) {
            TpsSettings.GI_HIGH -> RenderingServer.voxelGiSetQuality(RenderingServer.VOXEL_GI_QUALITY_HIGH)
            TpsSettings.GI_LOW -> RenderingServer.voxelGiSetQuality(RenderingServer.VOXEL_GI_QUALITY_LOW)
            else -> self.requireAs("VoxelGI", ::Node3D).hide()
        }
    }

    private fun setupLightmapgi() {
        GD.print("TPS Level GI: LightmapGI")
        worldEnvironment.environment?.sdfgiEnabled = false
        self.requireAs("VoxelGI", ::Node3D).hide()
        self.requireAs("ReflectionProbes", ::Node3D).show()
        if (lightmapGi == null) {
            val gi = LightmapGI.create()
            gi.setName("LightmapGI")
            gi.lightData = ResourceLoader.loadLightmapGIData("res://level/level.lmbake")
            lightmapGi = gi
            self.addChild(gi)
        }
        if (TpsSettings.renderLong("gi_quality") == TpsSettings.GI_DISABLED) {
            lightmapGi?.hide()
            self.requireAs("ReflectionProbes", ::Node3D).hide()
        }
    }

    @RegisterFunction("spawn_robot")
    fun spawnRobot(spawnPoint: Node3D) {
        if (exiting || self.isQueuedForDeletion() || !self.isInsideTree()) return
        GD.print("TPS Level spawnRobot: ${spawnPoint.getName()}")
        val robotNode = TpsScenes.instantiate(TpsScenes.RED_ROBOT) ?: return
        val robot = Node3D(robotNode.handle)
        robot.transform = spawnPoint.transform
        robot.signal(RedRobotNames.Signals.exploded).connect(self, argumentCount = 0) { respawnRobot(spawnPoint) }
        spawnedNodes.addChild(robotNode, true)
    }

    @RegisterFunction("_respawn_robot")
    fun respawnRobot(spawnPoint: Node3D) {
        kanamaScope.launch {
            self.getTree().createTimer(15.0)?.signal(Timer.Signals.timeout)?.await(self, argumentCount = 0)
            if (exiting || self.isQueuedForDeletion() || !self.isInsideTree()) return@launch
            spawnRobot(spawnPoint)
        }
    }

    @RegisterFunction("del_player")
    fun delPlayer(id: Long) {
        val name = id.toString()
        if (spawnedNodes.hasNode(name)) {
            spawnedNodes.getNode(name)?.queueFree()
        }
    }

    @RegisterFunction("add_player")
    fun addPlayer(id: Long, spawnPoint: Marker3D? = null) {
        GD.print("TPS Level addPlayer: $id")
        val chosen = spawnPoint
            ?: playerSpawnPoints.getChild((net.multigesture.kanama.api.GD.randi() % playerSpawnPoints.getChildCount()).toInt())?.let { Marker3D(it.handle) }
            ?: return
        val playerNode = TpsScenes.instantiate(TpsScenes.PLAYER) ?: return
        val player = Node3D(playerNode.handle)
        playerNode.setName(id.toString())
        playerNode.set("player_id", id)
        player.transform = chosen.transform
        spawnedNodes.addChild(playerNode)
    }

    @OnInput
    fun input(inputEvent: net.multigesture.kanama.api.GodotObject) {
        val event = InputEvent(inputEvent.handle)
        if (event.isActionPressed("quit")) {
            exiting = true
            kanamaScope.cancel()
            Input.setMouseMode(Input.MOUSE_MODE_VISIBLE)
            self.emitSignal("quit")
        }
    }

    @OnExitTree
    fun exitTree() {
        exiting = true
        kanamaScope.cancel()
    }

    @Signal
    fun quit() = Unit

    private companion object {
        var smokeLevelReadyCount = 0
    }
}
