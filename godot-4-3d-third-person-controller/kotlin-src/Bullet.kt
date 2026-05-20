package thirdperson

import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.AudioStreamPlayer3D
import net.multigesture.kanama.api.Curve
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node3D")
class Bullet(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {

    @ScriptProperty
    var scaleDecay: Curve? = null

    @ScriptProperty
    var distanceLimit: Double = 5.0
        set(value) {
            field = value
            updateAliveLimit()
        }

    @ScriptProperty
    var velocity: Vector3 = Vector3.ZERO
        set(value) {
            field = value
            updateAliveLimit()
            if (isReady && value.length() > 0.0) {
                self.lookAt(self.globalPosition + value)
            }
        }

    @ScriptProperty
    var shooter: Node? = null

    private lateinit var area: Area3D
    private lateinit var bulletVisuals: Node3D
    private lateinit var projectileSound: AudioStreamPlayer3D

    private var timeAlive = 0.0
    private var aliveLimit = 0.0
    private var isReady = false

    @OnReady
    fun ready() {
        area = self.requireAs("Area3d", ::Area3D)
        bulletVisuals = self.requireAs("Bullet", ::Node3D)
        projectileSound = self.requireAs("ProjectileSound", ::AudioStreamPlayer3D)

        area.signal(Area3D.Signals.bodyEntered).connectObject(self) { body ->
            onBodyEntered(Node3D(body.handle))
        }

        isReady = true
        updateAliveLimit()
        if (velocity.length() > 0.0) {
            self.lookAt(self.globalPosition + velocity)
            playProjectileSound()
        } else {
            self.setProcess(false)
            self.setVisible(false)
        }
    }

    fun launch(newShooter: Node, origin: Vector3, newVelocity: Vector3, newDistanceLimit: Double) {
        shooter = newShooter
        timeAlive = 0.0
        distanceLimit = newDistanceLimit
        velocity = newVelocity
        self.globalPosition = origin
        bulletVisuals.scale = Vector3.ONE
        self.setVisible(true)
        self.setProcess(true)
        if (newVelocity.length() > 0.0) {
            self.lookAt(origin + newVelocity)
        }
        playProjectileSound()
    }

    @OnProcess
    fun process(delta: Double) {
        self.globalPosition = self.globalPosition + velocity * delta
        timeAlive += delta

        val progress = if (aliveLimit > 0.0) timeAlive / aliveLimit else 1.0
        val sample = scaleDecay?.sample(progress) ?: 1.0
        bulletVisuals.scale = Vector3.ONE * sample

        if (timeAlive > aliveLimit) {
            finish()
        }
    }

    private fun updateAliveLimit() {
        val speed = velocity.length()
        aliveLimit = if (speed > 0.0) distanceLimit / speed else 0.0
    }

    private fun onBodyEntered(body: Node3D) {
        if (shooter?.handle?.address() == body.handle.address()) return

        if (body.isInGroup("damageables")) {
            val impactPoint = self.globalPosition - body.globalPosition
            body.call("damage", impactPoint, velocity)
        }
        finish()
    }

    private fun finish() {
        velocity = Vector3.ZERO
        timeAlive = 0.0
        bulletVisuals.scale = Vector3.ONE
        if (projectileSound.isPlaying()) {
            projectileSound.stop()
        }
        if (!DemoScenes.recycleBullet(self)) {
            self.queueFree()
        }
    }

    private fun playProjectileSound() {
        projectileSound.setPitchScale(GD.randfn(1.0, 0.1))
        projectileSound.play()
    }
}
