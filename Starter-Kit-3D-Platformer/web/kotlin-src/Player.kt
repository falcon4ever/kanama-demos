package net.multigesture.kanama.demos.platformer3d

import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.types.Vector3

/**
 * Web port of the platformer Player (Task 60d). The full desktop controller drives animation,
 * particles, footstep audio and camera-relative input; the Web foundation runs the physics core —
 * gravity + move_and_slide + floor contact — so the character settles onto the level's platforms.
 * (Camera-relative movement needs Input.get_axis, and animation/audio need families still being
 * built; they are omitted here rather than faked.)
 */
@ScriptClass(attachTo = "CharacterBody3D")
class Player(godotObject: GodotHandle) :
  KanamaScript<CharacterBody3D>(godotObject, ::CharacterBody3D) {

  @ScriptProperty var movementSpeed: Long = 250

  @ScriptProperty var jumpStrength: Long = 7

  private var gravity = 0.0

  @Signal fun coinCollected(value: Long) = Unit

  @OnPhysicsProcess
  fun physicsProcess(delta: Double) {
    gravity += 25.0 * delta
    if (self.isOnFloor() && gravity > 0.0) gravity = 0.0
    self.velocity = Vector3(0.0, -gravity, 0.0)
    self.moveAndSlide()
  }
}
