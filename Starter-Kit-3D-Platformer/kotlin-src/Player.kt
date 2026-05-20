import net.multigesture.kanama.annotations.ExportSubgroup
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.AudioStreamPlayer
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.GPUParticles3D
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.types.NodePath
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector3
import net.multigesture.kanama.generated.PlayerSignals
import java.lang.foreign.MemorySegment
import net.multigesture.kanama.api.KanamaScript

@ScriptClass(attachTo = "CharacterBody3D")
class Player(godotObject: MemorySegment) : KanamaScript<CharacterBody3D>(godotObject, ::CharacterBody3D) {

	@ExportSubgroup("Components")
	@ScriptProperty
	var view: NodePath = NodePath("../View")

	@ExportSubgroup("Properties")
	@ScriptProperty
	var movementSpeed: Long = 250

	@ScriptProperty
	var jumpStrength: Long = 7

	private var movementVelocity = Vector3.ZERO
	private var rotationDirection = 0.0
	private var gravity = 0.0
	private var previouslyFloored = false

	private var jumpSingle = true
	private var jumpDouble = true

	private var coins: Long = 0L

	private lateinit var viewNode: Node3D
	private lateinit var particlesTrail: GPUParticles3D
	private lateinit var soundFootsteps: AudioStreamPlayer
	private lateinit var model: Node3D
	private lateinit var animation: AnimationPlayer
	private lateinit var audio: Node

	@Signal
	fun coinCollected(value: Long) = Unit

	@OnReady
	fun ready() {
		if (view.path.isEmpty()) view = NodePath("../View")
		viewNode = self.getAsOrNull(view, ::Node3D) ?: error("Player requires view node at $view")
		particlesTrail = self.requireAs("ParticlesTrail", ::GPUParticles3D)
		soundFootsteps = self.requireAs("SoundFootsteps", ::AudioStreamPlayer)
		model = self.requireAs("Character", ::Node3D)
		animation = self.requireAs("Character/AnimationPlayer", ::AnimationPlayer)
		audio = self.getNodeOrNull("/root/Audio") ?: error("Player requires the Audio autoload")
	}

	@OnPhysicsProcess
	fun physicsProcess(delta: Double) {
		handleControls(delta)
		handleGravity(delta)
		handleEffects(delta)

		// Movement integration
		var applied = self.velocity.lerp(movementVelocity, delta * 10.0)
		applied = applied.withY(-gravity)
		self.velocity = applied
		self.moveAndSlide()

		// Face the direction we're moving
		val v = self.velocity
		val planar = Vector2(v.z, v.x)
		if (planar.length() > 0.0) {
			rotationDirection = planar.angle()
		}
		val rot = self.rotation
		self.rotation = rot.withY(Mathf.lerpAngle(rot.y.toDouble(), rotationDirection, delta * 10.0))

		// Falling/respawning
		if (self.position.y < -10f) {
			self.getTree().reloadCurrentScene()
		}

		// Squash-stretch model relax
		model.scale = model.scale.lerp(Vector3.ONE, delta * 10.0)

		// Landing pulse
		if (self.isOnFloor() && gravity > 2.0 && !previouslyFloored) {
			model.scale = Vector3(1.25f, 0.75f, 1.25f)
			playAudio("res://sounds/land.ogg")
		}
		previouslyFloored = self.isOnFloor()
	}

	private fun handleEffects(delta: Double) {
		particlesTrail.setEmitting(false)
		soundFootsteps.setStreamPaused(true)

		val anim = animation
		val v = self.velocity

		if (self.isOnFloor()) {
			val horiz = Vector2(v.x, v.z)
			val speedFactor = horiz.length() / movementSpeed.toDouble() / delta
			if (speedFactor > 0.05) {
				if (anim.getCurrentAnimation() != "walk") anim.play("walk", customBlend = 0.1)
				if (speedFactor > 0.3) {
					soundFootsteps.setStreamPaused(false)
					soundFootsteps.setPitchScale(speedFactor)
				}
				if (speedFactor > 0.75) particlesTrail.setEmitting(true)
			} else if (anim.getCurrentAnimation() != "idle") {
				anim.play("idle", customBlend = 0.1)
			}

			anim.setSpeedScale(if (anim.getCurrentAnimation() == "walk") speedFactor else 1.0)
		} else if (anim.getCurrentAnimation() != "jump") {
			anim.play("jump", customBlend = 0.1)
		}
	}

	private fun handleControls(delta: Double) {
		val ix = Input.getAxis("move_left", "move_right")
		val iz = Input.getAxis("move_forward", "move_back")
		var input = Vector3(ix.toFloat(), 0f, iz.toFloat())
		input = input.rotated(Vector3.UP, viewNode.rotation.y.toDouble())
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
		playAudio("res://sounds/jump.ogg")
		gravity = -jumpStrength.toDouble()
		model.scale = Vector3(0.5f, 1.5f, 0.5f)

		if (jumpSingle) {
			jumpSingle = false
			jumpDouble = true
		} else {
			jumpDouble = false
		}
	}

	@RegisterFunction
	fun collectCoin() {
		coins += 1
		PlayerSignals.coinCollected(this, coins)
	}

	private fun playAudio(path: String) {
		audio.call("play", path)
	}
}
