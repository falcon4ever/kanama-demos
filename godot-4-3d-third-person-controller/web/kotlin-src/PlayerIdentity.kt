package thirdperson

import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.hasMethod

internal fun Node3D.isPlayer(): Boolean = Node(handle).hasMethod("collect_coin")
