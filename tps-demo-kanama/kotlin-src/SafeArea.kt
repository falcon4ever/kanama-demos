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
    /** Shrink a full-rect UI root to the usable display rectangle. */
    fun applyInsets(control: Control) {
        val os = OS.getName()
        if (os != "iOS" && os != "Android") return
        val safe = DisplayServer.getDisplaySafeArea()
        val window = DisplayServer.windowGetSize()
        if (window.x <= 0 || window.y <= 0 || safe.size.x <= 0 || safe.size.y <= 0) return
        val canvas = control.getViewport()?.getVisibleRect()?.size ?: return
        val scaleX = canvas.x / window.x.toFloat()
        val scaleY = canvas.y / window.y.toFloat()
        val left = safe.position.x.toFloat() * scaleX
        val top = safe.position.y.toFloat() * scaleY
        val right = (window.x - safe.end.x).coerceAtLeast(0).toFloat() * scaleX
        val bottom = (window.y - safe.end.y).coerceAtLeast(0).toFloat() * scaleY
        if (left == 0.0f && top == 0.0f && right == 0.0f && bottom == 0.0f) return

        val currentPosition = control.position
        val currentSize = control.size
        control.setPosition(currentPosition + Vector2(left, top))
        control.setSize(
            Vector2(
                (currentSize.x - left - right).coerceAtLeast(0.0f),
                (currentSize.y - top - bottom).coerceAtLeast(0.0f),
            ),
        )
    }

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
