package thirdperson

import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.AudioStreamPlayer3D
import net.multigesture.kanama.api.CanvasItem
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEventKey
import net.multigesture.kanama.api.InputEventMouseButton
import net.multigesture.kanama.api.InputMap
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.ShapeCast3D
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.generated.CharacterSkinNames
import net.multigesture.kanama.generated.PlayerSignals
import net.multigesture.kanama.types.Basis
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@GlobalClass
@ScriptClass(attachTo = "CharacterBody3D")
class Player(godotObject: MemorySegment) : KanamaScript<CharacterBody3D>(godotObject, ::CharacterBody3D) {

	@ScriptProperty
	var moveSpeed = 8.0

	@ScriptProperty
	var bulletSpeed = 10.0

	@ScriptProperty
	var attackImpulse = 10.0

	@ScriptProperty
	var acceleration = 4.0

	@ScriptProperty
	var jumpInitialImpulse = 12.0

	@ScriptProperty
	var jumpAdditionalForce = 4.5

	@ScriptProperty
	var rotationSpeed = 12.0

	@ScriptProperty
	var stoppingSpeed = 1.0

	@ScriptProperty
	var maxThrowbackForce = 15.0

	@ScriptProperty
	var shootCooldown = 0.5

	@ScriptProperty
	var grenadeCooldown = 0.5

	private lateinit var rotationRoot: Node3D
	private lateinit var cameraControllerNode: Node3D
	private lateinit var cameraController: CameraController
	private lateinit var attackAnimationPlayer: AnimationPlayer
	private lateinit var groundShapeCast: ShapeCast3D
	private lateinit var grenadeAimControllerNode: Node3D
	private lateinit var grenadeAimController: GrenadeLauncher
	private lateinit var characterSkinNode: Node3D
	private lateinit var characterSkin: CharacterSkin
	private lateinit var uiAimReticle: CanvasItem
	private lateinit var uiCoinsContainer: CoinsContainer
	private lateinit var stepSound: AudioStreamPlayer3D
	private lateinit var landingSound: AudioStreamPlayer3D

	private var equippedWeapon = WeaponType.DEFAULT
	private var moveDirection = Vector3.ZERO
	private var lastStrongDirection = Vector3.FORWARD
	private var groundHeight = 0.0
	private var startPosition = Vector3.ZERO
	private var coins = 0L
	private var isOnFloorBuffer = false
	private var shootCooldownTick = 0.0
	private var grenadeCooldownTick = 0.0

	@OnReady
	fun ready() {
		rotationRoot = self.requireAs("CharacterRotationRoot", ::Node3D)
		cameraControllerNode = self.requireAs("CameraController", ::Node3D)
		cameraController = cameraControllerNode.kotlinScriptInstance<CameraController>()
			?: error("CameraController is missing CameraController script instance")
		attackAnimationPlayer = self.requireAs("CharacterRotationRoot/MeleeAnchor/AnimationPlayer", ::AnimationPlayer)
		groundShapeCast = self.requireAs("GroundShapeCast", ::ShapeCast3D)
		grenadeAimControllerNode = self.requireAs("GrenadeLauncher", ::Node3D)
		grenadeAimController = grenadeAimControllerNode.kotlinScriptInstance<GrenadeLauncher>()
			?: error("GrenadeLauncher is missing GrenadeLauncher script instance")
		characterSkinNode = self.requireAs("CharacterRotationRoot/CharacterSkin", ::Node3D)
		characterSkin = characterSkinNode.kotlinScriptInstance<CharacterSkin>()
			?: error("CharacterSkin is missing CharacterSkin script instance")
		uiAimReticle = self.requireAs("%AimReticle", ::CanvasItem)
		uiCoinsContainer = self.requireAs("%CoinsContainer", ::Node).kotlinScriptInstance<CoinsContainer>()
			?: error("%CoinsContainer is missing CoinsContainer script instance")
		stepSound = self.requireAs("StepSound", ::AudioStreamPlayer3D)
		landingSound = self.requireAs("LandingSound", ::AudioStreamPlayer3D)

		startPosition = self.globalTransform.origin
		shootCooldownTick = shootCooldown
		grenadeCooldownTick = grenadeCooldown

		Input.setMouseMode(Input.MOUSE_MODE_CAPTURED)
		cameraController.setup(this)
		grenadeAimControllerNode.setVisible(false)
		emitWeaponSwitched()

		if (!InputMap.hasAction("move_left")) {
			registerInputActions()
		}

		characterSkinNode.signal(CharacterSkinNames.Signals.stepped).connect(self, argumentCount = 0) {
			playFootStepSound()
		}
	}

	@OnPhysicsProcess
	fun physicsProcess(delta: Double) {
		updateGroundHeight()

		if (Input.isActionJustPressed("swap_weapons")) {
			equippedWeapon = if (equippedWeapon == WeaponType.GRENADE) WeaponType.DEFAULT else WeaponType.GRENADE
			grenadeAimControllerNode.setVisible(equippedWeapon == WeaponType.GRENADE)
			emitWeaponSwitched()
		}

		val isAttacking = Input.isActionPressed("attack") && !attackAnimationPlayer.isPlaying()
		val isJustAttacking = Input.isActionJustPressed("attack")
		val isJustJumping = Input.isActionJustPressed("jump") && self.isOnFloor()
		val isAiming = Input.isActionPressed("aim") && self.isOnFloor()
		val isAirBoosting = Input.isActionPressed("jump") && !self.isOnFloor() && self.velocity.y > 0.0f
		val isJustOnFloor = self.isOnFloor() && !isOnFloorBuffer

		isOnFloorBuffer = self.isOnFloor()
		moveDirection = getCameraOrientedInput()

		if (moveDirection.length() > 0.2) {
			lastStrongDirection = moveDirection.normalized()
		}
		if (isAiming) {
			lastStrongDirection = (cameraControllerNode.globalTransform.basis * Vector3.BACK).normalized()
		}

		orientCharacterToDirection(lastStrongDirection, delta)

		val yVelocity = self.velocity.y
		var newVelocity = self.velocity.withY(0.0)
		newVelocity = newVelocity.lerp(moveDirection * moveSpeed, acceleration * delta)
		if (moveDirection.length() == 0.0 && newVelocity.length() < stoppingSpeed) {
			newVelocity = Vector3.ZERO
		}
		self.velocity = newVelocity.withY(yVelocity)

		if (isAiming) {
			cameraController.setPivot(CameraController.PIVOT_OVER_SHOULDER)
			grenadeAimController.throwDirection = cameraController.getCameraForward()
			grenadeAimController.fromLookPosition = cameraController.getCameraGlobalPosition()
			uiAimReticle.setVisible(true)
		} else {
			cameraController.setPivot(CameraController.PIVOT_THIRD_PERSON)
			grenadeAimController.throwDirection = lastStrongDirection
			grenadeAimController.fromLookPosition = self.globalPosition
			uiAimReticle.setVisible(false)
		}

		shootCooldownTick += delta
		grenadeCooldownTick += delta

		if (isAttacking) {
			when (equippedWeapon) {
				WeaponType.DEFAULT -> {
					if (isAiming && self.isOnFloor()) {
						if (shootCooldownTick > shootCooldown) {
							shootCooldownTick = 0.0
							shoot()
						}
					} else if (isJustAttacking) {
						attack()
					}
				}
				WeaponType.GRENADE -> {
					if (grenadeCooldownTick > grenadeCooldown) {
						grenadeCooldownTick = 0.0
						grenadeAimController.throwGrenade()
					}
				}
			}
		}

		self.velocity = self.velocity.withY(self.velocity.y.toDouble() + GRAVITY * delta)

		if (isJustJumping) {
			self.velocity = self.velocity.withY(self.velocity.y.toDouble() + jumpInitialImpulse)
		} else if (isAirBoosting) {
			self.velocity = self.velocity.withY(self.velocity.y.toDouble() + jumpAdditionalForce * delta)
		}

		if (isJustJumping) {
			characterSkin.jump()
		} else if (!self.isOnFloor() && self.velocity.y < 0.0f) {
			characterSkin.fall()
		} else if (self.isOnFloor()) {
			val xzVelocity = Vector3(self.velocity.x, 0f, self.velocity.z)
			if (xzVelocity.length() > stoppingSpeed) {
				characterSkin.setMoving(true)
				characterSkin.setMovingSpeed(Mathf.inverseLerp(0.0, moveSpeed, xzVelocity.length()))
			} else {
				characterSkin.setMoving(false)
			}
		}

		if (isJustOnFloor) {
			landingSound.play()
		}

		val positionBefore = self.globalPosition
		self.moveAndSlide()
		val positionAfter = self.globalPosition

		val deltaPosition = positionAfter - positionBefore
		if (deltaPosition.length() < STUCK_EPSILON && self.velocity.length() > STUCK_EPSILON) {
			self.globalPosition = self.globalPosition + self.getWallNormal() * 0.1
		}
	}

	@RegisterFunction
	fun attack() {
		attackAnimationPlayer.play("Attack")
		characterSkin.punch()
		self.velocity = rotationRoot.transform.basis * Vector3.BACK * attackImpulse
	}

	@RegisterFunction
	fun shoot() {
		val origin = self.globalPosition + Vector3.UP
		val aimTarget = cameraController.getAimTarget()
		val aimDirection = (aimTarget - origin).normalized()
		DemoScenes.launchBullet(self.getParent(), self, origin, aimDirection * bulletSpeed, 14.0)
	}

	@RegisterFunction("reset_position")
	fun resetPosition() {
		self.transform = self.transform.withOrigin(startPosition)
	}

	@RegisterFunction("get_ground_height")
	fun getGroundHeight(): Double = groundHeight

	@RegisterFunction("collect_coin")
	fun collectCoin() {
		coins += 1
		uiCoinsContainer.updateCoinsAmount(coins)
	}

	@RegisterFunction("lose_coins")
	fun loseCoins() {
		val lostCoins = Mathf.min(coins, 5L)
		coins -= lostCoins
		repeat(lostCoins.toInt()) {
			val coinNode = DemoScenes.instantiate(DemoScenes.COIN) ?: return@repeat
			val coin = coinNode.kotlinScriptInstance<Coin>()
				?: error("Coin scene is missing Coin script instance")
			self.getParent()?.addChild(coinNode)
			Node3D(coinNode.handle).globalPosition = self.globalPosition
			coin.spawn(1.5)
		}
		uiCoinsContainer.updateCoinsAmount(coins)
	}

	@RegisterFunction("play_foot_step_sound")
	fun playFootStepSound() {
		stepSound.setPitchScale(GD.randfn(1.2, 0.2))
		stepSound.play()
	}

	@RegisterFunction
	fun damage(impactPoint: Vector3, force: Vector3) {
		self.velocity = force.withY(Mathf.abs(force.y.toDouble())).limitLength(maxThrowbackForce)
		loseCoins()
	}

	@Signal("weapon_switched")
	fun weaponSwitched(weaponName: String) = Unit

	private fun updateGroundHeight() {
		if (groundShapeCast.getCollisionCount() > 0) {
			for (index in 0 until groundShapeCast.getCollisionCount()) {
				groundHeight = Mathf.max(groundHeight, groundShapeCast.getCollisionPoint(index).y.toDouble())
			}
		} else {
			groundHeight = self.globalPosition.y.toDouble() + groundShapeCast.targetPosition.y.toDouble()
		}
		if (self.globalPosition.y.toDouble() < groundHeight) {
			groundHeight = self.globalPosition.y.toDouble()
		}
	}

	private fun getCameraOrientedInput(): Vector3 {
		if (attackAnimationPlayer.isPlaying()) {
			return Vector3.ZERO
		}

		val rawInput = Input.getVector("move_left", "move_right", "move_up", "move_down")
		var input = Vector3.ZERO
		input = input.withX(-rawInput.x.toDouble() * Mathf.sqrt(1.0 - rawInput.y.toDouble() * rawInput.y.toDouble() / 2.0))
		input = input.withZ(-rawInput.y.toDouble() * Mathf.sqrt(1.0 - rawInput.x.toDouble() * rawInput.x.toDouble() / 2.0))

		input = cameraControllerNode.globalTransform.basis * input
		return input.withY(0.0)
	}

	private fun orientCharacterToDirection(direction: Vector3, delta: Double) {
		val leftAxis = Vector3.UP.cross(direction)
		val rotationBasis = Basis(leftAxis, Vector3.UP, direction).getRotationQuaternion()
		val modelScale = rotationRoot.transform.basis.getScale()
		val currentRotation = rotationRoot.transform.basis.getRotationQuaternion()
		rotationRoot.basis = Basis(currentRotation.slerp(rotationBasis, delta * rotationSpeed)).scaled(modelScale)
	}

	private fun registerInputActions() {
		for ((action, keycode) in INPUT_ACTIONS) {
			if (InputMap.hasAction(action)) {
				continue
			}
			InputMap.addAction(action)
			val inputKey = InputEventKey.create()
			inputKey.keycode = keycode
			InputMap.actionAddEvent(action, inputKey)
		}
	}

	private fun emitWeaponSwitched() {
		PlayerSignals.weaponSwitched(this, equippedWeapon.name)
	}

	private enum class WeaponType {
		DEFAULT,
		GRENADE,
	}

	companion object {
		private const val GRAVITY = -30.0
		private const val STUCK_EPSILON = 0.001

		private val INPUT_ACTIONS = linkedMapOf(
			"move_left" to InputEventKey.KEY_A,
			"move_right" to InputEventKey.KEY_D,
			"move_up" to InputEventKey.KEY_W,
			"move_down" to InputEventKey.KEY_S,
			"jump" to InputEventKey.KEY_SPACE,
			"attack" to InputEventMouseButton.MOUSE_BUTTON_LEFT,
			"aim" to InputEventMouseButton.MOUSE_BUTTON_RIGHT,
			"swap_weapons" to InputEventKey.KEY_TAB,
			"pause" to InputEventKey.KEY_ESCAPE,
			"camera_left" to InputEventKey.KEY_Q,
			"camera_right" to InputEventKey.KEY_E,
			"camera_up" to InputEventKey.KEY_R,
			"camera_down" to InputEventKey.KEY_F,
		)
	}
}
