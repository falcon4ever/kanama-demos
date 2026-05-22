package squash

import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.generated.MobSignals
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "CharacterBody3D")
class Mob(godotObject: MemorySegment) : KanamaScript<CharacterBody3D>(godotObject, ::CharacterBody3D) {
    private val animationPlayer by lazy { self.requireAs("AnimationPlayer", ::AnimationPlayer) }

    /** Minimum speed of the mob in meters per second. */
    @ScriptProperty
    var minSpeed: Long = 10

    /** Maximum speed of the mob in meters per second. */
    @ScriptProperty
    var maxSpeed: Long = 18

    /** Emitted when the player jumped on the mob. */
    @Signal
    fun squashed() = Unit

    @OnPhysicsProcess
    fun physicsProcess(_delta: Double) {
        self.moveAndSlide()
    }

    @RegisterFunction
    fun initialize(startPosition: Vector3, playerPosition: Vector3) {
        // Ignore the player's height, so that the mob's orientation is not slightly
        // shifted if the mob spawns while the player is jumping.
        val target = Vector3(playerPosition.x, startPosition.y, playerPosition.z)
        self.lookAtFromPosition(startPosition, target, Vector3.UP)
        // Rotate this mob randomly within range of -45 and +45 degrees,
        // so that it doesn't move directly towards the player.
        self.rotateY(GD.randfRange(-Mathf.PI / 4.0, Mathf.PI / 4.0))

        // We calculate a forward velocity first, which represents the speed.
        val randomSpeed = GD.randfRange(minSpeed.toDouble(), maxSpeed.toDouble())
        var velocity = Vector3.FORWARD * randomSpeed
        // We then rotate the vector based on the mob's Y rotation to move in the direction it's looking.
        velocity = velocity.rotated(Vector3.UP, self.rotation.y.toDouble())
        self.velocity = velocity
        animationPlayer.setSpeedScale(randomSpeed / minSpeed.toDouble())
    }

    @RegisterFunction
    fun squash() {
        MobSignals.squashed(this)
        self.queueFree()
    }

    @RegisterFunction("_on_visible_on_screen_notifier_screen_exited")
    fun onVisibleOnScreenNotifierScreenExited() {
        self.queueFree()
    }
}
