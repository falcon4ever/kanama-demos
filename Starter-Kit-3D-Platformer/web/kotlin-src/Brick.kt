package net.multigesture.kanama.demos.platformer3d

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.StaticBody3D

/**
 * Web port of a breakable brick platform. The desktop version explodes when hit from below
 * (Area3D signal + particles + a delayed queue_free coroutine); the Web foundation renders it as a
 * static platform, deferring the break interaction until the signal/coroutine families land.
 */
@ScriptClass(attachTo = "StaticBody3D")
class Brick(godotObject: GodotHandle) : KanamaScript<StaticBody3D>(godotObject, ::StaticBody3D) {
  @OnReady fun ready() = Unit
}
