package net.multigesture.kanama.demos.bunnymark

import net.multigesture.kanama.api.ClassDB
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.Node
import net.multigesture.kanama.api.RefCounted
import net.multigesture.kanama.api.cast
import net.multigesture.kanama.binding.runtime.ObjectCalls

/**
 * GDScript's `Label.new()` for the node classes whose Kanama wrapper has no `create()` yet; `Sprite2D.create()` does
 * exactly this. A node is not reference-counted: the tree frees it, so the wrapper owns nothing.
 */
internal inline fun <reified T : Node> newNode(noinline wrap: (GodotHandle) -> T): T =
    wrap(GodotHandle(ObjectCalls.constructObject(T::class.simpleName!!)))

/**
 * GDScript's `RandomNumberGenerator.new()`, for a reference-counted class without `create()`. `ClassDB.instantiate`
 * returns the new object as a plain `RefCounted`; the cast is a typed reference of your own, and the untyped one is
 * released.
 */
internal inline fun <reified T : RefCounted> newRefCounted(): T =
    (ClassDB.instantiate(T::class.simpleName!!) as RefCounted).use { it.cast<T>() }
