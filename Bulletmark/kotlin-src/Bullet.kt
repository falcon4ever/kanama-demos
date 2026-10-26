package net.multigesture.kanama.demos.bulletmark

import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Area2D
import net.multigesture.kanama.api.CollisionShape2D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Shape2D
import net.multigesture.kanama.api.Sprite2D
import net.multigesture.kanama.api.Texture2D
import net.multigesture.kanama.api.isScript
import net.multigesture.kanama.types.Vector2

/**
 * One bullet: an Area2D with its own script, moved in its own `_physics_process` and told about hits through the
 * engine's `area_entered` signal, the way a game written one script per entity does it. It frees itself on its first
 * hit, on another bullet or a drone, or when its lifetime runs out, whichever comes first.
 *
 * With a texture it carries its own Sprite2D, as a bullet scene would; without one the arena draws it.
 */
@ScriptClass(attachTo = "Area2D")
class Bullet(godotObject: GodotHandle) : KanamaScript<Area2D>(godotObject, ::Area2D) {
    private lateinit var arena: Arena
    internal var team = 0
        private set
    private var velocityX = 0.0
    private var framesLeft = 0
    private var finished = false

    internal fun launch(
        arena: Arena,
        team: Int,
        x: Double,
        y: Double,
        velocityX: Double,
        lifetimeFrames: Int,
        shape: Shape2D,
        texture: Texture2D?,
    ) {
        this.arena = arena
        this.team = team
        this.velocityX = velocityX
        framesLeft = lifetimeFrames
        self.position = Vector2(x, y)
        // Each team sits on its own layer and scans the other team and the drones, never its own bullets.
        self.collisionLayer = if (team == 0) Layers.TEAM_A else Layers.TEAM_B
        self.collisionMask = (if (team == 0) Layers.TEAM_B else Layers.TEAM_A) or Layers.DRONE
        self.addChild(CollisionShape2D.create().also { it.shape = shape })
        if (texture != null) self.addChild(Sprite2D.create().also { it.texture = texture })
        // Without hits the bullet neither detects nor is detected, so the engine tracks no overlaps for it either.
        if (arena.hits) self.areaEntered.connect { other -> onAreaEntered(other) } else self.monitoring = false
        self.monitorable = arena.hits
    }

    @OnPhysicsProcess
    fun physicsProcess(delta: Double) {
        ScriptCalls.count++
        if (finished) return
        if (--framesLeft <= 0) {
            finish()
            arena.recordExpired(this)
            return
        }
        val moved = self.position
        self.position = Vector2(moved.x + velocityX * delta, moved.y)
    }

    private fun onAreaEntered(other: Area2D) {
        ScriptCalls.count++
        if (finished) return
        finish()
        arena.recordBulletHit(this, hitDrone = other.isScript<Drone>())
    }

    private fun finish() {
        finished = true
        self.queueFree()
    }
}
