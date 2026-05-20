import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AudioStreamPlayer
import net.multigesture.kanama.api.Node
import java.lang.foreign.MemorySegment
import net.multigesture.kanama.api.KanamaScript

@ScriptClass(attachTo = "Node")
class Audio(godotObject: MemorySegment) : KanamaScript<Node>(godotObject, ::Node) {

    private val numPlayers = 12
    private val bus = "master"
    private val available = ArrayDeque<AudioStreamPlayer>()
    private val queue = ArrayDeque<SoundRequest>()
    private val activeSounds = LinkedHashMap<String, MutableList<AudioStreamPlayer>>()

    @OnReady
    fun ready() {
        for (i in 0 until numPlayers) {
            val player = AudioStreamPlayer.create()
            self.addChild(player)
            available.addLast(player)
            player.setVolumeDb(-10.0)
            player.signal("finished").connect(self, argumentCount = 0) { onStreamFinished(player) }
            player.setBus(bus)
        }
    }

    @RegisterFunction("_on_stream_finished")
    fun onStreamFinished(player: AudioStreamPlayer) {
        for ((path, players) in activeSounds) {
            if (players.removeIf { it.handle.address() == player.handle.address() }) {
                if (players.isEmpty()) activeSounds.remove(path)
                break
            }
        }
        available.addLast(player)
    }

    fun play(soundPath: String, allowOverlap: Boolean = false, pitch: Double = 1.0, volume: Double = 1.0) {
        if (allowOverlap) {
            queue.addLast(SoundRequest(soundPath, overlap = true, pitch = pitch, volume = volume))
        } else if (!activeSounds.containsKey(soundPath) && queue.none { it.path == soundPath }) {
            queue.addLast(SoundRequest(soundPath, overlap = false, pitch = pitch, volume = volume))
        }
    }

    @OnProcess
    fun process(delta: Double) {
        if (queue.isEmpty() || available.isEmpty()) return

        val data = queue.removeFirst()
        val player = available.removeFirst()

        if (!data.overlap) {
            activeSounds.getOrPut(data.path) { mutableListOf() }.add(player)
        }

        player.setStreamFromPath(data.path)
        player.setPitchScale(data.pitch)
        player.setVolumeDb(linearToDb(data.volume))
        player.play()
    }

    // Utility for decibels
    private fun linearToDb(linear: Double): Double =
        if (linear > 0.0) 20.0 * Mathf.log(linear) / Mathf.log(10.0) else -80.0

    private data class SoundRequest(
        val path: String,
        val overlap: Boolean,
        val pitch: Double,
        val volume: Double,
    )

}
