package fps

import net.multigesture.kanama.annotations.Export
import net.multigesture.kanama.annotations.ExportRange
import net.multigesture.kanama.annotations.ExportSubgroup
import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.Resource
import net.multigesture.kanama.api.Texture2D
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector3

@ScriptClass(attachTo = "Resource")
@GlobalClass
class Weapon(godotObject: GodotHandle) : KanamaScript<Resource>(godotObject, Resource::fromHandle) {
    @ExportSubgroup("Model")
    @Export
    var model: PackedScene? = null

    @Export
    var position: Vector3 = Vector3.ZERO

    @Export
    var rotation: Vector3 = Vector3.ZERO

    @Export
    var muzzlePosition: Vector3 = Vector3.ZERO

    @ExportSubgroup("Properties")
    @ExportRange(0.1, 1.0)
    var cooldown: Double = 0.1

    @ExportRange(1.0, 20.0, 1.0)
    var maxDistance: Long = 10

    @ExportRange(0.0, 100.0)
    var damage: Double = 25.0

    @ExportRange(0.0, 5.0)
    var spread: Double = 0.0

    @ExportRange(1.0, 5.0, 1.0)
    var shotCount: Long = 1

    @ExportRange(0.0, 50.0, 1.0)
    var knockback: Long = 20

    @Export
    var minKnockback: Vector2 = Vector2(0.001, 0.001)

    @Export
    var maxKnockback: Vector2 = Vector2(0.0025, 0.002)

    @ExportSubgroup("Sounds")
    @Export
    var soundShoot: String = ""

    @ExportSubgroup("Crosshair")
    @Export
    var crosshair: Texture2D? = null
}
