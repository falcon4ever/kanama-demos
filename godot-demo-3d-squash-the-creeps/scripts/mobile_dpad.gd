extends Control

@export var action_left: StringName = &"move_left"
@export var action_right: StringName = &"move_right"
@export var action_up: StringName = &"move_forward"
@export var action_down: StringName = &"move_back"
@export_range(0.0, 1.0, 0.01) var deadzone_ratio := 0.28

var _active_touch := -1
var _pressed_actions: Array[StringName] = []
var _tip_offset := Vector2.ZERO


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_STOP
	set_process_unhandled_input(false)


func _exit_tree() -> void:
	_set_actions([])


func _gui_input(event: InputEvent) -> void:
	if event is InputEventScreenTouch:
		if event.pressed and _active_touch == -1:
			_active_touch = event.index
			_update_from_position(event.position)
			accept_event()
		elif not event.pressed and event.index == _active_touch:
			_active_touch = -1
			_reset_pad()
			accept_event()
	elif event is InputEventScreenDrag and event.index == _active_touch:
		_update_from_position(event.position)
		accept_event()
	elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		if event.pressed:
			_active_touch = -2
			_update_from_position(event.position)
		else:
			_active_touch = -1
			_reset_pad()
		accept_event()
	elif event is InputEventMouseMotion and _active_touch == -2:
		_update_from_position(event.position)
		accept_event()


func _update_from_position(position: Vector2) -> void:
	var center := size * 0.5
	var radius := minf(size.x, size.y) * 0.5
	var offset := position - center
	var length := offset.length()

	if length > radius:
		offset = offset.normalized() * radius
	_tip_offset = offset

	if length < radius * deadzone_ratio:
		_set_actions([])
	else:
		_set_actions(_actions_for_offset(offset))

	queue_redraw()


func _actions_for_offset(offset: Vector2) -> Array[StringName]:
	var direction := wrapi(int(round(offset.angle() / (PI / 4.0))), 0, 8)
	match direction:
		0:
			return [action_right]
		1:
			return [action_right, action_down]
		2:
			return [action_down]
		3:
			return [action_left, action_down]
		4:
			return [action_left]
		5:
			return [action_left, action_up]
		6:
			return [action_up]
		7:
			return [action_right, action_up]
	return []


func _set_actions(actions: Array[StringName]) -> void:
	for action in _pressed_actions:
		if action not in actions:
			Input.action_release(action)
	for action in actions:
		if action not in _pressed_actions:
			Input.action_press(action)
	_pressed_actions = actions


func _reset_pad() -> void:
	_tip_offset = Vector2.ZERO
	_set_actions([])
	queue_redraw()


func _draw() -> void:
	var center := size * 0.5
	var radius := minf(size.x, size.y) * 0.5
	var tip_radius := radius * 0.34
	draw_circle(center, radius, Color(0.117647, 0.415686, 0.811765, 0.38))
	draw_arc(center, radius - 3.0, 0.0, TAU, 96, Color(1, 1, 1, 0.72), 6.0)
	draw_circle(center + _tip_offset, tip_radius, Color(0.0745098, 0.278431, 0.568627, 0.88))
	draw_arc(center + _tip_offset, tip_radius - 2.0, 0.0, TAU, 64, Color(1, 1, 1, 0.9), 4.0)
