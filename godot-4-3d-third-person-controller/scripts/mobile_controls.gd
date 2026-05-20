extends CanvasLayer


func _ready() -> void:
	visible = OS.has_feature("android")
	if visible:
		_remove_mouse_bindings(&"attack")
		_remove_mouse_bindings(&"aim")
	for button in find_children("*", "Button", true, false):
		var action_value := String(button.get_meta("action", ""))
		if action_value.is_empty():
			continue
		var action := StringName(action_value)
		button.button_down.connect(_press_action.bind(action))
		button.button_up.connect(_release_action.bind(action))


func _press_action(action: StringName) -> void:
	if action == &"pause":
		_emit_escape_key_event(true)
		_emit_escape_key_event(false)
		return
	Input.action_press(action)


func _release_action(action: StringName) -> void:
	Input.action_release(action)


func _remove_mouse_bindings(action: StringName) -> void:
	for event in InputMap.action_get_events(action):
		if event is InputEventMouseButton:
			InputMap.action_erase_event(action, event)


func _emit_escape_key_event(pressed: bool) -> void:
	var event := InputEventKey.new()
	event.keycode = KEY_ESCAPE
	event.physical_keycode = KEY_ESCAPE
	event.pressed = pressed
	Input.parse_input_event(event)
