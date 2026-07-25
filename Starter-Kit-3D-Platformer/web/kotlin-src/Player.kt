package net.multigesture.kanama.demos.platformer3d

import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.AudioStreamPlayer
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.GPUParticles3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector3

/**
 * Web port of the platformer Player: camera-relative movement (Input.get_axis), double jump,
 * gravity + move_and_slide, facing lerp, squash-stretch, walk/idle/jump animation, trail particles,
 * and paced footstep audio. Deferred until their families land (documented, not faked): jump/land
 * one-shot audio (dynamic call into the GDScript Audio autoload) and cross-script coin scoring.
 * The current animation is tracked locally instead of reading AnimationPlayer.current_animation.
 */
@ScriptClass(attachTo = "CharacterBody3D")
class Player(godotObject: GodotHandle) :
  KanamaScript<CharacterBody3D>(godotObject, ::CharacterBody3D) {

  @ScriptProperty var movementSpeed: Long = 250

  @ScriptProperty var jumpStrength: Long = 7

  private var movementVelocity = Vector3.ZERO
  private var rotationDirection = 0.0
  private var gravity = 0.0
  private var previouslyFloored = false

  private var jumpSingle = true
  private var jumpDouble = true

  private var currentAnimation = ""

  private var viewNode: Node3D? = null
  private lateinit var particlesTrail: GPUParticles3D
  private lateinit var soundFootsteps: AudioStreamPlayer
  private lateinit var model: Node3D
  private lateinit var animation: AnimationPlayer

  @Signal fun coinCollected(value: Long) = Unit

  @OnReady
  fun ready() {
    viewNode = self.getAsOrNull("../View", ::Node3D)
    particlesTrail = self.requireAs("ParticlesTrail", ::GPUParticles3D)
    soundFootsteps = self.requireAs("SoundFootsteps", ::AudioStreamPlayer)
    model = self.requireAs("Character", ::Node3D)
    animation = self.requireAs("Character/AnimationPlayer", ::AnimationPlayer)
  }

  @OnPhysicsProcess
  fun physicsProcess(delta: Double) {
    handleControls(delta)
    handleGravity(delta)
    handleEffects(delta)

    var applied = self.velocity.lerp(movementVelocity, delta * 10.0)
    applied = applied.withY(-gravity)
    self.velocity = applied
    self.moveAndSlide()

    // Face the direction we're moving.
    val v = self.velocity
    val planar = Vector2(v.z, v.x)
    if (planar.length() > 0.0) {
      rotationDirection = planar.angle()
    }
    val rot = self.rotation
    self.rotation = rot.withY(Mathf.lerpAngle(rot.y, rotationDirection, delta * 10.0))

    // Falling/respawning.
    if (self.position.y < -10.0) {
      self.getTree().reloadCurrentScene()
      return
    }

    // Squash-stretch model relax.
    model.scale = model.scale.lerp(Vector3.ONE, delta * 10.0)

    // Landing pulse.
    if (self.isOnFloor() && gravity > 2.0 && !previouslyFloored) {
      model.scale = Vector3(1.25, 0.75, 1.25)
    }
    previouslyFloored = self.isOnFloor()
  }

  private fun playAnimation(name: String) {
    if (currentAnimation == name) return
    currentAnimation = name
    animation.play(name, customBlend = 0.1)
  }

  private fun handleEffects(delta: Double) {
    particlesTrail.setEmitting(false)
    soundFootsteps.setStreamPaused(true)

    val v = self.velocity
    if (self.isOnFloor()) {
      val horiz = Vector2(v.x, v.z)
      val speedFactor = horiz.length() / movementSpeed.toDouble() / delta
      if (speedFactor > 0.05) {
        playAnimation("walk")
        if (speedFactor > 0.3) {
          soundFootsteps.setStreamPaused(false)
          soundFootsteps.setPitchScale(speedFactor)
        }
        if (speedFactor > 0.75) particlesTrail.setEmitting(true)
      } else {
        playAnimation("idle")
      }
      animation.setSpeedScale(if (currentAnimation == "walk") speedFactor else 1.0)
    } else {
      playAnimation("jump")
    }
  }

  private fun handleControls(delta: Double) {
    val ix = Input.getAxis("move_left", "move_right")
    val iz = Input.getAxis("move_forward", "move_back")
    var input = Vector3(ix, 0.0, iz)
    input = input.rotated(Vector3.UP, viewNode?.rotation?.y ?: 0.0)
    if (input.length() > 1.0) input = input.normalized()
    movementVelocity = input * (movementSpeed.toDouble() * delta)

    if (Input.isActionJustPressed("jump") && (jumpSingle || jumpDouble)) {
      jump()
    }
  }

  private fun handleGravity(delta: Double) {
    gravity += 25.0 * delta
    if (gravity > 0.0 && self.isOnFloor()) {
      jumpSingle = true
      gravity = 0.0
    }
  }

  private fun jump() {
    gravity = -jumpStrength.toDouble()
    model.scale = Vector3(0.5, 1.5, 0.5)

    if (jumpSingle) {
      jumpSingle = false
      jumpDouble = true
    } else {
      jumpDouble = false
    }
  }
}
