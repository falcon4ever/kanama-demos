package fps

import net.multigesture.kanama.annotations.Export
import net.multigesture.kanama.annotations.ExportRange
import net.multigesture.kanama.annotations.ExportSubgroup
import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.Texture2D
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector3
import net.multigesture.kanama.web.KanamaWebScript

/**
 * Web port of the Weapon resource. The generated Web proxy carries the .tres-authored values and
 * pushes them into this instance when the owning Player's weapons array hydrates.
 */
@ScriptClass(attachTo = "Resource")
@GlobalClass
class Weapon(objectId: GodotHandle) : KanamaWebScript(objectId) {
  @ExportSubgroup("Model") @Export var model: PackedScene? = null

  @Export var position: Vector3 = Vector3.ZERO

  @Export var rotation: Vector3 = Vector3.ZERO

  @Export var muzzlePosition: Vector3 = Vector3.ZERO

  @ExportSubgroup("Properties")
  @ExportRange(0.1, 1.0)
  var cooldown: Double = 0.1

  @ExportRange(1.0, 20.0, 1.0) var maxDistance: Long = 10

  @ExportRange(0.0, 100.0) var damage: Double = 25.0

  @ExportRange(0.0, 5.0) var spread: Double = 0.0

  @ExportRange(1.0, 5.0, 1.0) var shotCount: Long = 1

  @ExportRange(0.0, 50.0, 1.0) var knockback: Long = 20

  @Export var minKnockback: Vector2 = Vector2(0.001, 0.001)

  @Export var maxKnockback: Vector2 = Vector2(0.0025, 0.002)

  @ExportSubgroup("Sounds") @Export var soundShoot: String = ""

  @ExportSubgroup("Crosshair") @Export var crosshair: Texture2D? = null

  /**
   * Harness-only: releases the hydrated sub-resource handles so the smoke's teardown can drain to
   * zero (Weapon resources themselves persist in Godot's resource cache).
   */
  fun releaseHydratedAssets() {
    crosshair?.close()
    crosshair = null
    model?.close()
    model = null
  }
}
