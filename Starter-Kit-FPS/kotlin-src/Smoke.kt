package fps

import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.api.TextureRect
import net.multigesture.kanama.types.Transform3D
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.kotlinScriptInstance

@ScriptClass(attachTo = "Node3D")
class Smoke(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {

    @OnReady
    fun ready() {
        if (OS.getEnvironment("KANAMA_FPS_SMOKE") != "1") return

        val player = self.requireAs("Player", ::Node3D)
        val health = self.requireAs("HUD/Health", ::Label)
        val crosshair = self.requireAs("HUD/Crosshair", ::TextureRect)
        val enemy = self.requireAs("Enemies/enemy-flying", ::Node3D)

        val originalRotation = player.rotation
        player.rotation = Vector3(0f, GD.degToRad(90.0).toFloat(), 0f)
        val globalForward = player.transform.basis * (Vector3.FORWARD * 5.0)
        check(globalForward.isCloseTo(Vector3(-5f, 0f, 0f))) {
            "FPS smoke expected yaw 90 forward to be (-5, 0, 0), got $globalForward"
        }
        val calledTransform = player.call("get_transform") as? Transform3D
            ?: error("FPS smoke expected get_transform call to return Transform3D")
        val calledForward = calledTransform.basis * (Vector3.FORWARD * 5.0)
        check(calledForward.isCloseTo(Vector3(-5f, 0f, 0f))) {
            "FPS smoke expected Variant Transform3D forward to be (-5, 0, 0), got $calledForward"
        }
        val localForward = player.basis.inverse() * globalForward
        check(localForward.isCloseTo(Vector3(0f, 0f, -5f))) {
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

        stopAudio()
        self.getTree().quit()
    }


    private fun stopAudio() {
        val audio = self.getNodeOrNull("/root/Audio") ?: return
        audio.kotlinScriptInstance<Audio>()?.stopAll()
    }

    private fun Vector3.isCloseTo(other: Vector3, epsilon: Float = 0.001f): Boolean =
        Mathf.abs(x - other.x) <= epsilon &&
            Mathf.abs(y - other.y) <= epsilon &&
            Mathf.abs(z - other.z) <= epsilon
}
