package citybuilder

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.Process
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AudioStreamPlayer
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node")
class Audio(godotObject: MemorySegment) : KanamaScript<Node>(godotObject, ::Node) {

    private val numPlayers = 12
    private val bus = "master"

    // GDScript `available = []`: pooled players ready to play the next sound.
    private val available = ArrayDeque<AudioStreamPlayer>()

    // GDScript `queue = []` of dictionaries; Kotlin uses a typed data class.
    private val queue = ArrayDeque<QueuedSound>()

    data class QueuedSound(val path: String, val volume: Double)

    @OnReady
    fun ready() {
        repeat(numPlayers) {
            val player = AudioStreamPlayer.create()
            self.addChild(player)
            player.setVolumeDb(-10.0)
            player.signal("finished").connect(self, argumentCount = 0) { onStreamFinished(player) }
            player.setBus(bus)
            available.addLast(player)
        }
    }

    @RegisterFunction("_on_stream_finished")
    fun onStreamFinished(player: AudioStreamPlayer) {
        available.addLast(player)
    }

    fun play(soundPath: String, volumeDb: Double = -10.0) {
        // Path, or multiple paths separated by commas.
        val sounds = soundPath.split(",")
        val chosen = "res://" + sounds[(GD.randi() % sounds.size).toInt()].trim()
        queue.addLast(QueuedSound(chosen, volumeDb))
    }

    @Process
    fun process(delta: Double) {
        if (queue.isNotEmpty() && available.isNotEmpty()) {
            val item = queue.removeFirst()
            val player = available.removeFirst()
            player.setStreamFromPath(item.path)
            player.setVolumeDb(item.volume)
            player.setPitchScale(GD.randfRange(0.9, 1.1))
            player.play()
        }
    }
}
