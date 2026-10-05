package net.multigesture.kanama.demos.match3

import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.InputEventScreenTouch
import net.multigesture.kanama.api.MainThread
import net.multigesture.kanama.types.Vector2
import net.multigesture.kanama.types.Vector2i

/**
 * Smoke-only (called by SmokeQuit under its smoke switch): one tile swipe made of TOUCH events.
 *
 * It feeds `Input.parse_input_event` an `InputEventScreenTouch` (index 0) pressed on a tile
 * centre and released 0.8 x spacing to the right, so the swipe reaches the game the way a finger
 * does on a phone: Godot's `emulate_mouse_from_touch` (on by default) turns the touch into the
 * `InputEventMouseButton` press the tile's `_input_event` picks up and the release `Main._input`
 * reads. Kanama task 129 A made iOS `InputEventMouseButton.from(touch)` return null, so this
 * emulation is the only touch path left; the smoke proves it on desktop and on the phone.
 *
 * Web has its own copy (web/kotlin-src/SmokeTouchSwipe.kt) that skips: the Web API has no
 * `Input.parseInputEvent`.
 */
internal object SmokeTouchSwipe {
  private val CELL = Vector2i(3, 3)

  /** The result line, ending in `releases=<n> moves=<n>`; null when the platform cannot inject. */
  suspend fun run(main: Main): String? {
    var frames = 0
    while (!main.isBoardIdle && frames < 600) {
      MainThread.awaitNextFrame()
      frames++
    }
    val board = main.board
    val viewport = requireNotNull(board.getViewport()) { "the Match3 board is not in a viewport" }
    // Board-local -> canvas -> window: touch positions are window coordinates, and the stretch
    // transform (canvas_items) differs between a desktop window and a phone screen.
    val toWindow = viewport.getFinalTransform() * board.getGlobalTransformWithCanvas()
    val start = main.cellCenter(CELL)
    val end = start + Vector2(main.offset.toDouble() * 0.8, 0.0)
    val pressAt = toWindow * start
    val releaseAt = toWindow * end
    touch(pressAt, pressed = true)
    awaitFrames(10)
    touch(releaseAt, pressed = false)
    awaitFrames(10)
    return "cell=(${CELL.x}, ${CELL.y}) press=(${pressAt.x}, ${pressAt.y}) " +
      "release=(${releaseAt.x}, ${releaseAt.y}) releases=${main.dragReleases} moves=${main.moves}"
  }

  private fun touch(at: Vector2, pressed: Boolean) {
    InputEventScreenTouch.create().use { event ->
      event.index = 0
      event.position = at
      event.setPressed(pressed)
      Input.parseInputEvent(event)
    }
  }

  private suspend fun awaitFrames(count: Int) {
    repeat(count) { MainThread.awaitNextFrame() }
  }
}
