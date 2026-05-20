data class DemoBuild(
    val buildName: String,
    val projectPath: String,
    val taskPrefix: String,
    val displayName: String,
)

fun gradleCommand(projectDir: File): List<String> {
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    if (!isWindows) return listOf(projectDir.resolve("gradlew").absolutePath)
    val batWrapper = projectDir.resolve("gradlew.bat")
    if (batWrapper.isFile) return listOf(batWrapper.absolutePath)
    val bash = file("C:/Program Files/Git/bin/bash.exe").takeIf { it.isFile }?.absolutePath ?: "bash"
    return listOf(bash, projectDir.resolve("gradlew").absolutePath)
}

val demoBuilds = listOf(
    DemoBuild("bunnymark", "Bunnymark", "bunnymark", "Bunnymark"),
    DemoBuild("starter-kit-3d-platformer", "Starter-Kit-3D-Platformer", "platformer", "Starter Kit 3D Platformer"),
    DemoBuild("starter-kit-match3", "Starter-Kit-Match3", "match3", "Starter Kit Match-3"),
    DemoBuild("starter-kit-fps", "Starter-Kit-FPS", "fps", "Starter Kit FPS"),
    DemoBuild("starter-kit-racing", "Starter-Kit-Racing", "racing", "Starter Kit Racing"),
    DemoBuild("starter-kit-city-builder", "Starter-Kit-City-Builder", "cityBuilder", "Starter Kit City Builder"),
    DemoBuild("godot-demo-2d-dodge-the-creeps", "godot-demo-2d-dodge-the-creeps", "dodge", "Godot Dodge the Creeps"),
    DemoBuild("godot-demo-3d-squash-the-creeps", "godot-demo-3d-squash-the-creeps", "squash", "Godot Squash the Creeps"),
    DemoBuild("godot-4-3d-character-controller-tutorial", "godot-4-3d-character-controller-tutorial", "characterController", "GDQuest Character Controller"),
    DemoBuild("godot-4-3d-third-person-controller", "godot-4-3d-third-person-controller", "thirdPerson", "GDQuest Third Person Controller"),
)

for (demo in demoBuilds) {
    tasks.register("${demo.taskPrefix}BuildScripts") {
        group = "kanama demos"
        description = "Build Kotlin scripts for ${demo.displayName}."
        dependsOn(gradle.includedBuild(demo.buildName).task(":buildScripts"))
    }

    tasks.register("${demo.taskPrefix}RunGodot") {
        group = "kanama demos"
        description = "Run ${demo.displayName} in Godot."
        dependsOn(gradle.includedBuild(demo.buildName).task(":runGodot"))
    }

    tasks.register("${demo.taskPrefix}OpenGodotEditor") {
        group = "kanama demos"
        description = "Open ${demo.displayName} in the Godot editor."
        dependsOn(gradle.includedBuild(demo.buildName).task(":openGodotEditor"))
    }

    tasks.register("${demo.taskPrefix}ImportGodot") {
        group = "kanama demos"
        description = "Import ${demo.displayName} assets in Godot."
        dependsOn(gradle.includedBuild(demo.buildName).task(":importGodot"))
    }

    tasks.register("${demo.taskPrefix}BuildAndRunGodot") {
        group = "kanama demos"
        description = "Build Kotlin scripts, then run ${demo.displayName} in Godot."
        dependsOn(gradle.includedBuild(demo.buildName).task(":buildAndRunGodot"))
    }

}

tasks.register("buildAllScripts") {
    group = "kanama"
    description = "Build Kotlin scripts for every demo project."
    doLast {
        buildScriptsSequentially(demoBuilds)
    }
}

tasks.register("importAllGodot") {
    group = "verification"
    description = "Build scripts and import Godot assets for every demo project."
    doLast {
        runDemoTasksSequentially(demoBuilds, "buildScripts", "importGodot")
    }
}

tasks.register<Exec>("demoParityAudit") {
    group = "kanama"
    description = "Audit ported Kotlin demo scripts for risky GDScript parity drift patterns."
    commandLine("python3", "scripts/demo_parity_audit.py", "--root", projectDir.absolutePath)
}

tasks.register<Exec>("androidSmokeAll") {
    group = "verification"
    description = "Run Android smoke validation for all Android-enabled demos."
    val defaultGodotBin = file("/Applications/Godot.app/Contents/MacOS/Godot")
        .takeIf { it.exists() }
        ?.absolutePath
        ?: "godot"
    val godotBin = providers.gradleProperty("kanama.godot.executable")
        .orElse(providers.gradleProperty("godotBin"))
        .orElse(providers.environmentVariable("KANAMA_GODOT"))
        .getOrElse(defaultGodotBin)
    commandLine("scripts/android_smoke_all.sh", godotBin)
}

tasks.register<Exec>("desktopSmokeAll") {
    group = "verification"
    description = "Run desktop headless smoke validation for smoke-enabled demos."
    val defaultGodotBin = file("/Applications/Godot.app/Contents/MacOS/Godot")
        .takeIf { it.exists() }
        ?.absolutePath
        ?: "godot"
    val godotBin = providers.gradleProperty("kanama.godot.executable")
        .orElse(providers.gradleProperty("godotBin"))
        .orElse(providers.environmentVariable("KANAMA_GODOT"))
        .getOrElse(defaultGodotBin)
    commandLine("scripts/desktop_smoke_all.sh", godotBin)
}

tasks.register("check") {
    group = "verification"
    description = "Run kanama-demos repository checks."
    dependsOn("demoParityAudit")
}

tasks.register("buildStarterKitScripts") {
    group = "kanama"
    description = "Build Kotlin scripts for the Kenney starter kit demos."
    doLast {
        buildScriptsSequentially(demoBuilds.take(5))
    }
}

tasks.register("buildReferenceDemoScripts") {
    group = "kanama"
    description = "Build Kotlin scripts for the Godot and GDQuest reference demos."
    doLast {
        buildScriptsSequentially(demoBuilds.drop(5))
    }
}

fun buildScriptsSequentially(demos: List<DemoBuild>) {
    runDemoTasksSequentially(demos, "buildScripts")
}

fun runDemoTasksSequentially(demos: List<DemoBuild>, vararg tasks: String) {
    for (demo in demos) {
        logger.lifecycle("Running ${tasks.joinToString(" ")} for ${demo.displayName}")
        providers.exec {
            val demoDir = file(demo.projectPath)
            workingDir = demoDir
            commandLine(gradleCommand(demoDir) + tasks.toList())
        }.result.get().assertNormalExitValue()
    }
}
