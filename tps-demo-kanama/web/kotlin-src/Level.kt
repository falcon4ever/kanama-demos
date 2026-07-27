package tps

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEvent
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.LightmapGI
import net.multigesture.kanama.api.Marker3D
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.RenderingServer
import net.multigesture.kanama.api.RenderingServerQuality
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.SceneTree
import net.multigesture.kanama.api.WorldEnvironment
import net.multigesture.kanama.api.environmentSetSdfgiRayCount
import net.multigesture.kanama.api.getChildren
import net.multigesture.kanama.api.getMultiplayer
import net.multigesture.kanama.api.getName
import net.multigesture.kanama.api.getWindow
import net.multigesture.kanama.api.isInsideTree
import net.multigesture.kanama.api.isQueuedForDeletion
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.api.loadLightmapGIData
import net.multigesture.kanama.api.sdfgiEnabled
import net.multigesture.kanama.api.setName
import net.multigesture.kanama.api.voxelGiSetQuality
import net.multigesture.kanama.api.show
import net.multigesture.kanama.api.transform
import net.multigesture.kanama.generated.RedRobotNames
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "Node3D")
class Level(godotObject: GodotHandle) :
  KanamaScript<Node3D>(godotObject, ::Node3D), KanamaCoroutineOwner {
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
      // Web builds are single-player: there are no remote peers to spawn and the
      // peer_connected/peer_disconnected signals can never fire.
    }
    GD.print("TPS Level ready: complete")
  }

  private fun setupSdfgi() {
    GD.print("TPS Level GI: SDFGI")
    worldEnvironment.environment?.sdfgiEnabled = true
    self.requireAs("VoxelGI", ::Node3D).hide()
    self.requireAs("ReflectionProbes", ::Node3D).hide()
    lightmapGi?.queueFree()
    when (TpsSettings.renderLong("gi_quality")) {
      TpsSettings.GI_HIGH ->
        RenderingServer.environmentSetSdfgiRayCount(RenderingServerQuality.ENV_SDFGI_RAY_COUNT_96)
      TpsSettings.GI_LOW ->
        RenderingServer.environmentSetSdfgiRayCount(RenderingServerQuality.ENV_SDFGI_RAY_COUNT_32)
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
      TpsSettings.GI_HIGH ->
        RenderingServer.voxelGiSetQuality(RenderingServerQuality.VOXEL_GI_QUALITY_HIGH)
      TpsSettings.GI_LOW ->
        RenderingServer.voxelGiSetQuality(RenderingServerQuality.VOXEL_GI_QUALITY_LOW)
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
    robot.signal(RedRobotNames.Signals.exploded).connect(self, argumentCount = 0) {
      respawnRobot(spawnPoint)
    }
    spawnedNodes.addChild(robotNode, true)
  }

  @RegisterFunction("_respawn_robot")
  fun respawnRobot(spawnPoint: Node3D) {
    kanamaScope.launch {
      SceneTree.delaySeconds(15.0)
      if (exiting || self.isQueuedForDeletion() || !self.isInsideTree()) return@launch
      spawnRobot(spawnPoint)
    }
  }

  @RegisterFunction("del_player")
  fun delPlayer(id: Long) {
    val name = id.toString()
    spawnedNodes.getNodeOrNull(name)?.let { net.multigesture.kanama.api.Node(it.handle).queueFree() }
  }

  @RegisterFunction("add_player")
  fun addPlayer(id: Long, spawnPoint: Marker3D? = null) {
    GD.print("TPS Level addPlayer: $id")
    val chosen =
      spawnPoint
        ?: playerSpawnPoints
          .getChildren()
          .getOrNull((GD.randi() % playerSpawnPoints.getChildren().size).toInt())
          ?.let { Marker3D(it.handle) }
        ?: return
    val playerNode = TpsScenes.instantiate(TpsScenes.PLAYER) ?: return
    val player = Node3D(playerNode.handle)
    playerNode.setName(id.toString())
    // Web adaptation: the seam addresses the script instance directly instead of routing a
    // Variant property write through the proxy's property path.
    playerNode.kotlinScriptInstance<Player>()?.playerId = id
    player.transform = chosen.transform
    spawnedNodes.addChild(playerNode)
  }

  @OnInput
  fun input(inputEvent: GodotObject) {
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

  @Signal fun quit() = Unit
}
