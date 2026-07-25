package net.multigesture.kanama.demos.platformer3d

import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.types.Vector3

/** Web port of a falling platform: relaxes its squash toward its rest scale (the fall/respawn
 *  interaction is deferred until the physics-body-step families land). */
@ScriptClass(attachTo = "Node3D")
class PlatformFalling(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {
  @OnPhysicsProcess
  fun physicsProcess(delta: Double) {
    self.scale = self.scale.lerp(Vector3.ONE, delta * 10.0)
  }
}
