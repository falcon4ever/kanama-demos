package charactercontroller

import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.PhysicsBody3D

/** Autoload event bus: kill-plane touches and flag wins fan out from here. */
@ScriptClass(attachTo = "Node")
class Events(godotObject: GodotHandle) : KanamaScript<Node>(godotObject, ::Node) {
  @Signal fun killPlaneTouched(body: PhysicsBody3D) = Unit

  @Signal fun flagReached() = Unit
}
