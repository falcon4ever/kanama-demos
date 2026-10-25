package net.multigesture.kanama.demos.bulletmark

import net.multigesture.kanama.types.Rect2
import net.multigesture.kanama.types.Vector2

/** GDScript's `Rect2(x, y, width, height)`. */
internal fun rect(x: Double, y: Double, width: Double, height: Double): Rect2 = Rect2(Vector2(x, y), Vector2(width, height))
