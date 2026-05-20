package thirdperson

import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node3D")
class GrenadeVisual(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {
    private val rotationAxis = Vector3(1f, 0f, 0f).normalized()

    @OnReady
    fun ready() {
        self.requireAs("AnimationPlayer", ::AnimationPlayer).play("wave")
    }

    @OnProcess
    fun process(delta: Double) {
        self.rotateObjectLocal(rotationAxis, 10.0 * delta)
    }
}
