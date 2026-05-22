package squash

import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.generated.PlayerSignals
import net.multigesture.kanama.generated.MobMethods
import net.multigesture.kanama.types.Basis
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "CharacterBody3D")
class Player(godotObject: MemorySegment) : KanamaScript<CharacterBody3D>(godotObject, ::CharacterBody3D) {
    /** How fast the player moves in meters per second. */
    @ScriptProperty
    var speed: Long = 14

    /** Vertical impulse applied to the character upon jumping in meters per second. */
    @ScriptProperty
    var jumpImpulse: Long = 20

    /** Vertical impulse applied to the character upon bouncing over a mob in meters per second. */
    @ScriptProperty
    var bounceImpulse: Long = 16

    /** The downward acceleration when in the air, in meters per second. */
    @ScriptProperty
    var fallAcceleration: Long = 75

    @Signal
    fun hit() = Unit

    private lateinit var animationPlayer: AnimationPlayer

    @OnReady
    fun ready() {
        animationPlayer = self.requireAs("AnimationPlayer", ::AnimationPlayer)
    }

    @OnPhysicsProcess
    fun physicsProcess(delta: Double) {
        var direction = Vector3.ZERO
        if (Input.isActionPressed("move_right")) direction = direction.withX(direction.x.toDouble() + 1.0)
        if (Input.isActionPressed("move_left")) direction = direction.withX(direction.x.toDouble() - 1.0)
        if (Input.isActionPressed("move_back")) direction = direction.withZ(direction.z.toDouble() + 1.0)
        if (Input.isActionPressed("move_forward")) direction = direction.withZ(direction.z.toDouble() - 1.0)

        if (direction != Vector3.ZERO) {
            direction = direction.normalized()
            // In the lines below, we turn the character when moving and make the animation play faster.
            // Setting the basis property will affect the rotation of the node.
            self.basis = Basis.lookingAt(direction)
            animationPlayer.setSpeedScale(4.0)
        } else {
            animationPlayer.setSpeedScale(1.0)
        }

        var velocity = self.velocity
        velocity = velocity
            .withX(direction.x.toDouble() * speed.toDouble())
            .withZ(direction.z.toDouble() * speed.toDouble())

        // Jumping.
        if (self.isOnFloor() && Input.isActionJustPressed("jump")) {
            velocity = velocity.withY(velocity.y.toDouble() + jumpImpulse.toDouble())
        }

        // We apply gravity every frame so the character always collides with the ground when moving.
        // This is necessary for the is_on_floor() function to work as a body can always detect
        // the floor, walls, etc. when a collision happens the same frame.
        velocity = velocity.withY(velocity.y.toDouble() - fallAcceleration.toDouble() * delta)
        self.velocity = velocity
        self.moveAndSlide()

        // Here, we check if we landed on top of a mob and if so, we kill it and bounce.
        // With move_and_slide(), Godot makes the body move sometimes multiple times in a row to
        // smooth out the character's motion. So we have to loop over all collisions that may have
        // happened. If there are no "slides" this frame, the loop below won't run.
        for (index in 0 until self.getSlideCollisionCount()) {
            var bounced = false
            self.getSlideCollision(index)?.use { collision ->
                val collider = collision.getCollider() ?: return@use
                val colliderNode = Node(collider.handle)
                if (colliderNode.isInGroup("mob") && Vector3.UP.dot(collision.getNormal()) > 0.1) {
                    MobMethods.squash(collider)
                    self.velocity = self.velocity.withY(bounceImpulse)
                    bounced = true
                }
            }
            // Prevent this block from running more than once,
            // which would award the player more than 1 point for squashing a single mob.
            if (bounced) break
        }

        // This makes the character follow a nice arc when jumping.
        val rotation = self.rotation
        self.rotation = rotation.withX(Mathf.PI / 6.0 * self.velocity.y.toDouble() / jumpImpulse.toDouble())
    }

    fun die() {
        PlayerSignals.hit(this)
        self.queueFree()
    }

    @RegisterFunction("_on_MobDetector_body_entered")
    fun onMobDetectorBodyEntered(_body: GodotObject) {
        die()
    }
}
