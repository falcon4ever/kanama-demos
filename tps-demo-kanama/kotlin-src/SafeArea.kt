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
 *
 * A browser page has no OS window to measure and no notch to route around -- the canvas is
 * already the usable area -- so every entry point returns before it asks the display server
 * (Kanama's Web backend has no answer for `window_get_size` / `get_display_safe_area` and says
 * so loudly). The arithmetic below is spelled in Double because desktop vectors carry `real_t`
 * (Float) components and the Web mirrors carry Double.
 */
object SafeArea {
    private fun isBrowser(): Boolean = OS.hasFeature("web")

    /** Shrink a full-rect UI root to the usable display rectangle. */
    fun applyInsets(control: Control) {
        if (isBrowser()) return
        val os = OS.getName()
        if (os != "iOS" && os != "Android") return
        val safe = DisplayServer.getDisplaySafeArea()
        val window = DisplayServer.windowGetSize()
        if (window.x <= 0 || window.y <= 0 || safe.size.x <= 0 || safe.size.y <= 0) return
        val canvas = control.getViewport()?.getVisibleRect()?.size ?: return
        val scaleX = canvas.x.toDouble() / window.x.toDouble()
        val scaleY = canvas.y.toDouble() / window.y.toDouble()
        val left = safe.position.x.toDouble() * scaleX
        val top = safe.position.y.toDouble() * scaleY
        val right = (window.x - safe.end.x).coerceAtLeast(0).toDouble() * scaleX
        val bottom = (window.y - safe.end.y).coerceAtLeast(0).toDouble() * scaleY
        if (left == 0.0 && top == 0.0 && right == 0.0 && bottom == 0.0) return

        val currentPosition = control.position
        val currentSize = control.size
        control.setPosition(currentPosition + Vector2(left, top))
        control.setSize(
            Vector2(
                (currentSize.x.toDouble() - left - right).coerceAtLeast(0.0),
                (currentSize.y.toDouble() - top - bottom).coerceAtLeast(0.0),
            ),
        )
    }

    fun applyTopLeftInset(control: Control) {
        if (isBrowser()) return
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
                current.x.toDouble() + safe.position.x.toDouble() * canvas.x.toDouble() / window.x.toDouble(),
                current.y.toDouble() + safe.position.y.toDouble() * canvas.y.toDouble() / window.y.toDouble(),
            ),
        )
    }
}
