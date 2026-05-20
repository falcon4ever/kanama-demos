package fps

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
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
    private val players = mutableListOf<AudioStreamPlayer>()
    private val available = ArrayDeque<AudioStreamPlayer>()
    private val queue = ArrayDeque<String>()

    @OnReady
    fun ready() {
        for (i in 0 until numPlayers) {
            val player = AudioStreamPlayer.create()
            self.addChild(player)
            players += player
            available.addLast(player)
            player.setVolumeDb(-10.0)
            player.signal("finished").connect(self, argumentCount = 0) { onStreamFinished(player) }
            player.setBus(bus)
        }
    }

    @RegisterFunction("_on_stream_finished")
    fun onStreamFinished(player: AudioStreamPlayer) {
        available.addLast(player)
    }

    fun play(soundPath: String) {
        val sounds = soundPath.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (sounds.isEmpty()) return

        val selected = sounds[(GD.randi() % sounds.size.toLong()).toInt()]
        queue.addLast(if (selected.startsWith("res://")) selected else "res://$selected")
    }

    @OnProcess
    fun process(delta: Double) {
        if (queue.isEmpty() || available.isEmpty()) return

        val player = available.removeFirst()
        player.setStreamFromPath(queue.removeFirst())
        player.play()
        player.setPitchScale(GD.randfRange(0.9, 1.1))
    }

    @OnExitTree
    fun exitTree() {
        stopAll()
    }

    fun stopAll() {
        queue.clear()
        available.clear()
        for (player in players) {
            player.stop()
            player.setStream(null)
        }
    }

}
