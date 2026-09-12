package tps

import net.multigesture.kanama.api.AudioStreamPlayer
import net.multigesture.kanama.api.AudioStreamPlayer3D
import net.multigesture.kanama.api.ButtonGroup
import net.multigesture.kanama.api.CPUParticles3D
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.CollisionObject3D
import net.multigesture.kanama.api.ConfigFile
import net.multigesture.kanama.api.ENetMultiplayerPeer
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Material
import net.multigesture.kanama.api.MeshInstance3D
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.MultiplayerAPI
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.OfflineMultiplayerPeer
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.ShaderMaterial
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.types.Basis
import net.multigesture.kanama.types.Transform3D
import net.multigesture.kanama.types.Vector3

object TpsScenes {
    const val LEVEL = "res://level/level.tscn"
    const val MENU = "res://menu/menu.tscn"
    const val PLAYER = "res://player/player.tscn"
    const val BULLET = "res://player/bullet/bullet.tscn"
    const val RED_ROBOT = "res://enemies/red_robot/red_robot.tscn"
    const val ROBOT_BLAST = "res://enemies/red_robot/laser/impact_effect/impact_effect.tscn"
    const val PART_PUFF = "res://enemies/red_robot/parts/part_disappear_effect/part_disappear.tscn"

    private val sceneCache = mutableMapOf<String, PackedScene>()

    fun scene(path: String): PackedScene? =
        sceneCache.getOrPut(path) { ResourceLoader.loadPackedScene(path) ?: return null }

    fun instantiate(path: String): Node? = scene(path)?.instantiate()

    /**
     * Smoke teardown: the scene cache holds PackedScene handles that outlive the scene root (Godot
     * caches resources), so the browser smoke releases them to drain the live-handle count to zero.
     */
    fun releaseCachedScenes() {
        sceneCache.values.forEach { it.close() }
        sceneCache.clear()
    }
}

object TpsFactory {
    fun configFile(): ConfigFile = ConfigFile.create()
    fun offlineMultiplayerPeer(): OfflineMultiplayerPeer = OfflineMultiplayerPeer.create()
    fun enetMultiplayerPeer(): ENetMultiplayerPeer = ENetMultiplayerPeer.create()
    fun buttonGroup(): ButtonGroup = ButtonGroup.create()
}

fun Transform3D.composedWith(other: Transform3D): Transform3D =
    Transform3D(
        basis = basis.composedWith(other.basis),
        origin = basis * other.origin + origin,
    )

fun Basis.composedWith(other: Basis): Basis =
    Basis(
        x = this * other.x,
        y = this * other.y,
        z = this * other.z,
    )

fun GodotObject.asNode3DOrNull(): Node3D? =
    if (handle.address() == 0L) null else Node3D(handle)

fun Node.isPlayerNode(): Boolean =
    kotlinScriptInstance<Player>() != null || getName() == "Player" || getName().toLongOrNull() != null

// `Node.getMultiplayer()` returns an owned +1 on the SceneMultiplayer, like every RefCounted
// getter (kanama docs/game-dev/godot-api.md#resource-ownership). GDScript's `multiplayer`
// drops that reference at scope exit; here the release is explicit, so every read goes
// through this helper. Before it, per-frame reads in DebugLabel/PlayerInputSynchronizer left
// `Leaked instance: SceneMultiplayer ... Reference count: 6867` in the headless smoke.
inline fun <R> Node.withMultiplayer(block: (MultiplayerAPI) -> R): R? {
    val api = getMultiplayer() ?: return null
    try {
        return block(api)
    } finally {
        api.close()
    }
}

fun Node.isMultiplayerServer(): Boolean = withMultiplayer { it.isServer() } == true

fun Node.multiplayerPeers(): List<Int> = withMultiplayer { it.getPeers() } ?: emptyList()

fun Node.multiplayerUniqueId(): Int = withMultiplayer { it.getUniqueId() } ?: 0

fun Node.multiplayerRemoteSenderId(): Int = withMultiplayer { it.getRemoteSenderId() } ?: 0

// getMultiplayerPeer() is a second owned +1 (the previous `is OfflineMultiplayerPeer` test also
// never matched: the wrapper comes back typed as the base MultiplayerPeer, so the class is
// checked by name here).
fun Node.isOfflineMultiplayer(): Boolean =
    withMultiplayer { api -> api.getMultiplayerPeer()?.use { it.isClass("OfflineMultiplayerPeer") } } == true

// getSurfaceOverrideMaterial() is an owned +1; the ShaderMaterial handed back is a view over the
// material the mesh keeps alive, so the caller must not close it.
fun MeshInstance3D.shaderMaterialOverride(surface: Int = 0): ShaderMaterial? =
    getSurfaceOverrideMaterial(surface)?.use { ShaderMaterial.fromResource(it) }

fun Material?.asShaderMaterial(): ShaderMaterial? =
    this?.let { ShaderMaterial.fromResource(it) }

fun CPUParticles3D.restartEmitting() {
    restart()
    emitting = true
}

fun AudioStreamPlayer.playRandomPitch(base: Double = 1.0, variance: Double = 0.1) {
    setPitchScale(net.multigesture.kanama.api.GD.randfn(base, variance))
    play()
}

fun AudioStreamPlayer3D.playRandomPitch(base: Double = 1.0, variance: Double = 0.1) {
    setPitchScale(net.multigesture.kanama.api.GD.randfn(base, variance))
    play()
}

fun CollisionObject3D.excludeSelfRid(): List<net.multigesture.kanama.types.RID> =
    listOf(getRid())
