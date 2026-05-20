package thirdperson

import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.AudioStreamPlayer3D
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.ProjectSettings
import net.multigesture.kanama.api.Timer
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "CharacterBody3D")
class Grenade(godotObject: MemorySegment) : KanamaScript<CharacterBody3D>(
    godotObject,
    ::CharacterBody3D,
),
    KanamaCoroutineOwner {

    override val kanamaScope = KanamaScope()

    private val gravity = ProjectSettings.getSettingDouble("physics/3d/default_gravity")

    private lateinit var explosionArea: Area3D
    private lateinit var explosionSound: AudioStreamPlayer3D
    private lateinit var explosionStartTimer: Timer

    private var grenadeVelocity = Vector3.ZERO

    @OnReady
    fun ready() {
        explosionArea = self.requireAs("ExplosionArea", ::Area3D)
        explosionSound = self.requireAs("ExplosionSound", ::AudioStreamPlayer3D)
        explosionStartTimer = self.requireAs("ExplosionStartTimer", ::Timer)

        explosionStartTimer.signal(Timer.Signals.timeout).connect(self, argumentCount = 0) { explode() }
    }

    @OnPhysicsProcess
    fun physicsProcess(delta: Double) {
        grenadeVelocity += Vector3.DOWN * gravity * delta
        val collision = self.moveAndCollide(grenadeVelocity * delta)
        if (collision != null) {
            grenadeVelocity = grenadeVelocity.bounce(collision.getNormal()) * BOUNCE_DAMPING
            collision.close()
            if (explosionStartTimer.isStopped()) {
                explosionStartTimer.start()
            }
        }
    }

    @RegisterFunction("throw")
    fun throwGrenade(throwVelocity: Vector3) {
        grenadeVelocity = throwVelocity
    }

    private fun explode() {
        self.setPhysicsProcess(false)

        explosionSound.setPitchScale(GD.randfn(2.0, 0.1))
        explosionSound.play()

        for (body in explosionArea.getOverlappingBodies()) {
            if (body.isInGroup("damageables") && !Node3D(body.handle).isPlayer()) {
                var impactPoint = (self.globalPosition - body.globalPosition).normalized()
                impactPoint = (impactPoint + Vector3.DOWN).normalized() * 0.5
                val force = -impactPoint * 10.0
                body.call("damage", impactPoint, force)
            }
        }

        val explosion = DemoScenes.instantiate(DemoScenes.EXPLOSION)
        if (explosion != null) {
            self.getParent()?.addChild(explosion)
            Node3D(explosion.handle).globalPosition = self.globalPosition
        }

        self.hide()
        kanamaScope.launch {
            explosionSound.signal(AudioStreamPlayer3D.Signals.finished)
                .await(self, argumentCount = 0)
            self.queueFree()
        }
    }

    companion object {
        private const val BOUNCE_DAMPING = 0.7
    }
}
