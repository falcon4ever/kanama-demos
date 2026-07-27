package thirdperson

import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.hasMethod

internal fun Node3D.isPlayer(): Boolean {
  // Scene teardown fires area signals carrying handles that are already mid-free;
  // a dead handle is never the player.
  if (!GD.isInstanceValid(GodotObject(handle))) return false
  return Node(handle).hasMethod("collect_coin")
}
