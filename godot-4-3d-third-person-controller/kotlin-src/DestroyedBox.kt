package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.RigidBody3D
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node3D")
class DestroyedBox(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {

    @OnReady
    fun ready() {
        val pieces = self.getChildren().shuffled().take(FLYING_PIECES)
        for (pieceNode in pieces) {
            val piece = RigidBody3D(pieceNode.handle)
            piece.show()
            piece.freeze = false
            piece.sleeping = false
            piece.setCollisionMaskValue(1, true)

            val randVector = (Vector3.ONE * 0.5) - Vector3(GD.randf(), GD.randf(), GD.randf())
            piece.applyForce(randVector * THROW_STRENGTH, randVector)
        }
    }

    companion object {
        private const val FLYING_PIECES = 3
        private const val THROW_STRENGTH = 500.0
    }
}
