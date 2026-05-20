package thirdperson

import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.OnPhysicsProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.CharacterBody3D
import net.multigesture.kanama.api.CollisionObject3D
import net.multigesture.kanama.api.Engine
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Marker3D
import net.multigesture.kanama.api.Mesh
import net.multigesture.kanama.api.MeshInstance3D
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.PhysicsServer3D
import net.multigesture.kanama.api.ShapeCast3D
import net.multigesture.kanama.api.SurfaceTool
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Node3D")
class GrenadeLauncher(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {
    @ScriptProperty
    var minThrowDistance: Double = 7.0

    @ScriptProperty
    var maxThrowDistance: Double = 16.0

    @ScriptProperty
    var gravity: Double = 16.0

    @ScriptProperty
    var fromLookPosition: Vector3 = Vector3.ZERO

    @ScriptProperty
    var throwDirection: Vector3 = Vector3.ZERO

    private lateinit var snapMesh: Node3D
    private lateinit var raycast: ShapeCast3D
    private lateinit var launchPoint: Marker3D
    private lateinit var trailMeshInstance: MeshInstance3D

    private var throwVelocity = Vector3.ZERO
    private var timeToLand = 0.0
    private var trailMeshReady = false
    private var trailUpdateElapsed = TRAIL_UPDATE_INTERVAL
    private var lastTrailVelocity = Vector3.ZERO
    private var lastTrailTimeToLand = 0.0
    private var wasVisible = false

    @OnReady
    fun ready() {
        snapMesh = self.requireAs("%SnapMesh", ::Node3D)
        raycast = self.requireAs("%ShapeCast3D", ::ShapeCast3D)
        launchPoint = self.requireAs("%LaunchPoint", ::Marker3D)
        trailMeshInstance = self.requireAs("%TrailMeshInstance", ::MeshInstance3D)

        warmUpTrailMesh()

        if (Engine.isEditorHint()) {
            self.setPhysicsProcess(false)
        }
    }

    @OnPhysicsProcess
    fun physicsProcess(delta: Double) {
        if (!self.isVisible()) {
            wasVisible = false
            return
        }
        if (!wasVisible) {
            wasVisible = true
            trailUpdateElapsed = 0.0
        }

        updateThrowVelocity()
        trailUpdateElapsed += delta
        if (trailUpdateElapsed >= TRAIL_UPDATE_INTERVAL && trailNeedsUpdate()) {
            drawThrowPath()
            trailMeshReady = true
            trailUpdateElapsed = 0.0
            lastTrailVelocity = throwVelocity
            lastTrailTimeToLand = timeToLand
        }
    }

    @RegisterFunction("throw_grenade")
    fun throwGrenade(): Boolean {
        if (!self.isVisible()) return false

        val parent = self.getParent() ?: return false
        val grenade = DemoScenes.instantiate(DemoScenes.GRENADE) ?: return false

        parent.addChild(grenade)
        val grenadeBody = CharacterBody3D(grenade.handle)
        grenadeBody.globalPosition = launchPoint.globalPosition
        val grenadeScript = grenade.kotlinScriptInstance<Grenade>()
            ?: error("Grenade scene is missing Grenade script instance")
        grenadeScript.throwGrenade(throwVelocity)

        PhysicsServer3D.bodyAddCollisionException(
            CollisionObject3D(parent.handle).getRid(),
            grenadeBody.getRid(),
        )
        return true
    }

    private fun updateThrowVelocity() {
        val camera = self.getViewport()?.getCamera3D() ?: return
        val upRatio = (Mathf.max(camera.rotation.x.toDouble() + 0.5, -0.4) * 2.0).coerceIn(0.0, 1.0)

        val baseThrowDistance = lerp(minThrowDistance, maxThrowDistance, upRatio)
        val throwDistance = baseThrowDistance
        val globalCameraLookPosition = fromLookPosition + throwDirection * throwDistance
        raycast.targetPosition = globalCameraLookPosition - raycast.globalPosition

        var toTarget = raycast.targetPosition
        if (raycast.getCollisionCount() != 0L) {
            val collider = raycast.getCollider(0)
            val hasTarget = collider != null && Node(collider.handle).isInGroup("targeteables")
            snapMesh.setVisible(hasTarget)
            if (hasTarget) {
                val colliderNode = Node3D(requireNotNull(collider).handle)
                toTarget = colliderNode.globalPosition - launchPoint.globalPosition
                snapMesh.globalPosition = launchPoint.globalPosition + toTarget
                snapMesh.lookAt(launchPoint.globalPosition)
            }
        } else {
            snapMesh.setVisible(false)
        }

        val peakHeight = Mathf.max(toTarget.y.toDouble() + 0.25, launchPoint.position.y.toDouble() + 0.25)
        val motionUp = peakHeight
        val timeGoingUp = Mathf.sqrt(2.0 * motionUp / gravity)

        val motionDown = toTarget.y.toDouble() - peakHeight
        val timeGoingDown = Mathf.sqrt(-2.0 * motionDown / gravity)

        timeToLand = timeGoingUp + timeGoingDown

        val targetPositionXzPlane = Vector3(toTarget.x, 0.0, toTarget.z)
        val startPositionXzPlane = Vector3(launchPoint.position.x, 0.0, launchPoint.position.z)

        val forwardVelocity = (targetPositionXzPlane - startPositionXzPlane) / timeToLand
        val velocityUp = Mathf.sqrt(2.0 * gravity * motionUp)

        throwVelocity = Vector3.UP * velocityUp + forwardVelocity
    }

    private fun trailNeedsUpdate(): Boolean =
        !trailMeshReady ||
            lastTrailVelocity.distanceSquaredTo(throwVelocity) > TRAIL_VELOCITY_EPSILON ||
            Mathf.abs(lastTrailTimeToLand - timeToLand) > TRAIL_TIME_EPSILON

    private fun warmUpTrailMesh() {
        throwVelocity = Vector3.FORWARD * minThrowDistance + Vector3.UP * 4.0
        timeToLand = 1.0
        drawThrowPath()
        trailMeshReady = true
        lastTrailVelocity = throwVelocity
        lastTrailTimeToLand = timeToLand
        trailUpdateElapsed = 0.0
    }

    private fun drawThrowPath() {
        val forwardDirection = Vector3(throwVelocity.x, 0.0, throwVelocity.z).normalized()
        val leftDirection = Vector3.UP.cross(forwardDirection)
        val offsetLeft = leftDirection * TRAIL_WIDTH / 2.0
        val offsetRight = -leftDirection * TRAIL_WIDTH / 2.0

        val surfaceTool = SurfaceTool.create()
        try {
            surfaceTool.begin(Mesh.PRIMITIVE_TRIANGLES)

            val endTime = timeToLand + 0.5
            var pointPrevious = Vector3.ZERO
            var timeCurrent = 0.0
            while (timeCurrent < endTime) {
                timeCurrent += TIME_STEP
                val pointCurrent = throwVelocity * timeCurrent + Vector3.DOWN * gravity * 0.5 * timeCurrent * timeCurrent

                val trailPointLeftEnd = pointCurrent + offsetLeft
                val trailPointRightEnd = pointCurrent + offsetRight
                val trailPointLeftStart = pointPrevious + offsetLeft
                val trailPointRightStart = pointPrevious + offsetRight

                val uvProgressEnd = timeCurrent / endTime
                val uvProgressStart = uvProgressEnd - (TIME_STEP / endTime)
                val uvValueRightStart = Vector2.RIGHT * uvProgressStart
                val uvValueRightEnd = Vector2.RIGHT * uvProgressEnd
                val uvValueLeftStart = Vector2.DOWN + uvValueRightStart
                val uvValueLeftEnd = Vector2.DOWN + uvValueRightEnd

                pointPrevious = pointCurrent

                surfaceTool.setUv(uvValueRightEnd)
                surfaceTool.addVertex(trailPointRightEnd)
                surfaceTool.setUv(uvValueLeftStart)
                surfaceTool.addVertex(trailPointLeftStart)
                surfaceTool.setUv(uvValueLeftEnd)
                surfaceTool.addVertex(trailPointLeftEnd)

                surfaceTool.setUv(uvValueRightStart)
                surfaceTool.addVertex(trailPointRightStart)
                surfaceTool.setUv(uvValueLeftStart)
                surfaceTool.addVertex(trailPointLeftStart)
                surfaceTool.setUv(uvValueRightEnd)
                surfaceTool.addVertex(trailPointRightEnd)
            }

            val mesh = surfaceTool.commit()
            trailMeshInstance.setMesh(mesh)
        } finally {
            surfaceTool.close()
        }
    }

    private fun lerp(from: Double, to: Double, weight: Double): Double =
        from + (to - from) * weight

    companion object {
        private const val TIME_STEP = 0.12
        private const val TRAIL_UPDATE_INTERVAL = 0.25
        private const val TRAIL_TIME_EPSILON = 0.08
        private const val TRAIL_VELOCITY_EPSILON = 0.02
        private const val TRAIL_WIDTH = 0.25
    }
}
