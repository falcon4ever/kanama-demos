package citybuilder

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.Process
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.GridMap
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Mesh
import net.multigesture.kanama.api.MeshInstance3D
import net.multigesture.kanama.api.MeshLibrary
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.Resource
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.ResourceSaver
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.types.Plane
import net.multigesture.kanama.types.Transform3D
import net.multigesture.kanama.types.Vector2i
import net.multigesture.kanama.types.Vector3
import net.multigesture.kanama.types.Vector3i
import java.lang.foreign.MemorySegment
import net.multigesture.kanama.api.KanamaScript

@ScriptClass(attachTo = "Node3D")
class Builder(godotObject: MemorySegment) : KanamaScript<Node3D>(godotObject, ::Node3D) {

	@ScriptProperty
	var structures: List<Structure> = emptyList()

	@ScriptProperty
	var selector: Node3D? = null

	@ScriptProperty
	var selectorContainer: Node3D? = null

	@ScriptProperty
	var viewCamera: Camera3D? = null

	@ScriptProperty
	var gridmap: GridMap? = null

	@ScriptProperty
	var cashDisplay: Label? = null

	private lateinit var map: DataMap
	private var meshLibrary: MeshLibrary? = null
	private var index = 0
	private val plane = Plane(Vector3.UP, Vector3.ZERO.y)

	@OnReady
	fun ready() {
		map = newScriptResource("res://kotlin-src/DataMap.kt")
		val library = MeshLibrary.create()

		for ((structureIndex, structure) in structures.withIndex()) {
			val id = structureIndex
            val mesh = getMesh(structure.model) ?: continue
            library.createItem(id)
            library.setItemMesh(id, mesh)
            library.setItemMeshTransform(id, Transform3D.IDENTITY)
        }

		meshLibrary = library
		requireGridMap().setMeshLibrary(library)
		updateStructure()
		updateCash()
	}

	@OnExitTree
	fun exitTree() {
        clearStructurePreview()
        gridmap?.clear()
        gridmap?.setMeshLibrary(null)
        meshLibrary = null
    }

	@Process
	fun process(delta: Double) {
		actionRotate()
		actionStructureToggle()
		actionSave()
		actionLoad()
		actionLoadResources()

		val camera = requireViewCamera()
		val mousePosition = self.getViewport()?.getMousePosition() ?: return
		val worldPosition = plane.intersectsRay(
			camera.projectRayOrigin(mousePosition),
			camera.projectRayNormal(mousePosition),
		) ?: return
		val gridmapPosition = Vector3i(worldPosition.x.toDouble().let { Mathf.roundToInt(it).toInt() }, 0, worldPosition.z.toDouble().let { Mathf.roundToInt(it).toInt() })

		val selectorNode = requireSelector()
		selectorNode.position = selectorNode.position.lerp(
			Vector3(gridmapPosition.x, gridmapPosition.y, gridmapPosition.z),
			Mathf.min(delta * 40.0, 1.0),
		)

		actionBuild(gridmapPosition)
		actionDemolish(gridmapPosition)
	}

	private fun getMesh(packedScene: PackedScene?): Mesh? {
		if (packedScene == null) return null
		val sceneState = packedScene.getState() ?: return null
		for (nodeIndex in 0 until sceneState.getNodeCount()) {
			if (sceneState.getNodeType(nodeIndex) != "MeshInstance3D") continue
			for (propertyIndex in 0 until sceneState.getNodePropertyCount(nodeIndex)) {
				if (sceneState.getNodePropertyName(nodeIndex, propertyIndex) != "mesh") continue
				val propertyValue = sceneState.getNodePropertyValue(nodeIndex, propertyIndex) as? GodotObject
					?: continue
				val mesh = Mesh.fromObject(propertyValue) ?: continue
				val duplicate = mesh.duplicate() ?: continue
				return Mesh.fromObject(duplicate.asObject())
			}
		}
		return null
	}

	private fun actionBuild(gridmapPosition: Vector3i) {
		if (Input.isActionJustPressed("build")) {
			val grid = requireGridMap()
			val previousTile = grid.getCellItem(gridmapPosition)
			grid.setCellItem(gridmapPosition, index, grid.getOrthogonalIndexFromBasis(requireSelector().basis))

			if (previousTile != index) {
				map.cash -= structures[index].price
				updateCash()
				playAudio("sounds/placement-a.ogg, sounds/placement-b.ogg, sounds/placement-c.ogg, sounds/placement-d.ogg", -20.0)
			}
		}
	}

	private fun actionDemolish(gridmapPosition: Vector3i) {
		if (Input.isActionJustPressed("demolish")) {
			val grid = requireGridMap()
			if (grid.getCellItem(gridmapPosition) != GridMap.INVALID_CELL_ITEM) {
				grid.setCellItem(gridmapPosition, GridMap.INVALID_CELL_ITEM)
				playAudio("sounds/removal-a.ogg, sounds/removal-b.ogg, sounds/removal-c.ogg, sounds/removal-d.ogg", -20.0)
			}
		}
	}

	private fun actionRotate() {
		if (Input.isActionJustPressed("rotate")) {
			requireSelector().rotateY(GD.degToRad(90.0))
			playAudio("sounds/rotate.ogg", -30.0)
		}
	}

	private fun actionStructureToggle() {
		var changed = false
		if (Input.isActionJustPressed("structure_next")) {
			index = Mathf.wrap(index + 1L, 0, structures.size.toLong()).toInt()
			playAudio("sounds/toggle.ogg", -30.0)
			changed = true
		}
		if (Input.isActionJustPressed("structure_previous")) {
			index = Mathf.wrap(index - 1L, 0, structures.size.toLong()).toInt()
			playAudio("sounds/toggle.ogg", -30.0)
			changed = true
		}
		if (changed) {
			updateStructure()
		}
	}

	private fun updateStructure() {
		val container = requireSelectorContainer()
		clearStructurePreview()

		val model = structures[index].model?.instantiate() ?: return
		pruneNullMeshInstances(model)
		container.addChild(model)
		if (model.isClass("Node3D")) {
			val model3d = Node3D(model.handle)
			model3d.position = model3d.position.withY(model3d.position.y.toDouble() + 0.25)
		}
	}

	private fun clearStructurePreview() {
		val container = selectorContainer ?: return
		for (child in container.getChildren()) {
			container.removeChild(child)
			child.queueFree()
		}
	}

	private fun pruneNullMeshInstances(root: Node) {
		for (node in root.findChildren("*", "MeshInstance3D", recursive = true, owned = false)) {
            val mesh = MeshInstance3D(node.handle).getMesh()
            if (mesh != null) {
                continue
            }
			node.getParent()?.removeChild(node)
			node.queueFree()
		}
	}

	private fun playAudio(soundPath: String, volumeDb: Double = -10.0) {
		val audio = self.getNodeOrNull("/root/Audio") ?: return
		audio.kotlinScriptInstance<Audio>()?.play(soundPath, volumeDb)
	}

	private fun updateCash() {
		cashDisplay?.text = "$" + map.cash
	}

	private fun actionSave() {
		if (Input.isActionJustPressed("save")) {
			GD.print("Saving map...")
			val savedStructures = mutableListOf<DataStructure>()
			val grid = requireGridMap()
			for (cell in grid.getUsedCells()) {
				val dataStructure = newScriptResource<DataStructure>("res://kotlin-src/DataStructure.kt")
				dataStructure.position = Vector2i(cell.x, cell.z)
				dataStructure.orientation = grid.getCellItemOrientation(cell).toLong()
				dataStructure.structure = grid.getCellItem(cell).toLong()
				savedStructures += dataStructure
			}
			map.structures = savedStructures
			Resource.fromObject(GodotObject(map.godotObject))?.let { ResourceSaver.save(it, "user://map.res") }
		}
	}

	private fun actionLoad() {
		if (Input.isActionJustPressed("load")) {
			GD.print("Loading map...")
			loadMap("user://map.res")
		}
	}

	private fun actionLoadResources() {
		if (Input.isActionJustPressed("load_resources")) {
			GD.print("Loading map...")
			loadMap("res://sample map/map.res")
		}
	}

	private fun loadMap(path: String) {
		requireGridMap().clear()
		map = ResourceLoader.load(path)?.asObject()?.kotlinScriptInstance<DataMap>() ?: newScriptResource("res://kotlin-src/DataMap.kt")
		for (cell in map.structures) {
			requireGridMap().setCellItem(Vector3i(cell.position.x, 0, cell.position.y), cell.structure.toInt(), cell.orientation.toInt())
		}
		updateCash()
	}

	private fun requireSelector(): Node3D = selector ?: error("Builder requires selector")
	private fun requireSelectorContainer(): Node3D = selectorContainer ?: error("Builder requires selector_container")
	private fun requireViewCamera(): Camera3D = viewCamera ?: error("Builder requires view_camera")
	private fun requireGridMap(): GridMap = gridmap ?: error("Builder requires gridmap")

	private inline fun <reified T> newScriptResource(path: String): T {
		val script = ResourceLoader.load(path) ?: error("Unable to load script resource $path")
		val owner = Resource.create()
		owner.asObject().setScript(script)
		return owner.asObject().kotlinScriptInstance<T>() ?: error("Script resource $path did not create ${T::class.simpleName}")
	}
}
