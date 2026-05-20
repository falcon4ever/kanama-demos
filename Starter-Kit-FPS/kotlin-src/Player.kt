package fps

import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.Export
import net.multigesture.kanama.annotations.ExportSubgroup
import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.PropertyHint
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.AnimatedSprite3D
import net.multigesture.kanama.api.AudioStreamPlayer
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEventMouseMotion
import net.multigesture.kanama.api.MeshInstance3D
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.RayCast3D
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.TextureRect
import net.multigesture.kanama.api.Timer
import net.multigesture.kanama.api.Tween
import net.multigesture.kanama.generated.PlayerSignals
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.kotlinScriptInstance

@ScriptClass(attachTo = "CharacterBody3D")
@GlobalClass
class Player(godotObject: MemorySegment) : KanamaScript<CharacterBody3D>(godotObject, ::CharacterBody3D) {

    @ExportSubgroup("Properties")
    @ScriptProperty
    var movementSpeed: Long = 5

    @Export(hint = PropertyHint.RANGE, hintString = "0,100,1")
    var numberOfJumps: Long = 2

    @ScriptProperty
    var jumpStrength: Long = 8

    @ExportSubgroup("Weapons")
    @ScriptProperty
    var weapons: List<Weapon> = emptyList()

    @ScriptProperty
    var crosshair: TextureRect? = null

    private lateinit var camera: Node3D
    private lateinit var raycast: RayCast3D
    private lateinit var muzzle: AnimatedSprite3D
    private lateinit var container: Node3D
    private lateinit var soundFootsteps: AudioStreamPlayer
    private lateinit var blasterCooldown: Timer

    private lateinit var weapon: Weapon
    private var weaponIndex = 0

    private var mouseSensitivity = 700.0
    private var gamepadSensitivity = 0.075
    private var mouseCaptured = true
    private var movementVelocity = Vector3.ZERO
    private var rotationTarget = Vector3.ZERO
    private var gravity = 0.0
    private var previouslyFloored = false
    private var jumpsRemaining = 0L
    private val containerOffset = Vector3(1.2f, -1.1f, -2.75f)
    private var health = 100L
    private var tween: Tween? = null
    private var impactSceneLoaded = false
    private val impactScene: PackedScene by lazy {
        impactSceneLoaded = true
        ResourceLoader.loadPackedScene("res://objects/impact.tscn")
            ?: error("Unable to load res://objects/impact.tscn")
    }

    @Signal
    fun healthUpdated(health: Long) = Unit

    @OnReady
    fun ready() {
        Input.setMouseMode(Input.MOUSE_MODE_CAPTURED)
        camera = self.requireAs("Head/Camera", ::Node3D)
        raycast = self.requireAs("Head/Camera/RayCast", ::RayCast3D)
        muzzle = self.requireAs("Head/Camera/SubViewportContainer/SubViewport/CameraItem/Muzzle", ::AnimatedSprite3D)
        container = self.requireAs("Head/Camera/SubViewportContainer/SubViewport/CameraItem/Container", ::Node3D)
        soundFootsteps = self.requireAs("SoundFootsteps", ::AudioStreamPlayer)
        blasterCooldown = self.requireAs("Cooldown", ::Timer)
        if (crosshair == null) error("Player requires a crosshair TextureRect")

        weapon = weapons.getOrNull(weaponIndex) ?: error("Player requires at least one Weapon")
        initiateChangeWeapon(weaponIndex)
    }

    @OnProcess
    fun process(delta: Double) {
        handleControls(delta)
        handleGravity(delta)

        val movedForward = self.transform.basis * movementVelocity
        val appliedVelocity = self.velocity.lerp(movedForward, delta * 10.0).withY(-gravity)

        self.velocity = appliedVelocity
        self.moveAndSlide()

        container.position = container.position.lerp(
            containerOffset - (self.basis.inverse() * appliedVelocity / 30.0),
            delta * 10.0,
        )

        soundFootsteps.setStreamPaused(true)
        if (self.isOnFloor() && (Mathf.abs(self.velocity.x) > 1f || Mathf.abs(self.velocity.z) > 1f)) {
            soundFootsteps.setStreamPaused(false)
        }

        camera.position = camera.position.withY(GD.lerpf(camera.position.y.toDouble(), 0.0, delta * 5.0))
        if (self.isOnFloor() && gravity > 1.0 && !previouslyFloored) {
            playAudio("sounds/land.ogg")
            camera.position = camera.position.withY(-0.1)
        }
        previouslyFloored = self.isOnFloor()

        if (self.position.y < -10f) {
            self.getTree().reloadCurrentScene()
        }
    }

    @OnInput
    fun input(event: GodotObject) {
        if (!mouseCaptured) return
        val relative = InputEventMouseMotion.from(event)?.getRelative() ?: return
        handleRotation(relative.x.toDouble(), relative.y.toDouble(), isController = false)
    }

    private fun handleControls(delta: Double) {
        if (Input.isActionJustPressed("mouse_capture")) {
            Input.setMouseMode(Input.MOUSE_MODE_CAPTURED)
            mouseCaptured = true
        }

        if (Input.isActionJustPressed("mouse_capture_exit")) {
            Input.setMouseMode(Input.MOUSE_MODE_VISIBLE)
            mouseCaptured = false
        }

        val input = Input.getVector("move_left", "move_right", "move_forward", "move_back")
        movementVelocity = Vector3(input.x, 0f, input.y).normalized() * movementSpeed.toDouble()

        val rotationInput = Input.getVector("camera_right", "camera_left", "camera_down", "camera_up")
        if (rotationInput != Vector2.ZERO) {
            handleRotation(rotationInput.x.toDouble(), rotationInput.y.toDouble(), isController = true, delta = delta)
        }

        actionShoot()

        if (Input.isActionJustPressed("jump") && jumpsRemaining > 0) {
            actionJump()
        }

        actionWeaponToggle()
    }

    private fun handleRotation(
        xRot: Double,
        yRot: Double,
        isController: Boolean,
        delta: Double = 0.0,
    ) {
        if (isController) {
            rotationTarget -= Vector3(-yRot.toFloat(), -xRot.toFloat(), 0f)
                .limitLength(1.0) * gamepadSensitivity
            rotationTarget = rotationTarget.withX(
                GD.clampf(rotationTarget.x.toDouble(), GD.degToRad(-90.0), GD.degToRad(90.0)),
            )
            camera.rotation = camera.rotation.withX(GD.lerpAngle(camera.rotation.x.toDouble(), rotationTarget.x.toDouble(), delta * 25.0))
            self.rotation = self.rotation.withY(GD.lerpAngle(self.rotation.y.toDouble(), rotationTarget.y.toDouble(), delta * 25.0))
        } else {
            rotationTarget += Vector3(-yRot.toFloat(), -xRot.toFloat(), 0f) / mouseSensitivity
            rotationTarget = rotationTarget.withX(
                GD.clampf(rotationTarget.x.toDouble(), GD.degToRad(-90.0), GD.degToRad(90.0)),
            )
            camera.rotation = camera.rotation.withX(rotationTarget.x)
            self.rotation = self.rotation.withY(rotationTarget.y)
        }
    }

    private fun handleGravity(delta: Double) {
        gravity += 20.0 * delta

        if (gravity < 0.0 && self.isOnCeiling()) {
            gravity = 0.0
        }

        if (gravity > 0.0 && self.isOnFloor()) {
            jumpsRemaining = numberOfJumps
            gravity = 0.0
        }
    }

    private fun actionJump() {
        playAudio("sounds/jump_a.ogg, sounds/jump_b.ogg, sounds/jump_c.ogg")
        gravity = -jumpStrength.toDouble()
        jumpsRemaining -= 1
    }

    private fun actionShoot() {
        if (!Input.isActionPressed("shoot")) return
        if (!blasterCooldown.isStopped()) return

        playAudio(weapon.soundShoot)

        muzzle.play("default")
        muzzle.rotationDegrees = muzzle.rotationDegrees.withZ(GD.randfRange(-45.0, 45.0))
        muzzle.scale = Vector3.ONE * GD.randfRange(0.40, 0.75)
        muzzle.position = container.position - weapon.muzzlePosition

        blasterCooldown.start(weapon.cooldown)

        repeat(weapon.shotCount.toInt()) {
            raycast.setTargetPosition(
                raycast.getTargetPosition()
                    .withX(GD.randfRange(-weapon.spread, weapon.spread))
                    .withY(GD.randfRange(-weapon.spread, weapon.spread)),
            )
            raycast.forceRaycastUpdate()

            if (!raycast.isColliding()) return@repeat

            raycast.getCollider()?.let { collider ->
                if (collider.hasMethod("damage")) {
                    collider.call("damage", weapon.damage)
                }
            }

            val impact = impactScene.instantiate()?.let { AnimatedSprite3D(it.handle) } ?: return@repeat
            impact.play("shot")
            Node(self.getTree().getRoot()).addChild(impact)
            impact.position = raycast.getCollisionPoint() + (raycast.getCollisionNormal() / 10.0)
            impact.lookAt(camera.globalPosition, Vector3.UP, useModelFront = true)
        }

        val knockback = randomVec2(weapon.minKnockback, weapon.maxKnockback)
        container.position = container.position.withZ(container.position.z + 0.25f)
        camera.rotation = camera.rotation.withX(camera.rotation.x + knockback.x)
        self.rotation = self.rotation.withY(self.rotation.y + knockback.y)
        rotationTarget = rotationTarget + Vector3(knockback.x, knockback.y, 0f)
        movementVelocity += Vector3(0f, 0f, weapon.knockback.toFloat())
    }

    private fun actionWeaponToggle() {
        if (!Input.isActionJustPressed("weapon_toggle")) return
        weaponIndex = (weaponIndex + 1).mod(weapons.size)
        initiateChangeWeapon(weaponIndex)
        playAudio("sounds/weapon_change.ogg")
    }

    private fun initiateChangeWeapon(index: Int) {
        weaponIndex = index
        clearTween()
        tween = self.getTree().createTween()
            ?.bindNode(self)
            ?.setEase(Tween.EASE_OUT_IN)
        tween?.tweenProperty(container, "position", containerOffset - Vector3(0f, 1f, 0f), 0.1)
        tween?.tweenCallback(self, "change_weapon")
    }

    private fun clearTween() {
        tween?.let {
            it.kill()
        }
        tween = null
    }

    @RegisterFunction("change_weapon")
    fun changeWeapon() {
        weapon = weapons[weaponIndex]
        val weaponModelScene = weapon.model ?: error("Weapon at index $weaponIndex requires a model PackedScene")
        val weaponCrosshair = weapon.crosshair ?: error("Weapon at index $weaponIndex requires a crosshair Texture2D")

        for (child in container.getChildren()) {
            container.removeChild(child)
            child.queueFree()
        }

        val weaponModel = weaponModelScene.instantiate() ?: error("Weapon at index $weaponIndex failed to instantiate its model")
        container.addChild(weaponModel)

        val weaponNode = Node3D(weaponModel.handle)
        weaponNode.position = weapon.position
        weaponNode.rotationDegrees = weapon.rotation

        for (child in weaponModel.findChildren("*", "MeshInstance3D")) {
            MeshInstance3D(child.handle).setLayerMask(2)
        }

        raycast.setTargetPosition(Vector3(0f, 0f, -1f) * weapon.maxDistance.toDouble())
        crosshair?.texture = weaponCrosshair
    }

    @OnExitTree
    fun exitTree() {
        clearTween()
        if (impactSceneLoaded) {
            impactSceneLoaded = false
        }
    }

    @RegisterFunction("damage")
    fun damage(amount: Double) {
        health -= amount.toLong()
        PlayerSignals.healthUpdated(this, health)

        if (health < 0) {
            self.getTree().reloadCurrentScene()
        }
    }

    private fun randomVec2(min: Vector2, max: Vector2): Vector2 {
        val sign = if (GD.randi() % 2L == 0L) -1.0 else 1.0
        return Vector2(
            GD.randfRange(min.x.toDouble(), max.x.toDouble()).toFloat(),
            (GD.randfRange(min.y.toDouble(), max.y.toDouble()) * sign).toFloat(),
        )
    }

    private fun playAudio(soundPath: String) {
        val audio = self.getNodeOrNull("/root/Audio") ?: return
        audio.kotlinScriptInstance<Audio>()?.play(soundPath)
    }

}
