package net.multigesture.kanama.demos.bunnymark

import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.RandomNumberGenerator
import net.multigesture.kanama.api.Sprite2D
import net.multigesture.kanama.types.Vector2

@ScriptClass(attachTo = "Sprite2D")
class BunnyKanama(godotObject: GodotHandle) : KanamaScript<Sprite2D>(godotObject, ::Sprite2D) {

    internal var speed = Vector2(0.0, 0.0)

    private val gravity = 500.0
    private var screenSize = Vector2(0.0, 0.0)
    // One generator per bunny, which is deliberate: this benchmark exists to measure what many small scripts cost,
    // and an object of its own per bunny is part of that. Seeded by the spawner rather than here, with a seed of its
    // own per bunny, because a shared seed would have every bunny bounce in exactly the same way.
    private val bounceRandom = RandomNumberGenerator.create()

    internal fun seedBounce(seed: Long) {
        bounceRandom.seed = seed
    }

    @OnProcess
    fun process(delta: Double) {
        screenSize = self.getViewportRect().size
        val position = self.position
        var x = position.x + speed.x * delta
        var y = position.y + speed.y * delta
        var speedX = speed.x
        var speedY = speed.y + gravity * delta

        if (x > screenSize.x) {
            speedX *= -1.0
            x = screenSize.x
        }

        if (x < 0.0) {
            speedX *= -1.0
            x = 0.0
        }

        if (y > screenSize.y) {
            y = screenSize.y
            if (bounceRandom.randf() > 0.5) {
                speedY = -(bounceRandom.randi() % 1100 + 50).toDouble()
            } else {
                speedY *= -0.85
            }
        }

        if (y < 0.0) {
            speedY = 0.0
            y = 0.0
        }

        self.position = Vector2(x, y)
        speed = Vector2(speedX, speedY)
    }
}
