package fps

import net.multigesture.kanama.annotations.Export
import net.multigesture.kanama.annotations.ExportSubgroup
import net.multigesture.kanama.annotations.GlobalClass
import net.multigesture.kanama.annotations.PropertyHint
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.Resource
import net.multigesture.kanama.api.Texture2D
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Resource")
@GlobalClass
class Weapon(godotObject: MemorySegment) : KanamaScript<Resource>(godotObject, Resource::fromHandle) {
    @ExportSubgroup("Model")
    @ScriptProperty
    var model: PackedScene? = null

    @ScriptProperty
    var position: Vector3 = Vector3.ZERO

    @ScriptProperty
    var rotation: Vector3 = Vector3.ZERO

    @ScriptProperty
    var muzzlePosition: Vector3 = Vector3.ZERO

    @ExportSubgroup("Properties")
    @Export(hint = PropertyHint.RANGE, hintString = "0.1,1")
    var cooldown: Double = 0.1

    @Export(hint = PropertyHint.RANGE, hintString = "1,20,1")
    var maxDistance: Long = 10

    @Export(hint = PropertyHint.RANGE, hintString = "0,100")
    var damage: Double = 25.0

    @Export(hint = PropertyHint.RANGE, hintString = "0,5")
    var spread: Double = 0.0

    @Export(hint = PropertyHint.RANGE, hintString = "1,5,1")
    var shotCount: Long = 1

    @Export(hint = PropertyHint.RANGE, hintString = "0,50,1")
    var knockback: Long = 20

    @ScriptProperty
    var minKnockback: Vector2 = Vector2(0.001f, 0.001f)

    @ScriptProperty
    var maxKnockback: Vector2 = Vector2(0.0025f, 0.002f)

    @ExportSubgroup("Sounds")
    @ScriptProperty
    var soundShoot: String = ""

    @ExportSubgroup("Crosshair")
    @ScriptProperty
    var crosshair: Texture2D? = null
}
