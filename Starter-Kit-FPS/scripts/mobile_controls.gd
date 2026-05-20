extends CanvasLayer


func _ready() -> void:
	visible = OS.has_feature("android")
	if visible:
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
	for event in InputMap.action_get_events(action):
		if event is InputEventMouseButton:
			InputMap.action_erase_event(action, event)
