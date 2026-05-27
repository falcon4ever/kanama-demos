package tps

import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.Rpc
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.AnimationTree
import net.multigesture.kanama.api.AudioStreamPlayer3D
import net.multigesture.kanama.api.BoneAttachment3D
import net.multigesture.kanama.api.CPUParticles3D
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.CollisionShape3D
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.MeshInstance3D
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.api.PhysicsRayQueryParameters3D
import net.multigesture.kanama.api.RayCast3D
import net.multigesture.kanama.api.RigidBody3D
import net.multigesture.kanama.api.ShaderMaterial
import net.multigesture.kanama.api.Timer
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.types.Basis
import net.multigesture.kanama.types.Transform3D
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "CharacterBody3D")
class RedRobot(godotObject: MemorySegment) : KanamaScript<CharacterBody3D>(godotObject, ::CharacterBody3D), KanamaCoroutineOwner {
	override val kanamaScope = KanamaScope()

	@ScriptProperty(name = "test_shoot")
	var testShoot = false

	@ScriptProperty(name = "target_position")
	var targetPosition = Vector3.ZERO

	@ScriptProperty
	var health = 5L

	@ScriptProperty
	var state = State.APPROACH.id

	@ScriptProperty
	var dead = false

	@ScriptProperty(name = "aim_preparing")
	var aimPreparing = AIM_PREPARE_TIME

	private var shootCountdown = SHOOT_WAIT
	private var aimCountdown = AIM_TIME
	private var player: Node3D? = null
	private var orientation = Transform3D.IDENTITY

	private lateinit var animationTree: AnimationTree
	private lateinit var shootAnimation: AnimationPlayer
	private lateinit var model: Node3D
	private lateinit var rayFrom: BoneAttachment3D
	private lateinit var rayMesh: MeshInstance3D
	private lateinit var laserEmber: CPUParticles3D
	private lateinit var laserRaycast: RayCast3D
	private lateinit var collisionShape: CollisionShape3D
	private lateinit var explosionSound: AudioStreamPlayer3D
	private lateinit var hitSound: AudioStreamPlayer3D
	private lateinit var death: Node3D
	private lateinit var deathShield1: RigidBody3D
	private lateinit var deathShield2: RigidBody3D
	private lateinit var deathHead: RigidBody3D
	private lateinit var deathShield1Part: Part
	private lateinit var deathShield2Part: Part
	private lateinit var deathHeadPart: Part
	private lateinit var deathDetachSpark1: CPUParticles3D
	private lateinit var deathDetachSpark2: CPUParticles3D

	@OnReady
	fun ready() {
		orientation = self.globalTransform.withOrigin(Vector3.ZERO)
		animationTree = self.requireAs("AnimationTree", ::AnimationTree)
		shootAnimation = self.requireAs("ShootAnimation", ::AnimationPlayer)
		model = self.requireAs("RedRobotModel", ::Node3D)
		rayFrom = model.requireAs("Armature/Skeleton3D/RayFrom", ::BoneAttachment3D)
		rayMesh = rayFrom.requireAs("RayMesh", ::MeshInstance3D)
		laserEmber = rayFrom.requireAs("LaserEmber", ::CPUParticles3D)
		laserRaycast = rayFrom.requireAs("RayCast", ::RayCast3D)
		collisionShape = self.requireAs("CollisionShape3D", ::CollisionShape3D)
		explosionSound = self.requireAs("SoundEffects/Explosion", ::AudioStreamPlayer3D)
		hitSound = self.requireAs("SoundEffects/Hit", ::AudioStreamPlayer3D)
		death = self.requireAs("Death", ::Node3D)
		deathShield1 = death.requireAs("PartShield1", ::RigidBody3D)
		deathShield2 = death.requireAs("PartShield2", ::RigidBody3D)
		deathHead = death.requireAs("PartHead", ::RigidBody3D)
		deathShield1Part = deathShield1.kotlinScriptInstance<Part>()
			?: error("Death/PartShield1 is missing Part script")
		deathShield2Part = deathShield2.kotlinScriptInstance<Part>()
			?: error("Death/PartShield2 is missing Part script")
		deathHeadPart = deathHead.kotlinScriptInstance<Part>()
			?: error("Death/PartHead is missing Part script")
		deathDetachSpark1 = death.requireAs("DetachSpark1", ::CPUParticles3D)
		deathDetachSpark2 = death.requireAs("DetachSpark2", ::CPUParticles3D)
		animationTree.active = true
		if (testShoot) shootCountdown = 0.0
		if (dead) {
			model.visible = false
			disableCollision()
			animationTree.active = false
		}
		animate(0.0)
	}

	@RegisterFunction("resume_approach")
	fun resumeApproach() {
		state = State.APPROACH.id
		aimPreparing = AIM_PREPARE_TIME
		shootCountdown = SHOOT_WAIT
	}

	@RegisterFunction
	@Rpc(callLocal = true)
	fun hit() {
		if (dead) return
		animationTree.set("parameters/hit${(net.multigesture.kanama.api.GD.randi() % 3) + 1}/request", 1L)
		hitSound.play()
		health -= 1
		if (health > 0) return

		dead = true
		animationTree.active = false
		model.visible = false
		death.visible = true
		disableCollision()
		deathDetachSpark1.emitting = true
		deathDetachSpark2.emitting = true
		deathShield1Part.explode()
		deathShield2Part.explode()
		deathHeadPart.explode()
		explosionSound.play()
		self.emitSignal("exploded")
		if (self.getMultiplayer()?.isServer() == true) {
				kanamaScope.launch {
					self.getTree().createTimer(10.0)?.signal(Timer.Signals.timeout)?.await(self, argumentCount = 0)
					self.queueFree()
				}
		}
	}

	@RegisterFunction
	fun shoot() {
		val rayOrigin = rayFrom.globalTransform.origin
		val rayDir = rayFrom.globalTransform.basis.y
		var maxDist = 1000.0
		val query = PhysicsRayQueryParameters3D.create(rayOrigin, rayOrigin + rayDir * maxDist, 0xffffffffL, listOf(self.getRid()))
		val hit = self.getWorld3d()?.directSpaceState?.intersectRay(query) ?: emptyMap()
		if (hit.isNotEmpty()) {
			val position = hit["position"] as? Vector3
			if (position != null) maxDist = rayOrigin.distanceTo(position)
		}
		clipRay(maxDist)
			val meshOffset = rayMesh.position.z.toDouble()
		laserEmber.position = Vector3(0.0, 0.0, -maxDist / 2.0 - meshOffset)
			laserEmber.emissionBoxExtents = laserEmber.emissionBoxExtents.withZ((maxDist - Mathf.abs(meshOffset)) / 2.0)
		if (hit.isNotEmpty()) {
			val blast = TpsScenes.instantiate(TpsScenes.ROBOT_BLAST)
			if (blast != null) {
				self.getTree().root.addChild(blast)
				Node3D(blast.handle).globalTransform =
					Node3D(blast.handle).globalTransform.withOrigin(hit["position"] as? Vector3 ?: rayOrigin)
			}
			val collider = hit["collider"] as? GodotObject
			if (collider != null && player?.handle?.address() == collider.handle.address()) {
					val hitPlayer = player?.kotlinScriptInstance<Player>()
					if (hitPlayer != null) {
						kanamaScope.launch {
							self.getTree().createTimer(0.1)?.signal(Timer.Signals.timeout)?.await(self, argumentCount = 0)
							hitPlayer.addCameraShakeTrauma(13.0)
					}
				}
			}
		}
	}

	private fun animate(delta: Double) {
		if (state == State.APPROACH.id) {
			val local = toLocalTarget(targetPosition)
			val angle = Mathf.atan2(local.x.toDouble(), local.z.toDouble())
			when {
				angle > PLAYER_AIM_TOLERANCE -> animationTree.set("parameters/state/transition_request", "turn_left")
				angle < -PLAYER_AIM_TOLERANCE -> animationTree.set("parameters/state/transition_request", "turn_right")
				targetPosition == Vector3.ZERO -> animationTree.set("parameters/state/transition_request", "idle")
				else -> animationTree.set("parameters/state/transition_request", "walk")
			}
		} else {
			animationTree.set("parameters/state/transition_request", "idle")
		}

		if (targetPosition != Vector3.ZERO) {
			animationTree.set("parameters/aiming/blend_amount", (aimPreparing / AIM_PREPARE_TIME).coerceIn(0.0, 1.0))
			val local = rayMesh.globalTransform.basis.inverse() *
				((targetPosition + Vector3.UP) - rayMesh.globalTransform.origin)
				val hAngle = net.multigesture.kanama.api.GD.radToDeg(Mathf.atan2(local.x.toDouble(), -local.z.toDouble()))
				val vAngle = net.multigesture.kanama.api.GD.radToDeg(Mathf.atan2(local.y.toDouble(), -local.z.toDouble()))
			val blend = (animationTree.get("parameters/aim/blend_position") as? Vector2) ?: Vector2.ZERO
			animationTree.set(
				"parameters/aim/blend_position",
				Vector2(
					(blend.x.toDouble() + BLEND_AIM_SPEED * delta * -hAngle).coerceIn(-1.0, 1.0),
					(blend.y.toDouble() + BLEND_AIM_SPEED * delta * vAngle).coerceIn(-1.0, 1.0),
				),
			)
		}
	}

	@OnPhysicsProcess
	fun physicsProcess(delta: Double) {
		if (dead) return
		if (self.getMultiplayer()?.isServer() != true) {
			animate(delta)
			return
		}
		if (testShoot) {
			shoot()
			testShoot = false
		}

		val activePlayer = player
		if (activePlayer == null) {
			targetPosition = Vector3.ZERO
			animate(delta)
			self.velocity = self.getGravity() * delta
			self.setUpDirection(Vector3.UP)
			self.moveAndSlide()
			return
		}

		targetPosition = activePlayer.globalTransform.origin
		if (state == State.APPROACH.id) {
			if (aimPreparing > 0.0) aimPreparing = (aimPreparing - delta).coerceAtLeast(0.0)
			val angle = Mathf.atan2(toLocalTarget(targetPosition).x.toDouble(), toLocalTarget(targetPosition).z.toDouble())
			if (angle in -PLAYER_AIM_TOLERANCE..PLAYER_AIM_TOLERANCE) {
				shootCountdown -= delta
				if (shootCountdown < 0.0) {
					if (hasLineOfSight(activePlayer)) {
						state = State.AIM.id
						aimCountdown = AIM_TIME
						aimPreparing = 0.0
					} else {
						shootCountdown = SHOOT_WAIT
					}
				}
			}
		} else {
			var maxDist = 1000.0
			if (laserRaycast.isColliding()) {
				maxDist = (rayFrom.globalTransform.origin - laserRaycast.getCollisionPoint()).length()
			}
			clipRay(maxDist)
			if (aimPreparing < AIM_PREPARE_TIME) aimPreparing = (aimPreparing + delta).coerceAtMost(AIM_PREPARE_TIME)
			aimCountdown -= delta
				if (aimCountdown < 0.0 && state == State.AIM.id) {
					if (hasLineOfSight(activePlayer)) {
						state = State.SHOOTING.id
						shootCountdown = SHOOT_WAIT
						playShoot()
					} else {
						resumeApproach()
					}
			}
		}

		animate(delta)
		orientation = composeTransforms(orientation, Transform3D(Basis(animationTree.getRootMotionRotation()), animationTree.getRootMotionPosition()))
		val horizontal = orientation.origin / delta
		self.velocity = self.velocity.withX(horizontal.x.toDouble()).withZ(horizontal.z.toDouble())
		self.velocity = self.velocity + self.getGravity() * delta
		self.setUpDirection(Vector3.UP)
		self.moveAndSlide()
		orientation = orientation.withOrigin(Vector3.ZERO).orthonormalized()
		self.globalTransform = self.globalTransform.withBasis(orientation.basis)
	}

	@RegisterFunction("play_shoot")
	@Rpc(callLocal = true)
	fun playShoot() {
		shootAnimation.play("shoot")
	}

	@RegisterFunction("shoot_check")
	fun shootCheck() {
		testShoot = true
	}

	@RegisterFunction("_clip_ray")
	fun clipRay(length: Double) {
		if (OS.hasFeature("dedicated_server")) return
		val material = rayMesh.getSurfaceOverrideMaterial(0)?.let { ShaderMaterial.fromResource(it) } ?: return
		material.setShaderParameter("clip", length + rayMesh.position.z)
	}

	@RegisterFunction("_on_area_body_entered")
	fun onAreaBodyEntered(body: GodotObject) {
		val node = Node3D(body.handle)
		if (node.kotlinScriptInstance<Player>() != null || node.getName() == "Target") {
			player = node
		}
	}

	@RegisterFunction("_on_area_body_exited")
	fun onAreaBodyExited(body: GodotObject) {
		val node = Node3D(body.handle)
		if (player?.handle?.address() == node.handle.address()) {
			player = null
		}
	}

	@Signal
	fun exploded() = Unit

	@OnExitTree
	fun exitTree() {
		kanamaScope.cancel()
		self.setPhysicsProcess(false)
		disableCollision()
		if (::animationTree.isInitialized) {
			animationTree.active = false
		}
	}

	private fun disableCollision() {
		self.collisionLayer = 0
		self.collisionMask = 0
		if (::collisionShape.isInitialized) {
			collisionShape.disabled = true
		}
	}

	private fun hasLineOfSight(target: Node3D): Boolean {
		val rayOrigin = rayFrom.globalTransform.origin
		val rayTo = target.globalTransform.origin + Vector3.UP
		val query = PhysicsRayQueryParameters3D.create(rayOrigin, rayTo, 0xffffffffL, listOf(self.getRid()))
		val hit = self.getWorld3d()?.directSpaceState?.intersectRay(query) ?: return false
		val collider = hit["collider"] as? GodotObject ?: return false
		return collider.handle.address() == target.handle.address()
	}

	private fun toLocalTarget(target: Vector3): Vector3 =
		self.globalTransform.basis.inverse() * (target - self.globalTransform.origin)

	private fun composeTransforms(first: Transform3D, second: Transform3D): Transform3D =
		Transform3D(
			basis = composeBases(first.basis, second.basis),
			origin = first.basis * second.origin + first.origin,
		)

	private fun composeBases(first: Basis, second: Basis): Basis =
		Basis(
			x = first * second.x,
			y = first * second.y,
			z = first * second.z,
		)

	private enum class State(val id: Long) {
		APPROACH(0),
		AIM(1),
		SHOOTING(2),
	}

	private companion object {
		val PLAYER_AIM_TOLERANCE = net.multigesture.kanama.api.GD.degToRad(15.0)
		const val SHOOT_WAIT = 6.0
		const val AIM_TIME = 1.0
		const val AIM_PREPARE_TIME = 0.5
		const val BLEND_AIM_SPEED = 0.05
	}
}
