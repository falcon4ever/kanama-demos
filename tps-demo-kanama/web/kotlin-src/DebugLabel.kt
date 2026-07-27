package tps

import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.OnReady
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.DisplayServer
import net.multigesture.kanama.api.Engine
import net.multigesture.kanama.api.GodotHandle
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.Mathf
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.api.getMultiplayer
import net.multigesture.kanama.api.getStaticMemoryUsage

@ScriptClass(attachTo = "Label")
class DebugLabel(godotObject: GodotHandle) : KanamaScript<Label>(godotObject, ::Label) {
  @OnReady
  fun ready() {
    // Keep the top-left debug readout out of the phone's rounded corner /
    // camera cutout (task 26 safe-area work). No-op on desktop and Web.
    SafeArea.applyTopLeftInset(self)
  }

  @OnProcess
  fun process(delta: Double) {
    if (Input.isActionJustPressed("toggle_debug")) {
      self.setVisible(!self.isVisible())
    }
    val online = !self.isOfflineMultiplayer()
    self.text = buildString {
      append("FPS: ").append(Engine.getFramesPerSecond())
      append("\nVSync: ")
        .append(
          if (DisplayServer.windowGetVsyncMode() != DisplayServer.VSYNC_DISABLED) "Enabled"
          else "Disabled"
        )
      append("\nMemory: ").append(twoDecimals(OS.getStaticMemoryUsage() / 1048576.0)).append(" MiB")
      append("\nOnline: ").append(if (online) "Yes" else "No")
      if (online) append("\nMultiplayer ID: ").append(self.getMultiplayer()?.getUniqueId() ?: 0)
    }
  }
}

// Portable 2-decimal formatting (replaces JVM-only "%.2f".format).
private fun twoDecimals(value: Double): String {
  val scaled = Mathf.roundToInt(value * 100.0)
  return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}
