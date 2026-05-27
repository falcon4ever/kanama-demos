package tps

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.AnimationPlayer
import net.multigesture.kanama.api.Area3D
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.kotlinScriptInstance
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Area3D")
class Door(godotObject: MemorySegment) : KanamaScript<Area3D>(godotObject, ::Area3D) {
    private var open = false
    private lateinit var animationPlayer: AnimationPlayer

    @OnReady
    fun ready() {
        animationPlayer = self.requireAs("DoorModel/AnimationPlayer", ::AnimationPlayer)
    }

    @RegisterFunction("_on_door_body_entered")
    fun onDoorBodyEntered(body: GodotObject) {
        if (open) return
        val node = Node3D(body.handle)
        if (node.kotlinScriptInstance<Player>() != null) {
            animationPlayer.play("doorsimple_opening")
            open = true
        }
    }
}
