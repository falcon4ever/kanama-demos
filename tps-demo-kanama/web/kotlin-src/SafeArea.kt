package tps

import net.multigesture.kanama.api.CanvasItem

/**
 * Mobile display safe-area helper (task 26) — inert on Web.
 *
 * The desktop/mobile helper converts `DisplayServer.get_display_safe_area` into canvas
 * coordinates for notches and rounded corners. Browsers report no such inset (the canvas is
 * already the usable area), and the desktop code no-ops on any non-iOS/Android platform, so the
 * Web port keeps the call sites and does nothing.
 */
object SafeArea {
  fun applyInsets(@Suppress("UNUSED_PARAMETER") control: CanvasItem) = Unit

  fun applyTopLeftInset(@Suppress("UNUSED_PARAMETER") control: CanvasItem) = Unit
}
