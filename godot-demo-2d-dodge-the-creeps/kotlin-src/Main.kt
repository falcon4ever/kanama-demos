package dodge

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.AudioStreamPlayer
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Marker2D
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node2D
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.PathFollow2D
import net.multigesture.kanama.api.RigidBody2D
import net.multigesture.kanama.api.Timer
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.types.Vector2
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node")
class Main(godotObject: MemorySegment) : KanamaScript<Node>(godotObject, ::Node) {
    @ScriptProperty
    var mobScene: PackedScene? = null

    private var score: Long = 0

    private lateinit var player: Player
    private lateinit var startPosition: Marker2D
    private lateinit var mobSpawnLocation: PathFollow2D
    private lateinit var hud: HUD
    private lateinit var scoreTimer: Timer
    private lateinit var mobTimer: Timer
    private lateinit var startTimer: Timer
    private lateinit var music: AudioStreamPlayer
    private lateinit var deathSound: AudioStreamPlayer

    @OnReady
    fun ready() {
        player = self.requireAs("Player", ::Node).kotlinScriptInstance<Player>()
            ?: error("Player node is missing Player script")
        startPosition = self.requireAs("StartPosition", ::Marker2D)
        mobSpawnLocation = self.requireAs("MobPath/MobSpawnLocation", ::PathFollow2D)
        hud = self.requireAs("HUD", ::Node).kotlinScriptInstance<HUD>()
            ?: error("HUD node is missing HUD script")
        scoreTimer = self.requireAs("ScoreTimer", ::Timer)
        mobTimer = self.requireAs("MobTimer", ::Timer)
        startTimer = self.requireAs("StartTimer", ::Timer)
        music = self.requireAs("Music", ::AudioStreamPlayer)
        deathSound = self.requireAs("DeathSound", ::AudioStreamPlayer)
    }

    @RegisterFunction("game_over")
    fun gameOver() {
        scoreTimer.stop()
        mobTimer.stop()
        hud.showGameOver()
        music.stop()
        deathSound.play()
    }

    @RegisterFunction("new_game")
    fun newGame() {
        self.getTree().callGroup("mobs", "queue_free")
        score = 0
        player.start(startPosition.position)
        startTimer.start()
        hud.updateScore(score)
        hud.showMessage("Get Ready")
        music.play()
    }

    @RegisterFunction("_on_MobTimer_timeout")
    fun onMobTimerTimeout() {
        // Create a new instance of the Mob scene.
        val mobNode = mobScene?.instantiate() ?: error("Main.mob_scene is not assigned")
        val mob = RigidBody2D(mobNode.handle)

        // Choose a random location on Path2D.
        mobSpawnLocation.progressRatio = GD.randf()

        // Set the mob's position to a random location.
        mob.position = mobSpawnLocation.position

        // Set the mob's direction perpendicular to the path direction.
        var direction = mobSpawnLocation.rotation + Mathf.PI / 2.0

        // Add some randomness to the direction.
        direction += GD.randfRange(-Mathf.PI / 4.0, Mathf.PI / 4.0)
        mob.rotation = direction

        // Choose the velocity for the mob.
        val velocity = Vector2(GD.randfRange(150.0, 250.0), 0.0)
        mob.linearVelocity = velocity.rotated(direction)

        // Spawn the mob by adding it to the Main scene.
        self.addChild(mobNode)
    }

    @RegisterFunction("_on_ScoreTimer_timeout")
    fun onScoreTimerTimeout() {
        score += 1
        hud.updateScore(score)
    }

    @RegisterFunction("_on_StartTimer_timeout")
    fun onStartTimerTimeout() {
        mobTimer.start()
        scoreTimer.start()
    }
}
