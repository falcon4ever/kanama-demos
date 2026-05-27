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

fun Node.isOfflineMultiplayer(): Boolean =
    getMultiplayer()?.getMultiplayerPeer() is OfflineMultiplayerPeer

fun MeshInstance3D.shaderMaterialOverride(surface: Int = 0): ShaderMaterial? =
    getSurfaceOverrideMaterial(surface)?.let { ShaderMaterial.fromResource(it) }

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
