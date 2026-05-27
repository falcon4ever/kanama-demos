rootProject.name = "kanama-demos"

val demos = listOf(
    "Bunnymark" to "bunnymark",
    "Starter-Kit-3D-Platformer" to "starter-kit-3d-platformer",
    "Starter-Kit-Match3" to "starter-kit-match3",
    "Starter-Kit-FPS" to "starter-kit-fps",
    "Starter-Kit-Racing" to "starter-kit-racing",
    "Starter-Kit-City-Builder" to "starter-kit-city-builder",
    "godot-demo-2d-dodge-the-creeps" to "godot-demo-2d-dodge-the-creeps",
    "godot-demo-3d-squash-the-creeps" to "godot-demo-3d-squash-the-creeps",
    "godot-4-3d-character-controller-tutorial" to "godot-4-3d-character-controller-tutorial",
    "godot-4-3d-third-person-controller" to "godot-4-3d-third-person-controller",
    "tps-demo-kanama" to "tps-demo",
)

for ((path, buildName) in demos) {
    includeBuild(path) {
        name = buildName
    }
}
