package net.multigesture.kanama.demos.match3

import net.multigesture.kanama.annotations.GodotName
import net.multigesture.kanama.annotations.OnExitTree
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.annotations.Signal
import net.multigesture.kanama.api.Area2D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.GodotObject
import net.multigesture.kanama.api.InputEventMouseButton
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.MouseButton
import net.multigesture.kanama.api.Sprite2D
import net.multigesture.kanama.api.Texture2D
import net.multigesture.kanama.api.Tween
import net.multigesture.kanama.api.createTween
import net.multigesture.kanama.generated.Autoloads
import net.multigesture.kanama.generated.TileSignals
import net.multigesture.kanama.types.Color
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector2i

@ScriptClass(attachTo = "Area2D")
class Tile(godotObject: GodotHandle) : KanamaScript<Area2D>(godotObject, ::Area2D) {

  private var type: String = ""
  private var gridPosition: Vector2i = Vector2i.ZERO
  private val activeTweens = mutableSetOf<Tween>()

  @Signal fun tilePressed(pos: Vector2i) = Unit

  @Signal fun tileReleased(pos: Vector2i) = Unit

  // Highlight tile when hovering mouse
  @GodotName("_on_mouse_entered")
  fun onMouseEntered() {
    val sprite = sprite() ?: return
    val tween = trackedTween() ?: return
    tween.tweenProperty(sprite, "scale", Vector2(1.1, 1.1), 0.1)
    tween.tweenProperty(sprite, "modulate", Color(1.2, 1.2, 1.2), 0.1)
  }

  // Return to default state when mouse exits
  @GodotName("_on_mouse_exited")
  fun onMouseExited() {
    val sprite = sprite() ?: return
    val tween = trackedTween() ?: return
    tween.tweenProperty(sprite, "scale", Vector2.ONE, 0.1)
    tween.tweenProperty(sprite, "modulate", Color(1.0, 1.0, 1.0), 0.1)
  }

  // Set piece type when initializing
  fun setTileType(id: String, texture: Texture2D) {
    type = id
    sprite()?.texture = texture
  }

  fun setGridPosition(pos: Vector2i) {
    gridPosition = pos
  }

  fun getTileType(): String = type

  // Letting the main code know when a tile has been pressed
  @GodotName("_input_event")
  fun inputEvent(viewport: GodotObject, event: GodotObject, shapeIdx: Long) {
    val mouseButton = InputEventMouseButton.from(event) ?: return
    if (
      mouseButton.getButtonIndex() == MouseButton.LEFT &&
        mouseButton.isPressed()
    ) {
      TileSignals.tilePressed(this, gridPosition)
    } else if (mouseButton.isReleased()) {
      TileSignals.tileReleased(this, gridPosition)
    }
  }

  // Animations when tile is moving
  fun moveTo(targetPosition: Vector2, playSound: Boolean = true) {
    val tween = trackedTween(if (playSound) ::onMoveFinished else null) ?: return

    tween.tweenProperty(self, "position", targetPosition, 0.3).let { tweener ->
      tweener.setTrans(Tween.TransitionType.BACK).setEase(Tween.EaseType.OUT)
    }

    sprite()?.let { sprite ->
      sprite.scale = Vector2(1.2, 0.8)
      tween.tweenProperty(sprite, "scale", Vector2.ONE, 0.3).let { tweener ->
        tweener.setTrans(Tween.TransitionType.ELASTIC).setEase(Tween.EaseType.OUT)
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
  @GodotName("_on_move_finished")
  fun onMoveFinished() {
    playAudio("res://sounds/tile-land.ogg", false, 1.2 - (gridPosition.y * 0.05), 0.2)
  }

  private fun playAudio(
    soundPath: String,
    allowOverlap: Boolean = false,
    pitch: Double = 1.0,
    volume: Double = 1.0,
  ) {
    Autoloads.Audio.play(soundPath, allowOverlap, pitch, volume)
  }

  private fun sprite(): Sprite2D? = self.getAsOrNull("Sprite2D", ::Sprite2D)

  private fun trackedTween(onFinished: (() -> Unit)? = null): Tween? {
    val tween = self.createTween().setParallel(true)
    activeTweens += tween
    tween.finished.connect(GodotObject.ConnectFlags.ONE_SHOT) {
      releaseTween(tween)
      onFinished?.let { it() }
    }
    return tween
  }

  private fun releaseTween(tween: Tween) {
    activeTweens.remove(tween)
  }
}
