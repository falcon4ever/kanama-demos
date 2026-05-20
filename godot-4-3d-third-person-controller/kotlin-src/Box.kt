package thirdperson

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AudioStreamPlayer3D
import net.multigesture.kanama.api.CollisionShape3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.RigidBody3D
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "RigidBody3D")
class Box(godotObject: MemorySegment) : KanamaScript<RigidBody3D>(godotObject, ::RigidBody3D), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()

    private lateinit var destroySound: AudioStreamPlayer3D
    private lateinit var collisionShape: CollisionShape3D

    @OnReady
    fun ready() {
        destroySound = self.requireAs("DestroySound", ::AudioStreamPlayer3D)
        collisionShape = self.requireAs("CollisionShape3d", ::CollisionShape3D)
    }

    @RegisterFunction
    fun damage(impactPoint: Vector3, force: Vector3) {
        repeat(COINS_COUNT) {
            val coinNode = DemoScenes.instantiate(DemoScenes.COIN) ?: return@repeat
            val coin = coinNode.kotlinScriptInstance<Coin>()
                ?: error("Coin scene is missing Coin script instance")
            self.getParent()?.addChild(coinNode)
            val coin3d = Node3D(coinNode.handle)
            coin3d.globalPosition = self.globalPosition
            coin.spawn()
        }

        val destroyedBox = DemoScenes.instantiate(DemoScenes.DESTROYED_BOX)
        if (destroyedBox != null) {
            self.getParent()?.addChild(destroyedBox)
            Node3D(destroyedBox.handle).globalPosition = self.globalPosition
        }

        collisionShape.setDeferred("disabled", true)
        destroySound.setPitchScale(GD.randfn(1.0, 0.1))
        destroySound.play()

        kanamaScope.launch {
            destroySound.signal(AudioStreamPlayer3D.Signals.finished)
                .await(self, argumentCount = 0)
            self.queueFree()
        }
    }

    companion object {
        private const val COINS_COUNT = 5
    }
}
