extends Node3D

@onready var mobile_controls: CanvasLayer = $MobileControls

func _ready() -> void:
	mobile_controls.visible = OS.has_feature("android")

	if RenderingServer.get_current_rendering_method() == "gl_compatibility":
		# Reduce background and sun brightness when using the Compatibility renderer;
		# this tries to roughly match the appearance of Forward+.
		# This compensates for the different color space and light rendering for lights with shadows enabled.
		$Sun.light_energy = 0.24
		$Sun.shadow_opacity = 0.85
		$Environment.environment.background_energy_multiplier = 0.25


func _on_jump_button_button_down() -> void:
	Input.action_press("jump")


func _on_jump_button_button_up() -> void:
	Input.action_release("jump")
