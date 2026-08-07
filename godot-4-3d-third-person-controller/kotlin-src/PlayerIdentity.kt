package thirdperson

import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Node3D

internal fun Node3D.isPlayer(): Boolean {
    // Scene teardown fires area signals carrying handles that are already mid-free;
    // a dead handle is never the player.
    val candidate = GodotObject(handle)
    if (!GD.isInstanceValid(candidate)) return false
    return candidate.hasMethod("collect_coin")
}
