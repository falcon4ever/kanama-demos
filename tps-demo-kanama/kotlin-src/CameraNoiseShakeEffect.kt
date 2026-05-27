package tps

import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.FastNoiseLite
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Camera3D")
class CameraNoiseShakeEffect(godotObject: MemorySegment) : KanamaScript<Camera3D>(godotObject, ::Camera3D) {
    private var startRotation = Vector3.ZERO
    private var trauma = 0.0
    private var time = 0.0
    private val noise = FastNoiseLite.create()
    private val noiseSeed = GD.randi().toInt()

    @OnReady
    fun ready() {
        noise.seed = noiseSeed
        noise.fractalOctaves = 1
        noise.fractalLacunarity = 1.0
        startRotation = self.rotation
    }

    @OnProcess
    fun process(delta: Double) {
        if (trauma <= 0.0) return
        trauma = Mathf.max(trauma - DECAY_RATE * delta, 0.0)
        time += delta * SPEED * 5000.0
        val shake = trauma * trauma
        val yaw = MAX_YAW * shake * noiseValue(noiseSeed, time)
        val pitch = MAX_PITCH * shake * noiseValue(noiseSeed + 1, time)
        val roll = MAX_ROLL * shake * noiseValue(noiseSeed + 2, time)
        self.rotation = startRotation + Vector3(pitch, yaw, roll)
    }

    @RegisterFunction("add_trauma")
    fun addTrauma(amount: Double) {
        trauma = Mathf.min(trauma + amount, MAX_TRAUMA)
    }

    private fun noiseValue(seed: Int, pos: Double): Double {
        noise.seed = seed
        return noise.getNoise1d(pos)
    }

    private companion object {
        const val SPEED = 1.0
        const val DECAY_RATE = 1.5
        const val MAX_YAW = 0.05
        const val MAX_PITCH = 0.05
        const val MAX_ROLL = 0.1
        const val MAX_TRAUMA = 1.2
    }
}
