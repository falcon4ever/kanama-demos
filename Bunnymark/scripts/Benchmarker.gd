extends Control

var fps_update_interval := 1.0
var elapsed_time := 0.0
var fps_label: Label = null
var benchmark_container: Node2D = null
var benchmark_node: Node2D = null
var output_path := "user://benchmark_results.json"
var arg_bench := "--bench="
var arg_lang := "--lang="

var bunnymark_target := 60.0
var bunnymark_target_error := 0.1
var benchmark_is_bunnymark := false
var bunnymark_update_interval := 2.0
var stable_updates_required := 3
var bunnymark_update_elapsed_time := 0.0
var stable_updates := 0

var bunny_number := 0
var ping_pong_counter := 0
var increasing := true

var mobile_warmup_remaining := 0.0
var mobile_warmup_seed := 50
var mobile_max_bunny_batch := 200
var warmup_done := false
var damp_flips := 0

@export_enum("BunnymarkV1Sprites", "BunnymarkV1DrawTexture", "BunnymarkV2", "BunnymarkV3") var benchmark: String = "BunnymarkV2"
@export_enum("gd", "kanama") var language: String = "kanama"

func _ready():
    set_process(false)
    fps_label = get_node("Panel/FPS")
    benchmark_container = get_node("BenchmarkContainer")
    _apply_safe_area()

    var args = OS.get_cmdline_args() + OS.get_cmdline_user_args()
    for arg in args:
        if arg.substr(0, arg_bench.length()) == arg_bench:
            benchmark = arg.split("=")[1]
        elif arg.substr(0, arg_lang.length()) == arg_lang:
            language = arg.split("=")[1]

    start_benchmark(benchmark, language)

func _process(delta: float):
    elapsed_time += delta
    if elapsed_time >= fps_update_interval:
        fps_label.text = "FPS: " + str(Engine.get_frames_per_second())
        elapsed_time = 0.0
    if benchmark_is_bunnymark:
        if mobile_warmup_remaining > 0.0:
            mobile_warmup_remaining -= delta
            fps_label.text = "FPS: (warming up)"
            while bunny_number < mobile_warmup_seed and benchmark_node.has_method("add_bunny"):
                benchmark_node.call("add_bunny")
                bunny_number += 1
        else:
            if _is_mobile() and not warmup_done:
                bunnymark_update_elapsed_time = 0.0
                increasing = true
                warmup_done = true
            update_bunnymark(delta)

func _is_mobile() -> bool:
    return OS.has_feature("android") or OS.has_feature("ios")

# Shared top-left inset (safe-area origin + margin) applied to the on-screen
# readouts on mobile. The benchmark's "Bunnies" Label (created in Kotlin) is
# offset by this too, in start_benchmark.
var safe_inset := Vector2.ZERO

# Inset the top-left readouts past the device's rounded corner / notch.
# Stretch mode is disabled, so GUI coords map 1:1 to the screen pixels that
# DisplayServer.get_display_safe_area() reports. In landscape the safe-area top
# inset is tiny, so add a fixed margin so the FPS line clears the corner.
const SAFE_AREA_MARGIN := Vector2(24, 24)

func _apply_safe_area() -> void:
    if not _is_mobile():
        return
    var safe_area := DisplayServer.get_display_safe_area()
    safe_inset = Vector2(safe_area.position) + SAFE_AREA_MARGIN
    var panel := get_node("Panel") as Control
    panel.position += safe_inset

func get_script_path(benchmark_name: String, lang: String) -> String:
    if lang == "kanama":
        return "res://kotlin-src/" + benchmark_name + "Kanama.kt"
    return "res://benchmarks/" + benchmark_name + "/" + lang + "/" + benchmark_name + "." + lang

func start_benchmark(benchmark_name: String, lang: String):
    print(benchmark_name)
    print(lang)
    var script_path := get_script_path(benchmark_name, lang)
    benchmark_is_bunnymark = benchmark_name.begins_with("Bunnymark")
    if _is_mobile():
        mobile_warmup_remaining = 3.0
        bunnymark_target = 30.0
        warmup_done = false
        damp_flips = 0
        # Measure GPU/FFI cost, not the panel refresh (Pixel 7 = 90 Hz).
        DisplayServer.window_set_vsync_mode(DisplayServer.VSYNC_DISABLED)
    bunnymark_update_elapsed_time = bunnymark_update_interval
    var script = load(script_path)
    benchmark_node = Node2D.new()
    benchmark_node.set_script(script)
    benchmark_node.add_user_signal("benchmark_finished", ["output"])
    benchmark_node.connect("benchmark_finished", Callable(self, "benchmark_finished"))
    benchmark_container.add_child(benchmark_node)
    # The Kotlin benchmark (V2/V3) creates a "Bunnies" Label at (0, 20) in its
    # _ready; nudge it past the rounded corner on mobile (no-op elsewhere).
    if safe_inset != Vector2.ZERO:
        for label in benchmark_node.find_children("*", "Label", true, false):
            label.position += safe_inset
    bunny_number = 0
    ping_pong_counter = 0
    if benchmark_node.has_method("add_bunny"):
        set_process(true)
    else:
        benchmark_finished(0)

func benchmark_finished(output):
    print("benchmark output: ", output)
    benchmark_container.remove_child(benchmark_node)
    benchmark_node.queue_free()
    await get_tree().process_frame
    write_result(output)
    get_tree().quit()

func write_result(output):
    print("written ", output)
    var file := FileAccess.open(output_path, FileAccess.READ)
    var file_content = ""
    if file != null:
        file_content = file.get_as_text()
        file.close()

    var test_json_conv = JSON.new()
    var error := test_json_conv.parse(file_content)
    var benchmark_file: Variant = null
    if error == OK:
        benchmark_file = test_json_conv.data
    if benchmark_file == null or typeof(benchmark_file) != TYPE_DICTIONARY:
        benchmark_file = {
            "benchmark_results": {}
        }
    benchmark_file["benchmark_results"][benchmark + "_" + language] = output
    file = FileAccess.open(output_path, FileAccess.WRITE)
    benchmark_file["run_date"] = Time.get_datetime_dict_from_system()
    benchmark_file["target_fps"] = bunnymark_target
    benchmark_file["platform"] = OS.get_name()
    file.store_string(JSON.stringify(benchmark_file))

func update_bunnymark(delta):
    bunnymark_update_elapsed_time += delta
    if bunnymark_update_elapsed_time > bunnymark_update_interval:
        var fps = Engine.get_frames_per_second()
        var difference = fps - bunnymark_target
        var bunny_difference := 0
        var min_batch
        var max_batch
        var batch_scale
        var current_stability_target
        if _is_mobile():
            # Damped step: halve the max move on each direction flip so the
            # search settles instead of oscillating ±mobile_max_bunny_batch.
            min_batch = 1
            max_batch = max(1, mobile_max_bunny_batch >> damp_flips)
            batch_scale = 1
            # Cap so faster mobile flips don't loosen the tolerance into a false converge.
            current_stability_target = bunnymark_target_error * min(ping_pong_counter, 5)
        else:
            # Desktop: unchanged from main.
            min_batch = 10
            max_batch = 2000
            batch_scale = max(100, bunny_number / 1000)
            current_stability_target = bunnymark_target_error * ping_pong_counter
        print("Tolerance: " + str(current_stability_target))
        if difference > current_stability_target:
            bunny_difference = int(clamp(difference * batch_scale, min_batch, max_batch))
            if !increasing:
                increasing = true
                ping_pong_counter += 1
                if _is_mobile():
                    damp_flips += 1
            print("New Bunnies: " + str(bunny_difference))
        elif difference < -current_stability_target:
            bunny_difference = int(clamp(difference * batch_scale, -max_batch, -min_batch))
            if increasing:
                increasing = false
                ping_pong_counter += 1
                if _is_mobile():
                    damp_flips += 1
            print("Deleted Bunnies: " + str(bunny_difference))
        if abs(difference) < current_stability_target:
            stable_updates += 1
            print("Current Bunnies: " + str(bunny_number))
            if stable_updates == stable_updates_required:
                benchmark_node.call("finish")
        else:
            bunny_number += bunny_difference
            bunny_number = max(bunny_number, 0)
            if bunny_difference > 0:
                for i in range(bunny_difference):
                    benchmark_node.call("add_bunny")
            else:
                for i in range(-bunny_difference):
                    benchmark_node.call("remove_bunny")
            stable_updates = 0

        bunnymark_update_elapsed_time = 0.0
