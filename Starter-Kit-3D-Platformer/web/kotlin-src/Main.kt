package net.multigesture.kanama.demos.platformer3d

import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.RegisterFunction
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.CanvasLayer
import net.multigesture.kanama.api.DirectionalLight3D
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Node3D
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.api.RenderingServer
import net.multigesture.kanama.api.WorldEnvironment

@ScriptClass(attachTo = "Node3D")
class Main(godotObject: GodotHandle) : KanamaScript<Node3D>(godotObject, ::Node3D) {
  @OnReady
  fun ready() {
    self.requireAs("MobileControls", ::CanvasLayer).visible =
      OS.hasFeature("android") || OS.hasFeature("ios")

    if (RenderingServer.getCurrentRenderingMethod() == "gl_compatibility") {
      val sun = self.requireAs("Sun", ::DirectionalLight3D)
      sun.lightEnergy = 0.24
      sun.shadowOpacity = 0.85
      self.requireAs("Environment", ::WorldEnvironment).environment.backgroundEnergyMultiplier =
        0.25
    }
  }

  @RegisterFunction("_on_jump_button_button_down")
  fun onJumpButtonButtonDown() {
    Input.actionPress("jump")
  }

  @RegisterFunction("_on_jump_button_button_up")
  fun onJumpButtonButtonUp() {
    Input.actionRelease("jump")
  }
}
