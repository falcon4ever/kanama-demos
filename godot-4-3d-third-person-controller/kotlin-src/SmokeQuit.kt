package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.BaseButton
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.SceneTree
import net.multigesture.kanama.types.Basis
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "Node")
class SmokeQuit(godotObject: MemorySegment) : KanamaScript<Node>(godotObject, ::Node), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()

    @OnReady
    fun ready() {
        if (System.getenv("KANAMA_DEMO_SMOKE_QUIT") != "1") return
        kanamaScope.launch {
            MainThread.awaitNextFrame()
            pressResumeButton()
            repeat(90) {
                MainThread.awaitNextFrame()
            }
            if (System.getenv("KANAMA_DEMO_RESUME_ONLY") == "1") {
                finishSmoke()
                return@launch
            }
            if (System.getenv("KANAMA_ROTATION_SMOKE") == "1") {
                smokeBasisAssignment()
            }
            smokeCoinSpawnCalls()
            smokeGrenadeThrowCall()
            smokeGrenadeLauncherCall()
            smokeBulletPreAddProperties()
            smokeSmokePuffScene()
            smokeBeetleSkinExportedPackedStrings()
            smokeBeeBotDamageCall()
            smokeBeetleBotDamageCall()
            repeat(10) {
                MainThread.awaitNextFrame()
            }
            finishSmoke()
        }
    }

    private fun pressResumeButton() {
        val root = self.getParent() ?: return
        val resume = root.getAsOrNull("DemoPage/CanvasLayer/DemoPageRoot/Content/MarginContainer/Buttons/Resume", ::BaseButton)
        if (resume != null) {
            resume.signal(BaseButton.Signals.pressed).emit()
        } else {
            self.getTree().setPaused(false)
        }
    }

    private fun finishSmoke() {
        SceneTree.unloadCurrentScene()
        MainThread.postAfterFrames(QUIT_AFTER_UNLOAD_FRAMES) {
            SceneTree.quit()
        }
    }

    private fun smokeBasisAssignment() {
        val root = self.getParent() ?: return
        val rotationRoot = root.getAsOrNull("Player/CharacterRotationRoot", ::Node3D) ?: return
        val before = rotationRoot.basis
        val direction = Vector3.RIGHT
        val leftAxis = Vector3.UP.cross(direction)
        val basis = Basis(leftAxis, Vector3.UP, direction)
        rotationRoot.basis = basis
        val after = rotationRoot.basis
        val targetRotation = basis.getRotationQuaternion()
        val currentRotation = before.getRotationQuaternion()
        val interpolated = Basis(currentRotation.slerp(targetRotation, 0.25)).scaled(before.getScale())
        rotationRoot.basis = interpolated
        val slerped = rotationRoot.basis
        System.err.println(
            "[kanama:kt] rotation smoke before=$before after=$after expected=$basis " +
                "currentQuat=$currentRotation targetQuat=$targetRotation interpolated=$interpolated slerped=$slerped",
        )
    }

    private suspend fun smokeCoinSpawnCalls() {
        val scene = ResourceLoader.loadPackedScene("res://player/coin/coin.tscn") ?: return
        val parent = self.getParent() ?: self
        val defaultCoin = scene.instantiate()
        val immediateCoin = scene.instantiate()
        try {
            if (defaultCoin == null || immediateCoin == null) return
            parent.addChild(defaultCoin)
            parent.addChild(immediateCoin)

            MainThread.awaitNextFrame()

            Node3D(defaultCoin.handle).globalPosition = Vector3(0f, 20f, 0f)
            defaultCoin.call("spawn")
            Node3D(immediateCoin.handle).globalPosition = Vector3(2f, 20f, 0f)
            immediateCoin.call("spawn", 0.0)

            MainThread.awaitNextFrame()
        } finally {
            defaultCoin?.queueFree()
            immediateCoin?.queueFree()
            scene.close()
        }
    }

    private suspend fun smokeGrenadeThrowCall() {
        val scene = ResourceLoader.loadPackedScene("res://player/grenade.tscn") ?: return
        val parent = self.getParent() ?: self
        val grenade = scene.instantiate()
        try {
            if (grenade == null) return
            parent.addChild(grenade)

            MainThread.awaitNextFrame()

            Node3D(grenade.handle).globalPosition = Vector3(0f, 10f, 0f)
            grenade.call("throw", Vector3(0f, 0f, 0f))

            repeat(3) {
                MainThread.awaitNextFrame()
            }
        } finally {
            grenade?.queueFree()
            scene.close()
        }
    }

    private suspend fun smokeGrenadeLauncherCall() {
        val root = self.getParent() ?: return
        val launcher = root.getAsOrNull("Player/GrenadeLauncher", ::Node3D) ?: return
        launcher.setVisible(true)

        try {
            repeat(3) {
                MainThread.awaitNextFrame()
            }
            launcher.call("throw_grenade")
            repeat(3) {
                MainThread.awaitNextFrame()
            }
        } finally {
            launcher.setVisible(false)
        }
    }

    private suspend fun smokeBulletPreAddProperties() {
        val scene = ResourceLoader.loadPackedScene("res://player/bullet.tscn") ?: return
        val parent = self.getParent() ?: self
        val bullet = scene.instantiate()
        try {
            if (bullet == null) return
            val bulletObject = GodotObject(bullet.handle)
            bulletObject.call("set", "velocity", Vector3.RIGHT * 10.0)
            bulletObject.call("set", "distance_limit", 14.0)
            bulletObject.call("set", "shooter", GodotObject(parent.handle))
            Node(bullet.handle).setProcessMode(Node.PROCESS_MODE_ALWAYS)
            parent.addChild(bullet)

            val bullet3d = Node3D(bullet.handle)
            bullet3d.globalPosition = Vector3(0f, 30f, 0f)
            val shooter = bulletObject.call("get", "shooter") as? GodotObject
            check(shooter?.handle?.address() == parent.handle.address()) {
                "Bullet shooter property was not preserved before add_child"
            }
            val start = bullet3d.globalPosition
            val visual = Node(bullet.handle).requireAs("Bullet", ::Node3D)
            val initialVisualScale = visual.scale
            repeat(2) {
                MainThread.awaitNextFrame()
            }
            val end = bullet3d.globalPosition
            check(!bulletObject.isQueuedForDeletion()) {
                "Bullet queued itself after pre-add velocity assignment"
            }
            check(start.distanceTo(end) > 0.01) {
                "Bullet did not move after pre-add velocity assignment: start=$start end=$end"
            }
            val visualScale = visual.scale
            check(visualScale.length() > 0.01) {
                "Bullet visual scale collapsed after spawn: initial=$initialVisualScale final=$visualScale"
            }
        } finally {
            bullet?.queueFree()
            scene.close()
        }
    }

    private suspend fun smokeSmokePuffScene() {
        val scene = ResourceLoader.loadPackedScene("res://enemies/smoke_puff/smoke_puff.tscn") ?: return
        val parent = self.getParent() ?: self
        val puff = scene.instantiate()
        try {
            if (puff == null) return
            parent.addChild(puff)
            Node3D(puff.handle).globalPosition = Vector3(0f, 20f, 0f)
            repeat(8) {
                MainThread.awaitNextFrame()
            }
        } finally {
            puff?.queueFree()
            scene.close()
        }
    }

    private suspend fun smokeBeetleSkinExportedPackedStrings() {
        val scene = ResourceLoader.loadPackedScene("res://enemies/beetle_bot/beetlebot_skin.tscn") ?: return
        val parent = self.getParent() ?: self
        val skin = scene.instantiate()
        try {
            if (skin == null) return
            parent.addChild(skin)
            MainThread.awaitNextFrame()

            val forceLoop = GodotObject(skin.handle).call("get", "_force_loop") as? List<*> ?: emptyList<Any?>()
            check(forceLoop == listOf("Bob", "walk")) {
                "BeetlebotSkin _force_loop did not decode as List<String>: $forceLoop"
            }
            skin.call("walk")
            MainThread.awaitNextFrame()
        } finally {
            skin?.queueFree()
            scene.close()
        }
    }

    private suspend fun smokeBeeBotDamageCall() {
        val scene = ResourceLoader.loadPackedScene("res://enemies/bee_bot.tscn") ?: return
        val parent = self.getParent() ?: self
        val bee = scene.instantiate()
        try {
            if (bee == null) return
            parent.addChild(bee)
            val bee3d = Node3D(bee.handle)
            bee3d.globalPosition = Vector3(0f, 30f, 0f)
            bee3d.transform.lookingAt(Vector3(0f, 30f, -1f))
            MainThread.awaitNextFrame()
            bee.call("damage", Vector3.ZERO, Vector3(0f, 0f, 1f))
            repeat(3) {
                MainThread.awaitNextFrame()
            }
        } finally {
            bee?.queueFree()
            scene.close()
        }
    }

    private suspend fun smokeBeetleBotDamageCall() {
        val scene = ResourceLoader.loadPackedScene("res://enemies/beetle_bot.tscn") ?: return
        val parent = self.getParent() ?: self
        val beetle = scene.instantiate()
        try {
            if (beetle == null) return
            parent.addChild(beetle)
            Node3D(beetle.handle).globalPosition = Vector3(3f, 30f, 0f)
            MainThread.awaitNextFrame()
            beetle.call("damage", Vector3.ZERO, Vector3(0f, 0f, 1f))
            repeat(3) {
                MainThread.awaitNextFrame()
            }
        } finally {
            beetle?.queueFree()
            scene.close()
        }
    }

    companion object {
        private const val QUIT_AFTER_UNLOAD_FRAMES = 8
    }
}
