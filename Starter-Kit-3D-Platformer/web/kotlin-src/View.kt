package net.multigesture.kanama.demos.platformer3d

import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D

/**
 * Web port of the platformer camera rig: smoothly follows the Player's position. The desktop
 * version also applies camera-relative rotation/zoom from Input.get_axis; that input surface is
 * omitted on the Web foundation, so the rig just tracks the target.
 */
@ScriptClass(attachTo = "Node3D")
class View(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {
  private var targetNode: Node3D? = null

  @OnReady
  fun ready() {
    targetNode = self.getAsOrNull("../Player", ::Node3D)
  }

  @OnPhysicsProcess
  fun physicsProcess(delta: Double) {
    targetNode?.let { self.position = self.position.lerp(it.position, delta * 4.0) }
  }
}
