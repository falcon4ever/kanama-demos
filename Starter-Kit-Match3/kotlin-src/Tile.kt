import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.Area2D
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.InputEventMouseButton
import net.multigesture.kanama.api.Sprite2D
import net.multigesture.kanama.api.Texture2D
import net.multigesture.kanama.api.Tween
import net.multigesture.kanama.api.kotlinScriptInstance
import net.multigesture.kanama.generated.TileSignals
import net.multigesture.kanama.types.Color
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector2i
import java.lang.foreign.MemorySegment
import net.multigesture.kanama.api.KanamaScript

@ScriptClass(attachTo = "Area2D")
class Tile(godotObject: MemorySegment) : KanamaScript<Area2D>(godotObject, ::Area2D) {

    private var type: String = ""
    private var gridPosition: Vector2i = Vector2i.ZERO
    private val activeTweens = mutableSetOf<Tween>()

    @Signal
    fun tilePressed(pos: Vector2i) = Unit

    // Highlight tile when hovering mouse
    @RegisterFunction("_on_mouse_entered")
    fun onMouseEntered() {
        val sprite = sprite() ?: return
        val tween = trackedTween() ?: return
        tween.tweenProperty(sprite, "scale", Vector2(1.1f, 1.1f), 0.1)
        tween.tweenProperty(sprite, "modulate", Color(1.2f, 1.2f, 1.2f), 0.1)
    }

    // Return to default state when mouse exits
    @RegisterFunction("_on_mouse_exited")
    fun onMouseExited() {
        val sprite = sprite() ?: return
        val tween = trackedTween() ?: return
        tween.tweenProperty(sprite, "scale", Vector2.ONE, 0.1)
        tween.tweenProperty(sprite, "modulate", Color(1f, 1f, 1f), 0.1)
    }

    // Set piece type when initializing
    @RegisterFunction("set_tile_type")
    fun setTileType(id: String, texture: Texture2D) {
        type = id
        sprite()?.texture = texture
    }

    @RegisterFunction("set_grid_position")
    fun setGridPosition(pos: Vector2i) {
        gridPosition = pos
    }

    @RegisterFunction("get_tile_type")
    fun getTileType(): String = type

    // Letting the main code know when a tile has been pressed
    @RegisterFunction("_input_event")
    fun inputEvent(viewport: GodotObject, event: GodotObject, shapeIdx: Long) {
        val mouseButton = InputEventMouseButton.from(event) ?: return
        if (mouseButton.getButtonIndex() == InputEventMouseButton.MOUSE_BUTTON_LEFT && mouseButton.isPressed()) {
            TileSignals.tilePressed(this, gridPosition)
        }
    }

    // Animations when tile is moving
    @RegisterFunction("move_to")
    fun moveTo(targetPosition: Vector2, playSound: Boolean = true) {
        val tween = trackedTween(if (playSound) ::onMoveFinished else null) ?: return

        tween.tweenProperty(self, "position", targetPosition, 0.3)?.let { tweener ->
            tweener.setTrans(Tween.TRANS_BACK)
                .setEase(Tween.EASE_OUT)
        }

        sprite()?.let { sprite ->
            sprite.scale = Vector2(1.2f, 0.8f)
            tween.tweenProperty(sprite, "scale", Vector2.ONE, 0.3)?.let { tweener ->
                tweener.setTrans(Tween.TRANS_ELASTIC)
                    .setEase(Tween.EASE_OUT)
            }
        }
    }

    @OnExitTree
    fun exitTree() {
        for (tween in activeTweens.toList()) {
            tween.kill()
            releaseTween(tween)
        }
    }

    // Audio that plays after the tile lands on the board
    @RegisterFunction("_on_move_finished")
    fun onMoveFinished() {
        playAudio("res://sounds/tile-land.ogg", false, 1.2 - (gridPosition.y * 0.05), 0.2)
    }


    private fun playAudio(soundPath: String, allowOverlap: Boolean = false, pitch: Double = 1.0, volume: Double = 1.0) {
        val audio = self.getNodeOrNull("/root/Audio") ?: return
        audio.kotlinScriptInstance<Audio>()?.play(soundPath, allowOverlap, pitch, volume)
    }

    private fun sprite(): Sprite2D? =
        self.getAsOrNull("Sprite2D", ::Sprite2D)

    private fun trackedTween(onFinished: (() -> Unit)? = null): Tween? {
        val tween = self.createTween()?.setParallel(true) ?: return null
        activeTweens += tween
        tween.signal(Tween.Signals.finished).connect(self, argumentCount = 0, flags = GodotObject.CONNECT_ONE_SHOT) {
            releaseTween(tween)
            onFinished?.invoke()
        }
        return tween
    }

    private fun releaseTween(tween: Tween) {
        activeTweens.remove(tween)
    }
}
