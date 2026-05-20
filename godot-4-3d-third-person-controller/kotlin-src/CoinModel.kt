package thirdperson

import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.Time
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node3D")
class CoinModel(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {
    @ScriptProperty
    var yAmplitude: Double = 0.04

    @OnProcess
    fun process(delta: Double) {
        val rotation = self.rotation
        self.rotation = rotation.withY(rotation.y + 1.5 * delta)

        val position = self.position
        val timeSeconds = Time.getTicksMsec() / 1000.0
        self.position = position.withY((Mathf.sin(timeSeconds) * yAmplitude).toFloat())
    }
}
