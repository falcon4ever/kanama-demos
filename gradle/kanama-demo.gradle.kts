val kanamaKitDir = providers.gradleProperty("kanamaKitDir")
    .orElse(providers.environmentVariable("KANAMA_KIT_DIR"))
    .map { file(it) }
val kanamaRoot = providers.gradleProperty("kanamaRoot")
    .orElse(providers.environmentVariable("KANAMA_ROOT"))
    .map { file(it) }
    .getOrElse(file("../../kanama"))
val kanamaVersion = providers.gradleProperty("kanamaVersion").getOrElse("0.2.1")
val defaultGodotBin = file("/Applications/Godot.app/Contents/MacOS/Godot")
    .takeIf { it.exists() }
    ?.absolutePath
    ?: "godot"
val godotBin = providers.gradleProperty("kanama.godot.executable")
    .orElse(providers.gradleProperty("godotBin"))
    .orElse(providers.environmentVariable("KANAMA_GODOT"))
    .getOrElse(defaultGodotBin)
val kanamaGradle = kanamaRoot
    .resolve(if (System.getProperty("os.name").lowercase().contains("windows")) "gradlew.bat" else "gradlew")
    .absolutePath
val buildScriptsDependsOnCompileKotlin =
    (extra.properties["kanamaBuildScriptsDependsOnCompileKotlin"] as? Boolean) == true

repositories {
    val kitDir = kanamaKitDir.orNull
    if (kitDir != null) {
        maven {
            url = uri(kitDir.resolve("addons/kanama/maven"))
        }
    } else {
        mavenLocal()
    }
    mavenCentral()
}

dependencies {
    "implementation"("net.multigesture.kanama:kanama:$kanamaVersion")
    "implementation"("net.multigesture.kanama:annotations:$kanamaVersion")
    "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    "ksp"("net.multigesture.kanama:processor:$kanamaVersion")
}

tasks.register<Exec>("runGodot") {
    group = "kanama"
    description = "Run this project in Godot."
    commandLine(godotBin, "--path", projectDir.absolutePath)
}

tasks.register<Exec>("openGodotEditor") {
    group = "kanama"
    description = "Open this project in the Godot editor."
    commandLine(godotBin, "--path", projectDir.absolutePath, "--editor")
}

tasks.register<Exec>("importGodot") {
    group = "kanama"
    description = "Import this project's assets in Godot."
    commandLine(godotBin, "--headless", "--import", "--path", projectDir.absolutePath)
}

if (kanamaKitDir.isPresent) {
    val installKanamaKitAddon by tasks.registering {
        group = "kanama"
        description = "Install Kanama addon files from a packaged desktop kit."
        doLast {
            val kitDir = kanamaKitDir.get()
            copy {
                from(kitDir.resolve("addons/kanama")) {
                    exclude("kanama-scripts.jar")
                }
                into(layout.projectDirectory.dir("addons/kanama"))
            }
            copy {
                from(kitDir.resolve("addons/kanama_tools"))
                into(layout.projectDirectory.dir("addons/kanama_tools"))
            }
            val extensionList = projectDir.resolve(".godot/extension_list.cfg")
            val extensionPath = "res://addons/kanama/kanama.gdextension"
            extensionList.parentFile.mkdirs()
            val existing = if (extensionList.isFile) extensionList.readLines() else emptyList()
            if (extensionPath !in existing) {
                extensionList.writeText((existing + extensionPath).joinToString(System.lineSeparator()) + System.lineSeparator())
            }
        }
    }

    val installScriptsJar by tasks.registering(Copy::class) {
        group = "kanama"
        description = "Install compiled Kotlin demo scripts into addons/kanama."
        dependsOn(tasks.named("jar"))
        from(tasks.named<Jar>("jar").flatMap { it.archiveFile }) {
            rename { "kanama-scripts.jar" }
        }
        into(layout.projectDirectory.dir("addons/kanama"))
    }

    tasks.register("buildScripts") {
        group = "kanama"
        description = "Compile kotlin-src and install Kanama addon files from the packaged kit."
        dependsOn(installKanamaKitAddon, installScriptsJar)
    }
} else {
    tasks.register<Exec>("buildScripts") {
        group = "kanama"
        description = "Compile kotlin-src and install Kanama addon files from a source checkout."
        if (buildScriptsDependsOnCompileKotlin) {
            dependsOn("compileKotlin")
        }
        workingDir = kanamaRoot
        commandLine(
            kanamaGradle,
            ":project-scripts:jar",
            "-PkanamaProjectScriptsDir=${file("kotlin-src").absolutePath}",
            "installAddonJar",
            "-PkanamaProjectDir=${projectDir.absolutePath}",
            "-PkanamaProjectScriptsDir=${file("kotlin-src").absolutePath}",
        )
    }
}

tasks.register("buildAndRunGodot") {
    group = "kanama"
    description = "buildScripts, then runGodot."
    dependsOn("buildScripts", "runGodot")
    tasks.named("runGodot").get().mustRunAfter("buildScripts")
}
