package tps

import net.multigesture.kanama.annotations.OnProcess
import net.multigesture.kanama.annotations.ScriptClass
import net.multigesture.kanama.api.DisplayServer
import net.multigesture.kanama.api.Engine
import net.multigesture.kanama.api.Input
import net.multigesture.kanama.api.KanamaScript
import net.multigesture.kanama.api.Label
import net.multigesture.kanama.api.OS
import java.lang.foreign.MemorySegment

@ScriptClass(attachTo = "Label")
class DebugLabel(godotObject: MemorySegment) : KanamaScript<Label>(godotObject, ::Label) {
    @OnProcess
    fun process(_delta: Double) {
        if (Input.isActionJustPressed("toggle_debug")) {
            self.visible = !self.visible
        }
        val online = !self.isOfflineMultiplayer()
        self.text = buildString {
            append("FPS: ").append(Engine.getFramesPerSecond())
            append("\nVSync: ").append(if (DisplayServer.windowGetVsyncMode() != DisplayServer.VSYNC_DISABLED) "Enabled" else "Disabled")
            append("\nMemory: ").append(twoDecimals(OS.getStaticMemoryUsage() / 1048576.0)).append(" MiB")
            append("\nOnline: ").append(if (online) "Yes" else "No")
            if (online) append("\nMultiplayer ID: ").append(self.getMultiplayer()?.getUniqueId() ?: 0)
        }
    }
}

// Portable 2-decimal formatting (replaces JVM-only "%.2f".format, which is absent on Kotlin/Native).
private fun twoDecimals(value: Double): String {
    val scaled = kotlin.math.round(value * 100.0).toLong()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}
