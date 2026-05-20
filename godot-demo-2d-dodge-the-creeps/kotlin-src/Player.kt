package dodge

import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.AnimatedSprite2D
import net.multigesture.kanama.api.Area2D
import net.multigesture.kanama.api.CollisionShape2D
import net.multigesture.kanama.api.GPUParticles2D
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node2D
import net.multigesture.kanama.generated.PlayerSignals
import net.multigesture.kanama.types.Vector2
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Area2D")
class Player(godotObject: MemorySegment) : KanamaScript<Area2D>(godotObject, ::Area2D) {
    /** How fast the player will move, in pixels per second. */
    @ScriptProperty
    var speed: Long = 400

    /** Emitted when the player is hit by a mob. */
    @Signal
    fun hit() = Unit

    private lateinit var screenSize: Vector2
    private lateinit var animatedSprite: AnimatedSprite2D
    private lateinit var trail: GPUParticles2D
    private lateinit var collisionShape: CollisionShape2D

    @OnReady
    fun ready() {
        screenSize = self.getViewport()?.getVisibleRect()?.size ?: Vector2.ZERO
        animatedSprite = self.requireAs("AnimatedSprite2D", ::AnimatedSprite2D)
        trail = self.requireAs("Trail", ::GPUParticles2D)
        collisionShape = self.requireAs("CollisionShape2D", ::CollisionShape2D)
        self.hide()
    }

    @OnProcess
    fun process(delta: Double) {
        var velocity = Vector2.ZERO // The player's movement vector.
        if (Input.isActionPressed("move_right")) velocity = velocity.withX(velocity.x + 1f)
        if (Input.isActionPressed("move_left")) velocity = velocity.withX(velocity.x - 1f)
        if (Input.isActionPressed("move_down")) velocity = velocity.withY(velocity.y + 1f)
        if (Input.isActionPressed("move_up")) velocity = velocity.withY(velocity.y - 1f)

        if (velocity.length() > 0.0) {
            velocity = velocity.normalized() * speed
            animatedSprite.play()
        } else {
            animatedSprite.stop()
        }

        self.position = (self.position + velocity * delta).clamp(Vector2.ZERO, screenSize)

        if (velocity.x != 0f) {
            animatedSprite.animation = "right"
            self.rotation = 0.0
            animatedSprite.flipV = false
            trail.rotation = 0.0
            animatedSprite.flipH = velocity.x < 0f
        } else if (velocity.y != 0f) {
            animatedSprite.animation = "up"
            self.rotation = if (velocity.y > 0f) Mathf.PI else 0.0
        }
    }

    @RegisterFunction
    fun start(pos: Vector2) {
        self.position = pos
        self.rotation = 0.0
        self.show()
        collisionShape.setDisabled(false)
    }

    @RegisterFunction("_on_body_entered")
    fun onBodyEntered(_body: Node2D) {
        self.hide() // Player disappears after being hit.
        PlayerSignals.hit(this)
        // Must be deferred as we can't change physics properties on a physics callback.
        collisionShape.setDeferred("disabled", true)
    }
}
