package fps

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.Process
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.AnimatedSprite3D
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.RayCast3D
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.types.Vector3

/**
 * Web port of the FPS enemy. Adaptations: look_at rides look_at_from_position at the current
 * position (up/model-front baked — the flying-eye sprite is billboarded, so the front flip is
 * invisible); the return-fire hit resolves the Player script directly through the tracked collider
 * handle instead of the generated PlayerMethods dispatch.
 */
@ScriptClass(attachTo = "Area3D")
class Enemy(godotObject: GodotHandle) : KanamaScript<Area3D>(godotObject, ::Area3D) {

  @ScriptProperty var player: Node3D? = null

  private lateinit var raycast: RayCast3D
  private lateinit var muzzleA: AnimatedSprite3D
  private lateinit var muzzleB: AnimatedSprite3D

  private var health = 100.0
  private var time = 0.0
  private var targetPosition = Vector3.ZERO
  private var destroyed = false

  @OnReady
  fun ready() {
    if (player == null) error("Enemy requires a player Node3D")
    raycast = self.requireAs("RayCast", ::RayCast3D)
    muzzleA = self.requireAs("MuzzleA", ::AnimatedSprite3D)
    muzzleB = self.requireAs("MuzzleB", ::AnimatedSprite3D)
    targetPosition = self.position
  }

  @Process
  fun process(delta: Double) {
    val target = player ?: return
    self.lookAtFromPosition(self.position, target.position + Vector3(0.0, 0.5, 0.0))
    targetPosition = targetPosition.withY(targetPosition.y + Mathf.cos(time * 5.0) * delta)
    time += delta
    self.position = targetPosition
  }

  @RegisterFunction("damage")
  fun damage(amount: Double) {
    playAudio("sounds/enemy_hurt.ogg")
    health -= amount

    if (health <= 0.0 && !destroyed) {
      destroy()
    }
  }

  @RegisterFunction("destroy")
  fun destroy() {
    playAudio("sounds/enemy_destroy.ogg")
    destroyed = true
    self.queueFree()
  }

  @RegisterFunction("_on_timer_timeout")
  fun onTimerTimeout() {
    raycast.forceRaycastUpdate()

    if (!raycast.isColliding()) return
    val collider = raycast.getCollider() ?: return
    val hitPlayer = collider.kotlinScriptInstance<Player>() ?: return
    hitPlayer.damage(5.0)

    playMuzzleFlash(muzzleA)
    playMuzzleFlash(muzzleB)
    playAudio("sounds/enemy_attack.ogg")
  }

  private fun playMuzzleFlash(muzzle: AnimatedSprite3D) {
    muzzle.frame = 0
    muzzle.play("default")
    muzzle.rotationDegrees = muzzle.rotationDegrees.withZ(GD.randfRange(-45.0, 45.0))
  }

  private fun playAudio(soundPath: String) {
    val audio = self.getNodeOrNull("/root/Audio") ?: return
    audio.kotlinScriptInstance<Audio>()?.play(soundPath)
  }
}
