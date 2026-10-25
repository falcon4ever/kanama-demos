package net.multigesture.kanama.demos.bulletmark

import net.multigesture.kanama.api.ClassDB
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.RefCounted
import net.multigesture.kanama.api.cast
import net.multigesture.kanama.binding.runtime.ObjectCalls
import net.multigesture.kanama.types.Rect2
import net.multigesture.kanama.types.Vector2

/**
 * GDScript's `Area2D.new()` for the node classes whose Kanama wrapper has no `create()` yet; `Sprite2D.create()` does
 * exactly this. A node is not reference-counted: the tree frees it, so the wrapper owns nothing.
 */
internal inline fun <reified T : Node> newNode(noinline wrap: (GodotHandle) -> T): T =
    wrap(GodotHandle(ObjectCalls.constructObject(T::class.simpleName!!)))

/**
 * GDScript's `CircleShape2D.new()` for a reference-counted class without `create()`. `ClassDB.instantiate` returns the
 * new object as a plain `RefCounted`; the cast is a typed reference of your own, and the untyped one is released.
 */
internal inline fun <reified T : RefCounted> newRefCounted(): T =
    (ClassDB.instantiate(T::class.simpleName!!) as RefCounted).use { it.cast<T>() }

/** GDScript's `Rect2(x, y, width, height)`. */
internal fun rect(x: Double, y: Double, width: Double, height: Double): Rect2 = Rect2(Vector2(x, y), Vector2(width, height))
