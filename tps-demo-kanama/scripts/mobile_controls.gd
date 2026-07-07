extends CanvasLayer

# Touch controls for tps-demo-kanama, mirroring the mobile control pattern used
# by godot-4-3d-third-person-controller. The VirtualJoystick nodes drive the
# movement / look input actions directly; this script only wires the on-screen
# action buttons (jump / aim / shoot) and removes the mouse bindings that would
# otherwise let stray touches fire aim/shoot on a touch device.
#
# Only visible on touch platforms, so desktop keyboard/mouse/gamepad play is
# unchanged.


func _ready() -> void:
	visible = OS.has_feature("android") or OS.has_feature("ios")
	if not visible:
		return
	# aim + shoot are bound to mouse buttons on desktop; drop those on touch so
	# emulated-mouse-from-touch taps do not trigger them.
	_remove_mouse_bindings(&"aim")
	_remove_mouse_bindings(&"shoot")
	for button in find_children("*", "Button", true, false):
		var action_value := String(button.get_meta("action", ""))
		if action_value.is_empty():
			continue
		var action := StringName(action_value)
		button.button_down.connect(_press_action.bind(action))
		button.button_up.connect(_release_action.bind(action))


func _press_action(action: StringName) -> void:
	Input.action_press(action)


func _release_action(action: StringName) -> void:
	Input.action_release(action)


func _remove_mouse_bindings(action: StringName) -> void:
	if not InputMap.has_action(action):
		return
	for event in InputMap.action_get_events(action):
		if event is InputEventMouseButton:
			InputMap.action_erase_event(action, event)
