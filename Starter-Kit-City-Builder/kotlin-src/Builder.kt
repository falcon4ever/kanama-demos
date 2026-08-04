package citybuilder

import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.Process
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.Camera3D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.GridMap
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.Mesh
import net.multigesture.kanama.api.MeshInstance3D
import net.multigesture.kanama.api.MeshLibrary
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.OwnedScriptResource
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.Resource
import net.multigesture.kanama.api.ResourceLoader
import net.multigesture.kanama.api.ResourceSaver
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.api.newScriptInstance
import net.multigesture.kanama.types.Plane
import net.multigesture.kanama.types.Transform3D
import net.multigesture.kanama.types.Vector2i
import net.multigesture.kanama.types.Vector3
import net.multigesture.kanama.types.Vector3i

@ScriptClass(attachTo = "Node3D")
class Builder(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {

  @ScriptProperty var structures: List<Structure> = emptyList()

  @ScriptProperty var selector: Node3D? = null

  @ScriptProperty var selectorContainer: Node3D? = null

  @ScriptProperty var viewCamera: Camera3D? = null

  @ScriptProperty var gridmap: GridMap? = null

  @ScriptProperty var cashDisplay: Label? = null

  private lateinit var map: DataMap
  // Owning reference for `map` when we created it via newScriptInstance() (null when `map` was
  // loaded from the resource cache, which owns it). Released on reassign / exit_tree. Held as
  // the OwnedScriptResource handle: the portable owner spelling (its `.resource` wrapper is
  // typed differently per platform, but close() delegates identically on both).
  private var mapOwner: OwnedScriptResource<DataMap>? = null
  private var meshLibrary: MeshLibrary? = null
  private var index = 0
  private val plane = Plane(Vector3.UP, Vector3.ZERO.y)

  @OnReady
  fun ready() {
    map = adoptFreshMap()
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
    // close what you create (Kanama task 61): MeshLibrary.create() returns an owned wrapper.
    // The GridMap ref is dropped above; release ours so the library frees.
    meshLibrary?.close()
    meshLibrary = null
    // close what you create: release the DataMap resource if we own it (loaded maps are
    // owned by the resource cache, so mapOwner is null for those).
    mapOwner?.close()
    mapOwner = null
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
    val worldPosition =
      plane.intersectsRay(
        camera.projectRayOrigin(mousePosition),
        camera.projectRayNormal(mousePosition),
      ) ?: return
    val gridmapPosition =
      Vector3i(
        worldPosition.x.toDouble().let { Mathf.roundToInt(it).toInt() },
        0,
        worldPosition.z.toDouble().let { Mathf.roundToInt(it).toInt() },
      )

    val selectorNode = requireSelector()
    selectorNode.position =
      selectorNode.position.lerp(
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
        val propertyValue =
          sceneState.getNodePropertyValue(nodeIndex, propertyIndex) as? GodotObject ?: continue
        val mesh = Mesh.fromObject(propertyValue) ?: continue
        val duplicate = mesh.duplicate() ?: continue
        return Mesh.fromObject(duplicate)
      }
    }
    return null
  }

  private fun actionBuild(gridmapPosition: Vector3i) {
    if (Input.isActionJustPressed("build")) {
      val grid = requireGridMap()
      val previousTile = grid.getCellItem(gridmapPosition)
      grid.setCellItem(
        gridmapPosition,
        index,
        grid.getOrthogonalIndexFromBasis(requireSelector().basis),
      )

      if (previousTile != index) {
        map.cash -= structures[index].price
        updateCash()
        playAudio(
          "sounds/placement-a.ogg, sounds/placement-b.ogg, sounds/placement-c.ogg, sounds/placement-d.ogg",
          -20.0,
        )
      }
    }
  }

  private fun actionDemolish(gridmapPosition: Vector3i) {
    if (Input.isActionJustPressed("demolish")) {
      val grid = requireGridMap()
      if (grid.getCellItem(gridmapPosition) != GridMap.INVALID_CELL_ITEM.toInt()) {
        grid.setCellItem(gridmapPosition, GridMap.INVALID_CELL_ITEM.toInt())
        playAudio(
          "sounds/removal-a.ogg, sounds/removal-b.ogg, sounds/removal-c.ogg, sounds/removal-d.ogg",
          -20.0,
        )
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
    pruneNullMeshInstances(Node(model.handle))
    container.addChild(Node(model.handle))
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
      Node(node.getParent()?.handle ?: continue).removeChild(node)
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
      val owned = mutableListOf<OwnedScriptResource<DataStructure>>()
      val grid = requireGridMap()
      for (cell in grid.getUsedCells()) {
        val handle = newScriptInstance<DataStructure>()
        handle.instance.position = Vector2i(cell.x, cell.z)
        handle.instance.orientation = grid.getCellItemOrientation(cell).toLong()
        handle.instance.structure = grid.getCellItem(cell).toLong()
        savedStructures += handle.instance
        owned += handle
      }
      map.structures = savedStructures
      Resource.fromObject(GodotObject(map.godotObject))?.let {
        ResourceSaver.save(it, "user://map.res")
      }
      // close what you create: `map.structures` now holds engine references to each
      // DataStructure, so release the creation references we took above.
      owned.forEach { it.close() }
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
    map =
      ResourceLoader.load(path)?.kotlinScriptInstance<DataMap>()?.let { adoptLoadedMap(it) }
        ?: adoptFreshMap()
    for (cell in map.structures) {
      requireGridMap()
        .setCellItem(
          Vector3i(cell.position.x, 0, cell.position.y),
          cell.structure.toInt(),
          cell.orientation.toInt(),
        )
    }
    updateCash()
  }

  private fun requireSelector(): Node3D = selector ?: error("Builder requires selector")

  private fun requireSelectorContainer(): Node3D =
    selectorContainer ?: error("Builder requires selector_container")

  private fun requireViewCamera(): Camera3D = viewCamera ?: error("Builder requires view_camera")

  private fun requireGridMap(): GridMap = gridmap ?: error("Builder requires gridmap")

  // Creates a fresh DataMap we own, releasing any map we previously owned. Records the owning
  // reference in `mapOwner` so exit_tree / the next load can release it (close what you create).
  private fun adoptFreshMap(): DataMap {
    val owned = newScriptInstance<DataMap>()
    mapOwner?.close()
    mapOwner = owned
    return owned.instance
  }

  // Adopts a DataMap loaded from the resource cache (the cache owns it), releasing any map we
  // previously owned ourselves.
  private fun adoptLoadedMap(instance: DataMap): DataMap {
    mapOwner?.close()
    mapOwner = null
    return instance
  }
}
