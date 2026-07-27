package thirdperson

import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Marker3D
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.ShapeCast3D
import net.multigesture.kanama.api.addCollisionExceptionWith
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.api.lookAt
import net.multigesture.kanama.types.Vector3

/**
 * Web port of the grenade launcher. Adaptations: the SurfaceTool trajectory trail is not built
 * on Web (aim feedback comes from the snap mesh); the thrower exception rides
 * add_collision_exception_with on the grenade body instead of the PhysicsServer3D RID pair.
 */
@ScriptClass(attachTo = "Node3D")
class GrenadeLauncher(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {
  @ScriptProperty var minThrowDistance: Double = 7.0

  @ScriptProperty var maxThrowDistance: Double = 16.0

  @ScriptProperty var gravity: Double = 16.0

  @ScriptProperty var fromLookPosition: Vector3 = Vector3.ZERO

  @ScriptProperty var throwDirection: Vector3 = Vector3.ZERO

  private lateinit var snapMesh: Node3D
  private lateinit var raycast: ShapeCast3D
  private lateinit var launchPoint: Marker3D

  private var throwVelocity = Vector3.ZERO
  private var timeToLand = 0.0
  private var wasVisible = false

  @OnReady
  fun ready() {
    snapMesh = self.requireAs("%SnapMesh", ::Node3D)
    raycast = self.requireAs("%ShapeCast3D", ::ShapeCast3D)
    launchPoint = self.requireAs("%LaunchPoint", ::Marker3D)
  }

  @OnPhysicsProcess
  fun physicsProcess(delta: Double) {
    if (!wasVisible) {
      wasVisible = true
    }
    updateThrowVelocity()
  }

  @RegisterFunction("throw_grenade")
  fun throwGrenade(): Boolean {
    val parent = self.getParent() ?: return false
    val grenade = DemoScenes.instantiate(DemoScenes.GRENADE) ?: return false

    Node(parent.handle).addChild(grenade)
    val grenadeBody = CharacterBody3D(grenade.handle)
    grenadeBody.globalPosition = launchPoint.globalPosition
    val grenadeScript =
      grenade.kotlinScriptInstance<Grenade>()
        ?: error("Grenade scene is missing Grenade script instance")
    grenadeScript.throwGrenade(throwVelocity)

    // Web adaptation: pairwise exception on the grenade instead of the RID server call.
    grenadeBody.addCollisionExceptionWith(Node3D(parent.handle))
    return true
  }

  private fun updateThrowVelocity() {
    val camera =
      self.getViewport()?.let { net.multigesture.kanama.api.Viewport(it.handle) }?.getCamera3D()
        ?: return
    val upRatio = (Mathf.max(camera.rotation.x + 0.5, -0.4) * 2.0).coerceIn(0.0, 1.0)

    val baseThrowDistance = lerp(minThrowDistance, maxThrowDistance, upRatio)
    val throwDistance = baseThrowDistance
    val globalCameraLookPosition = fromLookPosition + throwDirection * throwDistance
    raycast.targetPosition = globalCameraLookPosition - raycast.globalPosition

    var toTarget = raycast.targetPosition
    if (raycast.getCollisionCount() != 0L) {
      val collider = raycast.getCollider(0)
      val hasTarget = collider != null && Node(collider.handle).isInGroup("targeteables")
      snapMesh.visible = hasTarget
      if (hasTarget) {
        val colliderNode = Node3D(requireNotNull(collider).handle)
        toTarget = colliderNode.globalPosition - launchPoint.globalPosition
        snapMesh.globalPosition = launchPoint.globalPosition + toTarget
        snapMesh.lookAt(launchPoint.globalPosition)
      }
    } else {
      snapMesh.visible = false
    }

    val peakHeight = Mathf.max(toTarget.y + 0.25, launchPoint.position.y + 0.25)
    val motionUp = peakHeight
    val timeGoingUp = Mathf.sqrt(2.0 * motionUp / gravity)

    val motionDown = toTarget.y - peakHeight
    val timeGoingDown = Mathf.sqrt(-2.0 * motionDown / gravity)

    timeToLand = timeGoingUp + timeGoingDown

    val targetPositionXzPlane = Vector3(toTarget.x, 0.0, toTarget.z)
    val startPositionXzPlane = Vector3(launchPoint.position.x, 0.0, launchPoint.position.z)

    val forwardVelocity = (targetPositionXzPlane - startPositionXzPlane) / timeToLand
    val velocityUp = Mathf.sqrt(2.0 * gravity * motionUp)

    throwVelocity = Vector3.UP * velocityUp + forwardVelocity
  }

  private fun lerp(from: Double, to: Double, weight: Double): Double = from + (to - from) * weight
}
