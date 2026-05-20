package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.CollisionShape3D
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Area3D")
class MeleeAttackArea(godotObject: MemorySegment) : KanamaScript<Area3D>(godotObject, ::Area3D) {

    private lateinit var collisionShape: CollisionShape3D

    @OnReady
    fun ready() {
        collisionShape = self.requireAs("CollisionShape3d", ::CollisionShape3D)
        self.signal(Area3D.Signals.bodyEntered).connectObject(self) { body ->
            onBodyEntered(Node3D(body.handle))
        }
    }

    @RegisterFunction
    fun activate() {
        collisionShape.setDeferred("disabled", false)
    }

    @RegisterFunction
    fun deactivate() {
        collisionShape.setDeferred("disabled", true)
    }

    private fun onBodyEntered(body: Node3D) {
        if (!body.isInGroup("damageables")) return

        val impactPoint = self.globalPosition - body.globalPosition
        val force = -impactPoint
        body.call("damage", impactPoint, force)
    }
}
