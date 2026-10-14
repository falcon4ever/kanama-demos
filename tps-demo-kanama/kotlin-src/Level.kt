package tps

import kotlinx.coroutines.launch
import net.multigesture.kanama.annotations.GodotName
import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEvent
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.LightmapGI
import net.multigesture.kanama.api.Marker3D
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.RenderingServer
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.WorldEnvironment
import net.multigesture.kanama.generated.Autoloads
import net.multigesture.kanama.generated.RedRobotSignals

@ScriptClass(attachTo = "Node3D")
class Level(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {

    private var lightmapGi: LightmapGI? = null
    private lateinit var worldEnvironment: WorldEnvironment
    private lateinit var robotSpawnPoints: Node3D
    private lateinit var playerSpawnPoints: Node3D
    private lateinit var spawnedNodes: Node3D
    private var exiting = false

    @OnReady
    fun ready() {
        GD.print("TPS Level ready: start")
        worldEnvironment = self.requireAs("WorldEnvironment", ::WorldEnvironment)
        robotSpawnPoints = self.requireAs("RobotSpawnpoints", ::Node3D)
        playerSpawnPoints = self.requireAs("PlayerSpawnpoints", ::Node3D)
        spawnedNodes = self.requireAs("SpawnedNodes", ::Node3D)
        GD.print("TPS Level ready: nodes resolved")

        Autoloads.Settings.applyGraphicsSettings(self.getWindow(), worldEnvironment.environment, self)
        GD.print("TPS Level ready: graphics settings applied")
        when (Autoloads.Settings.renderLong("gi_type")) {
            Settings.SDFGI -> setupSdfgi()
            Settings.VOXEL_GI -> setupVoxelgi()
            else -> setupLightmapgi()
        }
        GD.print("TPS Level ready: GI configured")

        if (self.isMultiplayerServer()) {
            GD.print("TPS Level ready: server spawning robots")
            for (child in robotSpawnPoints.getChildren()) {
                spawnRobot(Node3D(child.handle))
            }
            val spawnPoints = playerSpawnPoints.getChildren().map { Marker3D(it.handle) }.shuffled()
            GD.print("TPS Level ready: adding local player")
            addPlayer(1, spawnPoints.firstOrNull())
            for ((index, id) in self.multiplayerPeers().withIndex()) {
                addPlayer(id.toLong(), spawnPoints.getOrNull(index + 1))
            }
            self.withMultiplayer { api ->
                api.peerConnected.connect { id -> addPlayer(id, null) }
                api.peerDisconnected.connect { id -> delPlayer(id) }
            }
        }
        GD.print("TPS Level ready: complete")
    }

    private fun setupSdfgi() {
        GD.print("TPS Level GI: SDFGI")
        worldEnvironment.environment?.sdfgiEnabled = true
        self.requireAs("VoxelGI", ::Node3D).hide()
        self.requireAs("ReflectionProbes", ::Node3D).hide()
        lightmapGi?.queueFree()
        when (Autoloads.Settings.renderLong("gi_quality")) {
            Settings.GI_HIGH -> RenderingServer.environmentSetSdfgiRayCount(RenderingServer.EnvironmentSDFGIRayCount.COUNT_96)
            Settings.GI_LOW -> RenderingServer.environmentSetSdfgiRayCount(RenderingServer.EnvironmentSDFGIRayCount.COUNT_32)
            else -> worldEnvironment.environment?.sdfgiEnabled = false
        }
    }

    private fun setupVoxelgi() {
        GD.print("TPS Level GI: VoxelGI")
        worldEnvironment.environment?.sdfgiEnabled = false
        self.requireAs("VoxelGI", ::Node3D).show()
        self.requireAs("ReflectionProbes", ::Node3D).hide()
        lightmapGi?.queueFree()
        when (Autoloads.Settings.renderLong("gi_quality")) {
            Settings.GI_HIGH -> RenderingServer.voxelGiSetQuality(RenderingServer.VoxelGIQuality.HIGH)
            Settings.GI_LOW -> RenderingServer.voxelGiSetQuality(RenderingServer.VoxelGIQuality.LOW)
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
        if (Autoloads.Settings.renderLong("gi_quality") == Settings.GI_DISABLED) {
            lightmapGi?.hide()
            self.requireAs("ReflectionProbes", ::Node3D).hide()
        }
    }

    fun spawnRobot(spawnPoint: Node3D) {
        if (exiting || self.isQueuedForDeletion() || !self.isInsideTree()) return
        GD.print("TPS Level spawnRobot: ${spawnPoint.getName()}")
        val robotNode = TpsScenes.instantiate(TpsScenes.RED_ROBOT) ?: return
        val robot = Node3D(robotNode.handle)
        robot.transform = spawnPoint.transform
        RedRobotSignals.exploded(robot).connect { respawnRobot(spawnPoint) }
        spawnedNodes.addChild(robotNode, true)
    }

    @GodotName("_respawn_robot")
    fun respawnRobot(spawnPoint: Node3D) {
        launch {
            wait(15.0)
            if (exiting || self.isQueuedForDeletion() || !self.isInsideTree()) return@launch
            spawnRobot(spawnPoint)
        }
    }

    fun delPlayer(id: Long) {
        val name = id.toString()
        if (spawnedNodes.hasNode(name)) {
            spawnedNodes.getNode(name)?.queueFree()
        }
    }

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
    fun input(inputEvent: InputEvent) {
        val event = inputEvent
        if (event.isActionPressed("quit")) {
            exiting = true
            cancelCoroutines()
            Input.setMouseMode(Input.MouseMode.VISIBLE)
            self.emitSignal("quit")
        }
    }

    @OnExitTree
    fun exitTree() {
        exiting = true
        cancelCoroutines()
    }

    @Signal
    fun quit() = Unit
}
