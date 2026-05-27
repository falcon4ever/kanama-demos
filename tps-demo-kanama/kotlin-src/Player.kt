package tps

import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.OnEnterTree
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.Rpc
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.AnimationTree
import net.multigesture.kanama.api.AudioStreamPlayer
import net.multigesture.kanama.api.CPUParticles3D
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Marker3D
import net.multigesture.kanama.api.MultiplayerSynchronizer
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.Timer
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.types.Basis
import net.multigesture.kanama.types.Transform3D
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@GlobalClass
@ScriptClass(attachTo = "CharacterBody3D")
class Player(godotObject: MemorySegment) : KanamaScript<CharacterBody3D>(godotObject, ::CharacterBody3D) {
    enum class AnimationState(val id: Long) {
        JUMP_UP(0),
        JUMP_DOWN(1),
        STRAFE(2),
        WALK(3),
    }

    private var airborneTime = 100.0
    private var orientation = Transform3D.IDENTITY
    private var rootMotion = Transform3D.IDENTITY
    @ScriptProperty
    var motion = Vector2.ZERO
    private var initialPosition = Vector3.ZERO
    private var ready = false

    private lateinit var inputSynchronizer: MultiplayerSynchronizer
    private lateinit var playerInput: PlayerInputSynchronizer
    private lateinit var animationTree: AnimationTree
    private lateinit var playerModel: Node3D
    private lateinit var shootFrom: Marker3D
    private lateinit var shootParticle: CPUParticles3D
    private lateinit var muzzleFlash: CPUParticles3D
    private lateinit var fireCooldown: Timer
    private lateinit var soundEffectJump: AudioStreamPlayer
    private lateinit var soundEffectLand: AudioStreamPlayer
    private lateinit var soundEffectShoot: AudioStreamPlayer

    @ScriptProperty(name = "player_id")
    var playerId = 1L
        set(value) {
            field = value
            if (::inputSynchronizer.isInitialized) {
                inputSynchronizer.setMultiplayerAuthority(value.toInt())
            }
        }

    @ScriptProperty(name = "current_animation")
    var currentAnimation = AnimationState.WALK.id

    @OnEnterTree
    fun enterTree() {
        inputSynchronizer = self.requireAs("InputSynchronizer", ::MultiplayerSynchronizer)
        inputSynchronizer.setMultiplayerAuthority(playerId.toInt())
    }

    @OnReady
    fun ready() {
        playerInput = Node(inputSynchronizer.handle).kotlinScriptInstance<PlayerInputSynchronizer>()
            ?: error("InputSynchronizer is missing PlayerInputSynchronizer")
        animationTree = self.requireAs("AnimationTree", ::AnimationTree)
        playerModel = self.requireAs("PlayerModel", ::Node3D)
        shootFrom = playerModel.requireAs("Robot_Skeleton/Skeleton3D/GunBone/ShootFrom", ::Marker3D)
        shootParticle = shootFrom.requireAs("ShootParticle", ::CPUParticles3D)
        muzzleFlash = shootFrom.requireAs("MuzzleFlash", ::CPUParticles3D)
        fireCooldown = self.requireAs("FireCooldown", ::Timer)
        val soundEffects = self.requireAs("SoundEffects", ::Node)
        soundEffectJump = soundEffects.requireAs("Jump", ::AudioStreamPlayer)
        soundEffectLand = soundEffects.requireAs("Land", ::AudioStreamPlayer)
        soundEffectShoot = soundEffects.requireAs("Shoot", ::AudioStreamPlayer)
        initialPosition = self.transform.origin
        orientation = playerModel.globalTransform.withOrigin(Vector3.ZERO)
        ready = true
        if (self.getMultiplayer()?.isServer() != true) {
            self.setProcess(false)
        }
    }

    @OnPhysicsProcess
    fun physicsProcess(delta: Double) {
        if (self.getMultiplayer()?.isServer() == true) {
            applyInput(delta)
        } else {
            animate(currentAnimation, delta)
        }
    }

    @RegisterFunction
    fun animate(anim: Long, delta: Double = 0.0) {
        currentAnimation = anim
        when (anim) {
            AnimationState.JUMP_UP.id -> animationTree.set("parameters/state/transition_request", "jump_up")
            AnimationState.JUMP_DOWN.id -> animationTree.set("parameters/state/transition_request", "jump_down")
            AnimationState.STRAFE.id -> {
                animationTree.set("parameters/state/transition_request", "strafe")
                animationTree.set("parameters/aim/add_amount", playerInput.getAimRotation())
                animationTree.set("parameters/strafe/blend_position", Vector2(motion.x, -motion.y))
            }
            else -> {
                animationTree.set("parameters/aim/add_amount", 0.0)
                animationTree.set("parameters/state/transition_request", "walk")
                animationTree.set("parameters/walk/blend_position", Vector2(motion.length(), 0.0))
            }
        }
    }

    private fun applyInput(delta: Double) {
        motion = motion.lerp(playerInput.motion, MOTION_INTERPOLATE_SPEED * delta)

        val cameraBasis = playerInput.getCameraRotationBasis()
        var cameraZ = cameraBasis.z.withY(0.0).normalized()
        var cameraX = cameraBasis.x.withY(0.0).normalized()

        airborneTime += delta
        if (self.isOnFloor()) {
            if (airborneTime > 0.5) land()
            airborneTime = 0.0
        }
        var onAir = airborneTime > MIN_AIRBORNE_TIME
        if (!onAir && playerInput.jumping) {
            self.velocity = self.velocity.withY(JUMP_SPEED)
            onAir = true
            airborneTime = MIN_AIRBORNE_TIME
            jump()
        }
        playerInput.jumping = false

        if (onAir) {
            animate(if (self.velocity.y > 0.0f) AnimationState.JUMP_UP.id else AnimationState.JUMP_DOWN.id, delta)
        } else if (playerInput.aiming) {
            val qFrom = orientation.basis.getRotationQuaternion()
            val qTo = playerInput.getCameraBaseQuaternion()
            orientation = orientation.withBasis(Basis(qFrom.slerp(qTo, delta * ROTATION_INTERPOLATE_SPEED)))
            animate(AnimationState.STRAFE.id, delta)
            rootMotion = Transform3D(Basis(animationTree.getRootMotionRotation()), animationTree.getRootMotionPosition())
            if (playerInput.shooting && fireCooldown.getTimeLeft() == 0.0) {
                shootBullet()
                shoot()
            }
        } else {
            val target = cameraX * motion.x + cameraZ * motion.y
            if (target.length() > 0.001) {
                val qFrom = orientation.basis.getRotationQuaternion()
                val qTo = Basis.lookingAt(target).getRotationQuaternion()
                orientation = orientation.withBasis(Basis(qFrom.slerp(qTo, delta * ROTATION_INTERPOLATE_SPEED)))
            }
            animate(AnimationState.WALK.id, delta)
            rootMotion = Transform3D(Basis(animationTree.getRootMotionRotation()), animationTree.getRootMotionPosition())
        }

        orientation = composeTransforms(orientation, rootMotion)
        val horizontal = orientation.origin / delta
        self.velocity = self.velocity.withX(horizontal.x.toDouble()).withZ(horizontal.z.toDouble())
        self.velocity = self.velocity + self.getGravity() * delta
        self.setUpDirection(Vector3.UP)
        self.moveAndSlide()

        orientation = orientation.withOrigin(Vector3.ZERO).orthonormalized()
        playerModel.globalTransform = playerModel.globalTransform.withBasis(orientation.basis)
        if (self.transform.origin.y < -40.0f) {
            self.transform = self.transform.withOrigin(initialPosition)
        }
    }

    private fun shootBullet() {
        val shootOrigin = shootFrom.globalTransform.origin
        val shootDir = (playerInput.shootTarget - shootOrigin).normalized()
        val bulletNode = TpsScenes.instantiate(TpsScenes.BULLET) ?: return
        self.getParent()?.addChild(bulletNode, true)
        val bullet = CharacterBody3D(bulletNode.handle)
        bullet.globalTransform = bullet.globalTransform.withOrigin(shootOrigin)
        bullet.lookAt(shootOrigin + shootDir)
        bullet.addCollisionExceptionWith(self)
    }

    @RegisterFunction
    @Rpc(callLocal = true)
    fun jump() {
        animate(AnimationState.JUMP_UP.id, 0.0)
        soundEffectJump.play()
    }

    @RegisterFunction
    @Rpc(callLocal = true)
    fun land() {
        animate(AnimationState.JUMP_DOWN.id, 0.0)
        soundEffectLand.play()
    }

    @RegisterFunction
    @Rpc(callLocal = true)
    fun shoot() {
        restartEmitting(shootParticle)
        restartEmitting(muzzleFlash)
        fireCooldown.start()
        soundEffectShoot.play()
        addCameraShakeTrauma(0.35)
    }

    @RegisterFunction
    @Rpc(callLocal = true)
    fun hit() {
        addCameraShakeTrauma(0.75)
    }

    @RegisterFunction("add_camera_shake_trauma")
    @Rpc(callLocal = true)
    fun addCameraShakeTrauma(amount: Double) {
        playerInput.addCameraShakeTrauma(amount)
    }

    private fun restartEmitting(particles: CPUParticles3D) {
        particles.restart()
        particles.emitting = true
    }

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

    private companion object {
        const val MOTION_INTERPOLATE_SPEED = 10.0
        const val ROTATION_INTERPOLATE_SPEED = 10.0
        const val MIN_AIRBORNE_TIME = 0.1
        const val JUMP_SPEED = 5.0
    }
}
