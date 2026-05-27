package tps

import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.CPUParticles3D
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "Node3D")
class Blast(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D), KanamaCoroutineOwner {
	override val kanamaScope = KanamaScope()

	private lateinit var lightRays: CPUParticles3D
	private lateinit var animationPlayer: AnimationPlayer
	private var camera: Camera3D? = null

	@OnReady
	fun ready() {
		lightRays = self.requireAs("LightRays", ::CPUParticles3D)
		animationPlayer = self.requireAs("AnimationPlayer", ::AnimationPlayer)
		camera = self.getTree().root.getCamera3d()
		kanamaScope.launch {
			animationPlayer.signal(AnimationPlayer.Signals.animationFinished)
				.await(self, argumentCount = 1)
			self.queueFree()
		}
	}

	@OnProcess
	fun process(_delta: Double) {
		val target = camera ?: return
		if (self.isQueuedForDeletion() || !self.isInsideTree() || target.isQueuedForDeletion() || !target.isInsideTree()) return
		lightRays.lookAt(target.globalTransform.origin)
	}

	@OnExitTree
	fun exitTree() {
		kanamaScope.cancel()
		self.setProcess(false)
		camera = null
	}
}
