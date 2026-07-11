package tps

import net.multigesture.kanama.api.Control
import net.multigesture.kanama.api.DisplayServer
import net.multigesture.kanama.api.OS
import net.multigesture.kanama.types.Vector2

/**
 * Mobile display safe-area helper (task 26). Phones have rounded corners,
 * notches, and camera cutouts; HUD elements anchored at absolute canvas
 * offsets get clipped by them. This converts the screen-pixel safe-area inset
 * (`DisplayServer.get_display_safe_area`) into canvas coordinates under the
 * project's `canvas_items` stretch and nudges a Control inside it.
 * No-op on desktop and when the display reports no inset.
 */
object SafeArea {
    fun applyTopLeftInset(control: Control) {
        val os = OS.getName()
        if (os != "iOS" && os != "Android") return
        val safe = DisplayServer.getDisplaySafeArea()
        if (safe.position.x == 0 && safe.position.y == 0) return
        val window = DisplayServer.windowGetSize()
        if (window.x <= 0 || window.y <= 0) return
        val canvas = control.getViewport()?.getVisibleRect()?.size ?: return
        val current = control.position
        control.setPosition(
            Vector2(
                current.x + safe.position.x.toFloat() * canvas.x / window.x.toFloat(),
                current.y + safe.position.y.toFloat() * canvas.y / window.y.toFloat(),
            ),
        )
    }
}
