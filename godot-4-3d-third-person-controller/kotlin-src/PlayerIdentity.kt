package thirdperson

import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Node3D

internal fun Node3D.isPlayer(): Boolean =
    GodotObject(handle).hasMethod("collect_coin")
