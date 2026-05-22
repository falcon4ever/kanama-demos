package squash

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.OnUnhandledInput
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.Control
import net.multigesture.kanama.api.DirectionalLight3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.InputEvent
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.PathFollow3D
import net.multigesture.kanama.api.RenderingServer
import net.multigesture.kanama.api.Timer
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.generated.MobNames
import net.multigesture.kanama.generated.ScoreLabelNames
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node")
class Main(godotObject: MemorySegment) : KanamaScript<Node>(godotObject, ::Node) {
    @ScriptProperty
    var mobScene: PackedScene? = null

    private lateinit var retry: Control
    private lateinit var mobSpawnLocation: PathFollow3D
    private lateinit var player: Node3D
    private lateinit var scoreLabel: Label
    private lateinit var mobTimer: Timer

    @OnReady
    fun ready() {
        retry = self.requireAs("UserInterface/Retry", ::Control)
        mobSpawnLocation = self.requireAs("SpawnPath/SpawnLocation", ::PathFollow3D)
        player = self.requireAs("Player", ::Node3D)
        scoreLabel = self.requireAs("UserInterface/ScoreLabel", ::Label)
        mobTimer = self.requireAs("MobTimer", ::Timer)

        if (RenderingServer.getCurrentRenderingMethod() == "gl_compatibility") {
            // Use PCF13 shadow filtering to improve quality (Medium maps to PCF5 instead).
            RenderingServer.directionalSoftShadowFilterSetQuality(RenderingServer.SHADOW_QUALITY_SOFT_HIGH)
            // Darken the light's energy to compensate for sRGB blending (without affecting sky rendering).
            val light = self.requireAs("DirectionalLight3D", ::DirectionalLight3D)
            light.skyMode = DirectionalLight3D.SKY_MODE_SKY_ONLY
            val duplicate = light.duplicate()
            if (duplicate != null) {
                val newLight = DirectionalLight3D(duplicate.handle)
                newLight.lightEnergy = 0.35
                newLight.skyMode = DirectionalLight3D.SKY_MODE_LIGHT_ONLY
                self.addChild(newLight)
            }
        }

        retry.hide()
    }

    @OnUnhandledInput
    fun unhandledInput(event: GodotObject) {
        if (InputEvent(event.handle).isActionPressed("ui_accept") && retry.isVisible()) {
            retryCurrentScene()
        }
    }

    @RegisterFunction("_on_retry_button_pressed")
    fun onRetryButtonPressed() {
        if (retry.isVisible()) {
            retryCurrentScene()
        }
    }

    @RegisterFunction("_on_mob_timer_timeout")
    fun onMobTimerTimeout() {
        // Create a new instance of the Mob scene.
        val mob = mobScene?.instantiate() ?: return
        // Choose a random location on the SpawnPath.
        mobSpawnLocation.progressRatio = GD.randf()

        // Communicate the spawn location and the player's location to the mob.
        val playerPosition = player.position
        val mobScript = mob.kotlinScriptInstance<Mob>()
            ?: error("Instantiated mob scene is not backed by squash.Mob")
        mobScript.initialize(mobSpawnLocation.position, playerPosition)
        // Spawn the mob by adding it to the Main scene.
        self.addChild(mob)

        // We connect the mob to the score label to update the score upon squashing a mob.
        mob.signal(MobNames.Signals.squashed)
            .connect(scoreLabel, ScoreLabelNames.Methods.onMobSquashed)
    }

    @RegisterFunction("_on_player_hit")
    fun onPlayerHit() {
        mobTimer.stop()
        retry.show()
    }

    private fun retryCurrentScene() {
        self.getTree().reloadCurrentScene()
    }
}
