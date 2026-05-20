extends TextureRect


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_update_size()
	get_viewport().size_changed.connect(_update_size)


func _update_size() -> void:
	var viewport_rect := get_viewport_rect()
	position = viewport_rect.position
	size = viewport_rect.size
