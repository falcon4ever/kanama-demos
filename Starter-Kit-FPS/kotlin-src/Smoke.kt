package fps

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.api.TextureRect
import net.multigesture.kanama.generated.Autoloads
import net.multigesture.kanama.types.Transform3D
import net.multigesture.kanama.types.Vector3

/**
 * Desktop smoke of the FPS demo, as an autoload (Kanama task 131 item 17): it reaches the scene
 * through the root's "Main" child, so the scene itself carries no smoke script.
 */
@ScriptClass(attachTo = "Node")
class Smoke(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {

    @OnReady
    fun ready() {
        if (OS.getEnvironment("KANAMA_FPS_SMOKE") != "1") return
        launch {
            MainThread.awaitNextFrame() // the scene root is added after the autoloads' ready
            runChecks(self.getParent()?.getNodeOrNull("Main") ?: error("FPS smoke could not find the Main scene root"))
        }
    }

    private fun runChecks(main: Node) {
        val player = main.requireAs("Player", ::Node3D)
        val health = main.requireAs("HUD/Health", ::Label)
        val crosshair = main.requireAs("HUD/Crosshair", ::TextureRect)
        val enemy = main.requireAs("Enemies/enemy-flying", ::Node3D)

        val originalRotation = player.rotation
        player.rotation = Vector3(0.0, GD.degToRad(90.0), 0.0)
        val globalForward = player.transform.basis * (Vector3.FORWARD * 5.0)
        check(globalForward.isCloseTo(Vector3(-5.0, 0.0, 0.0))) {
            "FPS smoke expected yaw 90 forward to be (-5, 0, 0), got $globalForward"
        }
        val calledTransform = player.call("get_transform") as? Transform3D
            ?: error("FPS smoke expected get_transform call to return Transform3D")
        val calledForward = calledTransform.basis * (Vector3.FORWARD * 5.0)
        check(calledForward.isCloseTo(Vector3(-5.0, 0.0, 0.0))) {
            "FPS smoke expected Variant Transform3D forward to be (-5, 0, 0), got $calledForward"
        }
        val localForward = player.basis.inverse() * globalForward
        check(localForward.isCloseTo(Vector3(0.0, 0.0, -5.0))) {
            "FPS smoke expected inverse basis to recover local forward, got $localForward"
        }
        player.rotation = originalRotation

        check(health.text == "100%") {
            "FPS smoke expected HUD to start at 100%, got '${health.text}'"
        }
        player.call("damage", 5.0)
        check(health.text == "95%") {
            "FPS smoke expected Player.damage to emit health_updated to HUD, got '${health.text}'"
        }

        enemy.call("damage", 200.0)
        check(enemy.isQueuedForDeletion()) {
            "FPS smoke expected Enemy.damage to queue the enemy for deletion"
        }

        player.call("change_weapon")
        val assignedCrosshair = crosshair.texture
        check(assignedCrosshair != null) {
            "FPS smoke expected Crosshair.texture to remain set after Player.change_weapon"
        }
        assignedCrosshair.close() // owned +1 read-back; the Crosshair node keeps its own

        stopAudio()
        // The completion line scripts/desktop_smoke_all.sh and the iOS runner require (task 111/131).
        println("[kanama:smoke] SmokeQuit complete")
        requireNotNull(self.getTree()).quit()
    }


    private fun stopAudio() {
        Autoloads.Audio.stopAll()
    }

    private fun Vector3.isCloseTo(other: Vector3, epsilon: Double = 0.001): Boolean =
        Mathf.abs(x - other.x) <= epsilon &&
            Mathf.abs(y - other.y) <= epsilon &&
            Mathf.abs(z - other.z) <= epsilon
}
