plugins {
    kotlin("jvm") version "2.3.0"
    id("com.google.devtools.ksp") version "2.3.0"
}

repositories {
    mavenLocal()
    mavenCentral()
}

val kanamaRoot = providers.gradleProperty("kanamaRoot")
    .map { file(it) }
    .getOrElse(file("../../kanama"))
val kanamaVersion = "0.1.0"
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


kotlin {
    jvmToolchain(25)
    sourceSets.named("main") {
        kotlin.srcDir("kotlin-src")
        kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/main/kotlin"))
    }
}

dependencies {
    implementation("net.multigesture.kanama:kanama:$kanamaVersion")
    implementation("net.multigesture.kanama:annotations:$kanamaVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    ksp("net.multigesture.kanama:processor:$kanamaVersion")
}

tasks.register<Exec>("buildScripts") {
    group = "kanama"
    description = "Compile kotlin-src and install kanama addon into this project."
    dependsOn("compileKotlin")
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

tasks.register("buildAndRunGodot") {
    group = "kanama"
    description = "buildScripts, import assets, then runGodot."
    dependsOn("buildScripts", "importGodot", "runGodot")
    tasks.named("importGodot").get().mustRunAfter("buildScripts")
    tasks.named("runGodot").get().mustRunAfter("importGodot")
}
