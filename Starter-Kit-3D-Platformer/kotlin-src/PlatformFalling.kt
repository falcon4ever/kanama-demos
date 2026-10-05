package net.multigesture.kanama.demos.platformer3d

import net.multigesture.kanama.annotations.GodotName
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.generated.Autoloads
import net.multigesture.kanama.types.Vector3

@ScriptClass(attachTo = "Node3D")
class PlatformFalling(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {

    private var falling = false
    private var fallVelocity = 0.0
    @OnReady
    fun ready() {
    }

    @OnPhysicsProcess
    fun physicsProcess(delta: Double) {
        self.scale = self.scale.lerp(Vector3.ONE, delta * 10.0)

        if (falling) {
            fallVelocity += 15.0 * delta
            val pos = self.position
            self.position = pos.withY(pos.y - fallVelocity * delta)
        } else {
            fallVelocity = 0.0
        }

        if (self.position.y < -10.0) {
            self.queueFree()
        }
    }

    private fun playAudio(path: String) {
        Autoloads.Audio.call("play", path)
    }

    @GodotName("_on_body_entered")
    fun onBodyEntered(_body: Node) {
        if (!falling) {
            playAudio("res://sounds/fall.ogg")
            self.scale = Vector3(1.25, 1.0, 1.25)
        }
        falling = true
    }
}
