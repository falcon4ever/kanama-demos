import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.annotations.ExportSubgroup
import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.OnInput
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.ScriptProperty
import net.multigesture.kanama.api.Area2D
import net.multigesture.kanama.api.GD
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEventMouseButton
import net.multigesture.kanama.api.KanamaCoroutineOwner
import net.multigesture.kanama.api.KanamaScope
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.Node2D
import net.multigesture.kanama.api.PackedScene
import net.multigesture.kanama.api.SceneTree
import net.multigesture.kanama.api.Texture2D
import net.multigesture.kanama.api.Tween
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.generated.MainNames
import net.multigesture.kanama.generated.TileNames
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector2i
import net.multigesture.kanama.api.KanamaScript
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.launch

@ScriptClass(attachTo = "Node2D")
class Main(godotObject: MemorySegment) : KanamaScript<Node2D>(godotObject, ::Node2D), KanamaCoroutineOwner {
    override val kanamaScope = KanamaScope()


    @ExportSubgroup("Properties")
    @ScriptProperty
    var width: Long = 8

    @ScriptProperty
    var height: Long = 8

    @ScriptProperty
    var offset: Long = 68

    @ExportSubgroup("Scenes")
    @ScriptProperty
    var tileScene: PackedScene? = null

    @ScriptProperty
    var sparklesScene: PackedScene? = null

    @ExportSubgroup("Tiles")
    @ScriptProperty
    var textures: List<Texture2D> = emptyList()

    @ExportSubgroup("Cursors")
    @ScriptProperty
    var openHandCursor: Texture2D? = null

    @ScriptProperty
    var closedHandCursor: Texture2D? = null

    private lateinit var container: Node2D

    // State
    private var grid = mutableListOf<MutableList<TileRef?>>()
    private var firstTouch = Vector2i(-1, -1)
    private var isSwapping = false
    private var comboCount = 0
    private val activeTweens = mutableSetOf<Tween>()

    // Functions
    @OnReady
    fun ready() {
        container = self.requireAs("Board", ::Node2D)

        setCursor(openHandCursor)

        setupGridArray()
        kanamaScope.launch {
            processBoardState()
        }

        centerGridOnScreen()
        self.getViewport()?.signal("size_changed")?.connect(self, "center_grid_on_screen")
    }

    @OnExitTree
    fun exitTree() {
        kanamaScope.cancel()
        for (tween in activeTweens.toList()) {
            tween.kill()
            releaseTween(tween)
        }
        Input.setCustomMouseCursor(null)
        tileScene = null
        sparklesScene = null
        textures = emptyList()
        openHandCursor = null
        closedHandCursor = null
        grid.clear()
    }

    // Centers the board on-screen; the connection above keeps it centered after resizing the window.
    @RegisterFunction("center_grid_on_screen")
    fun centerGridOnScreen() {
        val viewportRect = self.getViewport()?.getVisibleRect() ?: return
        container.position = viewportRect.size / 2.0 - Vector2((width - 1).toFloat(), (height - 1).toFloat()) * offset.toDouble() / 2.0
    }

    // Initialize grid
    private fun setupGridArray() {
        grid = MutableList(width.toInt()) { MutableList<TileRef?>(height.toInt()) { null } }

        // Spawn initial pieces
        for (x in 0 until width.toInt()) {
            for (y in 0 until height.toInt()) {
                spawnAt(x, y)
            }
        }
    }

    // Spawn a new tile at a certain grid position
    private fun spawnAt(x: Int, y: Int) {
        val scene = checkNotNull(tileScene) { "tile_scene is not assigned" }
        check(textures.isNotEmpty()) { "textures is empty" }

        val createdPiece = scene.instantiate()?.let { Area2D(it.handle) }
            ?: error("tile_scene.instantiate() returned null")
        val createdTile = createdPiece.kotlinScriptInstance<Tile>()
            ?: error("tile_scene root did not create Tile script")
        val randomIndex = GD.randiRange(0, textures.size.toLong() - 1).toInt()

        container.addChild(createdPiece)

        createdTile.setTileType(randomIndex.toString(), textures[randomIndex])
        createdPiece.signal(TileNames.Signals.tilePressed)
            .connect(self, MainNames.Methods.onTilePressed)
        createdTile.setGridPosition(Vector2i(x, y))
        createdPiece.position = gridToPixel(x, y)

        grid[x][y] = TileRef(createdPiece, createdTile, randomIndex.toString(), Vector2i(x, y))
    }

    // Interaction
    @RegisterFunction("_on_tile_pressed")
    fun onTilePressed(gridPosition: Vector2i) {
        if (!isSwapping) {
            firstTouch = gridPosition
            setCursor(closedHandCursor)
        }
    }

    @OnInput
    fun input(event: GodotObject) {
        val mouseButton = InputEventMouseButton.from(event) ?: return
        if (mouseButton.getButtonIndex() == InputEventMouseButton.MOUSE_BUTTON_LEFT && mouseButton.isReleased()) {
            if (firstTouch != Vector2i(-1, -1)) {
                calculateSwipe(container.getLocalMousePosition())
            }
        }
    }

    private fun calculateSwipe(finalPos: Vector2) {
        val difference = finalPos - gridToPixel(firstTouch.x, firstTouch.y)

        if (difference.length() > 32.0) {
            var otherTouch = firstTouch
            otherTouch = if (Mathf.abs(difference.x) > Mathf.abs(difference.y)) {
                Vector2i(otherTouch.x + if (difference.x > 0f) 1 else -1, otherTouch.y)
            } else {
                Vector2i(otherTouch.x, otherTouch.y + if (difference.y > 0f) 1 else -1)
            }

            if (isWithinGrid(otherTouch)) {
                handleSwapLogic(firstTouch, otherTouch)
                playAudio("res://sounds/tile-swap.ogg", false, GD.randfRange(0.8, 1.2), 0.3)
            }
        }

        setCursor(openHandCursor)
        firstTouch = Vector2i(-1, -1)
    }

    // Game loop
    private fun handleSwapLogic(posA: Vector2i, posB: Vector2i) {
        kanamaScope.launch {
            isSwapping = true
            swapPieces(posA, posB)

            SceneTree.delaySeconds(0.3)

            if (findMatches().isNotEmpty()) {
                processBoardState()
            } else {
                swapPieces(posA, posB)
                playAudio("res://sounds/tile-swap.ogg", false, 2.0, 0.3)
                SceneTree.delaySeconds(0.3)
                isSwapping = false
            }
        }
    }

    private fun swapPieces(a: Vector2i, b: Vector2i) {
        val pieceA = grid[a.x][a.y]
        val pieceB = grid[b.x][b.y]

        if (pieceA != null && pieceB != null) {
            grid[a.x][a.y] = pieceB
            grid[b.x][b.y] = pieceA

            pieceA.gridPosition = b
            pieceB.gridPosition = a
            pieceA.tile.setGridPosition(b)
            pieceB.tile.setGridPosition(a)

            pieceA.tile.moveTo(gridToPixel(b.x, b.y), false)
            pieceB.tile.moveTo(gridToPixel(a.x, a.y), false)
        }
    }

    private fun findMatches(): List<TileRef> {
        val matched = LinkedHashSet<TileRef>()

        for (y in 0 until height.toInt()) {
            for (x in 0 until width.toInt() - 2) {
                val p1 = grid[x][y]
                val p2 = grid[x + 1][y]
                val p3 = grid[x + 2][y]
                if (p1 != null && p2 != null && p3 != null && p1.type == p2.type && p1.type == p3.type) {
                    matched += p1
                    matched += p2
                    matched += p3
                }
            }
        }

        for (x in 0 until width.toInt()) {
            for (y in 0 until height.toInt() - 2) {
                val p1 = grid[x][y]
                val p2 = grid[x][y + 1]
                val p3 = grid[x][y + 2]
                if (p1 != null && p2 != null && p3 != null && p1.type == p2.type && p1.type == p3.type) {
                    matched += p1
                    matched += p2
                    matched += p3
                }
            }
        }

        return matched.toList()
    }

    private suspend fun processBoardState() {
        comboCount = 0
        var matches = findMatches()

        while (matches.isNotEmpty()) {
            comboCount += 1
            playAudio("res://sounds/tile-match.ogg", true, 1.0 + (comboCount * 0.1))
            val piecesToFree = mutableListOf<Area2D>()

            for (piece in matches) {
                val effect = sparklesScene?.instantiate()?.let { Node2D(it.handle) }
                if (effect != null) {
                    effect.position = piece.node.position
                    container.addChild(effect)
                }

                grid[piece.gridPosition.x][piece.gridPosition.y] = null

                val tween = trackedTween()
                tween?.tweenProperty(piece.node, "scale", Vector2.ZERO, 0.2)
                piecesToFree += piece.node
            }

            SceneTree.delaySeconds(0.3)
            piecesToFree.forEach { it.queueFree() }
            collapseColumns()
            refillBoard()

            matches = findMatches()
        }

        isSwapping = false
    }

    private suspend fun collapseColumns() {
        for (x in 0 until width.toInt()) {
            for (y in height.toInt() - 1 downTo 0) {
                if (grid[x][y] == null) {
                    for (k in y - 1 downTo 0) {
                        if (grid[x][k] != null) {
                            grid[x][y] = grid[x][k]
                            grid[x][k] = null
                            grid[x][y]?.let { tile ->
                                tile.gridPosition = Vector2i(x, y)
                                tile.tile.setGridPosition(tile.gridPosition)
                                tile.tile.moveTo(gridToPixel(x, y), true)
                            }
                            break
                        }
                    }
                }
            }
        }
        SceneTree.delaySeconds(0.3)
    }

    private suspend fun refillBoard() {
        for (x in 0 until width.toInt()) {
            for (y in 0 until height.toInt()) {
                if (grid[x][y] == null) {
                    spawnAt(x, y)
                    val tile = grid[x][y] ?: continue
                    tile.node.position = tile.node.position.withY(tile.node.position.y - offset * 2)
                    tile.tile.moveTo(gridToPixel(x, y), true)
                }
            }
        }
        SceneTree.delaySeconds(0.3)
    }


    private fun playAudio(soundPath: String, allowOverlap: Boolean = false, pitch: Double = 1.0, volume: Double = 1.0) {
        val audio = self.getNodeOrNull("/root/Audio") ?: return
        audio.kotlinScriptInstance<Audio>()?.play(soundPath, allowOverlap, pitch, volume)
    }

    // Utilities for coordinates
    private fun gridToPixel(column: Int, row: Int): Vector2 =
        Vector2((offset * column).toFloat(), (offset * row).toFloat())

    private fun isWithinGrid(pos: Vector2i): Boolean =
        pos.x >= 0 && pos.x < width.toInt() && pos.y >= 0 && pos.y < height.toInt()

    // Utilities
    private fun setCursor(cursorTexture: Texture2D?) {
        Input.setCustomMouseCursor(cursorTexture, hotspot = Vector2(16f, 16f))
    }

    private fun trackedTween(): Tween? {
        val tween = self.createTween() ?: return null
        activeTweens += tween
        tween.signal(Tween.Signals.finished).connect(self, argumentCount = 0, flags = GodotObject.CONNECT_ONE_SHOT) {
            releaseTween(tween)
        }
        return tween
    }

    private fun releaseTween(tween: Tween) {
        activeTweens.remove(tween)
    }

    private data class TileRef(
        val node: Area2D,
        val tile: Tile,
        val type: String,
        var gridPosition: Vector2i,
    )
}
