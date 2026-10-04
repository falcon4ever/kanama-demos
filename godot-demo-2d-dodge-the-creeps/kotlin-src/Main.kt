package dodge

import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Export
import net.multigesture.kanama.annotations.GodotName
import net.multigesture.kanama.api.AudioStreamPlayer
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Marker2D
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.PathFollow2D
import net.multigesture.kanama.api.RigidBody2D
import net.multigesture.kanama.api.Timer
import net.multigesture.kanama.api.instantiateAs
import net.multigesture.kanama.api.tree
import net.multigesture.kanama.types.Vector2

@ScriptClass(attachTo = "Node")
class Main(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
    @Export
    var mobScene: PackedScene? = null

    private var score: Long = 0

    private val player by script<Player>("Player")
    private val startPosition by node<Marker2D>("StartPosition")
    private val mobSpawnLocation by node<PathFollow2D>("MobPath/MobSpawnLocation")
    private val hud by script<HUD>("HUD")
    private val scoreTimer by node<Timer>("ScoreTimer")
    private val mobTimer by node<Timer>("MobTimer")
    private val startTimer by node<Timer>("StartTimer")
    private val music by node<AudioStreamPlayer>("Music")
    private val deathSound by node<AudioStreamPlayer>("DeathSound")

    fun gameOver() {
        scoreTimer.stop()
        mobTimer.stop()
        hud.showGameOver()
        music.stop()
        deathSound.play()
    }

    fun newGame() {
        self.tree.callGroup("mobs", "queue_free")
        score = 0
        player.start(startPosition.position)
        startTimer.start()
        hud.updateScore(score)
        hud.showMessage("Get Ready")
        music.play()
    }

    @GodotName("_on_MobTimer_timeout")
    fun onMobTimerTimeout() {
        // Create a new instance of the Mob scene.
        val mob = (mobScene ?: error("Main.mob_scene is not assigned")).instantiateAs<RigidBody2D>()

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
        self.addChild(mob)
    }

    @GodotName("_on_ScoreTimer_timeout")
    fun onScoreTimerTimeout() {
        score += 1
        hud.updateScore(score)
    }

    @GodotName("_on_StartTimer_timeout")
    fun onStartTimerTimeout() {
        mobTimer.start()
        scoreTimer.start()
    }
}
