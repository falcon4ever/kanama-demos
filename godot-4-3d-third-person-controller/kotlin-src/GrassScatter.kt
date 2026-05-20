package thirdperson

import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.ArrayMesh
import net.multigesture.kanama.api.MeshDataTool
import net.multigesture.kanama.api.MeshInstance3D
import net.multigesture.kanama.api.MultiMesh
import net.multigesture.kanama.api.MultiMeshInstance3D
import net.multigesture.kanama.types.Basis
import net.multigesture.kanama.types.NodePath
import net.multigesture.kanama.types.Transform3D
import net.multigesture.kanama.types.Vector3
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "MultiMeshInstance3D")
class GrassScatter(godotObject: MemorySegment) : KanamaScript<MultiMeshInstance3D>(
    godotObject,
    ::MultiMeshInstance3D,
) {
    @ScriptProperty
    var targetMeshPath: NodePath = NodePath("")

    private val triangles = mutableListOf<Int>()
    private val cumulatedTriangleAreas = mutableListOf<Double>()

    @OnReady
    fun ready() {
        val targetMeshNode = self.requireAs(targetMeshPath, ::MeshInstance3D)
        val mesh = targetMeshNode.getMesh() ?: return
        val multimesh = self.multimesh
        if (multimesh == null) return

        val arrayMesh = ArrayMesh.fromResource(mesh) ?: return
        MeshDataTool.create().use { meshDataTool ->
            meshDataTool.createFromSurface(arrayMesh, 0)
            collectWalkableTriangles(meshDataTool)
            if (triangles.isEmpty()) return
            scatterInstances(meshDataTool, multimesh, targetMeshNode)
        }
    }

    private fun collectWalkableTriangles(meshDataTool: MeshDataTool) {
        triangles.clear()

        val faceCount = meshDataTool.getFaceCount()
        for (i in 0 until faceCount) {
            val normal = meshDataTool.getFaceNormal(i)
            if (normal.dot(Vector3.UP) < 0.99) continue

            val v1 = meshDataTool.getVertexColor(meshDataTool.getFaceVertex(i, 0))
            val v2 = meshDataTool.getVertexColor(meshDataTool.getFaceVertex(i, 1))
            val v3 = meshDataTool.getVertexColor(meshDataTool.getFaceVertex(i, 2))
            val redness = (v1.r + v2.r + v3.r) / 3.0
            if (redness > 0.25) continue

            triangles += i
        }

        cumulatedTriangleAreas.clear()
        cumulatedTriangleAreas.addAll(List(triangles.size) { 0.0 })
        for (i in triangles.indices) {
            val triangle = getTriangleVertices(meshDataTool, triangles[i])
            val area = triangleArea(triangle.a, triangle.b, triangle.c)
            cumulatedTriangleAreas[i] = (cumulatedTriangleAreas.getOrNull(i - 1) ?: 0.0) + area
        }
    }

    private fun scatterInstances(
        meshDataTool: MeshDataTool,
        multimesh: MultiMesh,
        targetMeshNode: MeshInstance3D,
    ) {
        val count = 600
        val targetOffset = self.toLocal(targetMeshNode.globalPosition)
        multimesh.instanceCount = count

        for (i in 0 until count) {
            val transform = Transform3D(Basis.IDENTITY, getRandomPoint(meshDataTool) + targetOffset)
                .scaledLocal(Vector3.ONE * GD.randfn(0.6, 0.1))
            multimesh.setInstanceTransform(i, transform)
        }
    }

    private fun getRandomPoint(meshDataTool: MeshDataTool): Vector3 {
        val index = GD.randiRange(0, triangles.lastIndex.toLong()).toInt()
        val triangle = getTriangleVertices(meshDataTool, triangles[index])
        return randomTrianglePoint(triangle.a, triangle.b, triangle.c)
    }

    private fun getTriangleVertices(meshDataTool: MeshDataTool, triangleIndex: Int): Triangle {
        val a = meshDataTool.getVertex(meshDataTool.getFaceVertex(triangleIndex, 0))
        val b = meshDataTool.getVertex(meshDataTool.getFaceVertex(triangleIndex, 1))
        val c = meshDataTool.getVertex(meshDataTool.getFaceVertex(triangleIndex, 2))
        return Triangle(a, b, c)
    }

    private fun triangleArea(p1: Vector3, p2: Vector3, p3: Vector3): Double =
        (p2 - p1).cross(p3 - p1).length() / 2.0

    private fun randomTrianglePoint(a: Vector3, b: Vector3, c: Vector3): Vector3 =
        a + (-a + b + (c - b) * GD.randf()) * Mathf.sqrt(GD.randf())

    private data class Triangle(val a: Vector3, val b: Vector3, val c: Vector3)
}
