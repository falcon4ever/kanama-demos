import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    kotlin("jvm") version "2.3.0"
    id("com.google.devtools.ksp") version "2.3.0"
}

abstract class ExecSupport @Inject constructor(
    val execOperations: ExecOperations,
)

repositories {
    mavenLocal()
    mavenCentral()
}

val kanamaRoot = providers.gradleProperty("kanamaRoot")
    .map { file(it) }
    .getOrElse(file("../../kanama"))
val kanamaVersion = "0.2.0"
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
val execSupport = objects.newInstance(ExecSupport::class.java)

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
    commandLine(godotBin, "--path", projectDir.absolutePath, "--windowed")
}

tasks.register<Exec>("smokeGodot") {
    group = "kanama"
    description = "Run this project in Godot for a bounded headless smoke check."
    commandLine(godotBin, "--headless", "--path", projectDir.absolutePath, "--quit-after", "120")
}

tasks.register("smokeGodotChecked") {
    group = "kanama"
    description = "Run a bounded headless smoke check and audit the TPS startup log."
    dependsOn("buildScripts", "importGodot")

    doLast {
        val output = ByteArrayOutputStream()
        val result = execSupport.execOperations.exec {
            commandLine(godotBin, "--headless", "--path", projectDir.absolutePath, "--quit-after", "120")
            environment("KANAMA_TPS_SMOKE_KILL_ROBOT", "1")
            isIgnoreExitValue = true
            standardOutput = output
            errorOutput = output
        }
        val log = output.toString(Charsets.UTF_8)
        val logFile = layout.buildDirectory.file("reports/tps-smoke.log").get().asFile
        logFile.parentFile.mkdirs()
        logFile.writeText(log)

        if (result.exitValue != 0) {
            throw GradleException("Godot TPS smoke exited ${result.exitValue}; see ${logFile.absolutePath}")
        }

        val required = listOf(
            "TPS load complete: res://level/level.tscn",
            "TPS Level ready: complete",
            "TPS Main added scene root: Level",
            "TPS smoke robot death complete",
        )
        val forbidden = listOf(
            "Unable to get the RPC configuration",
            "Parameter \"body\" is null.",
            "Parameter \"uniform_set\" is null.",
            "script method failed",
            "Parse Error:",
            "SIGBUS",
            "SIGABRT",
            "handle_crash:",
            "Program crashed",
            "Exception in thread",
            "NoClassDefFoundError",
            "ClassNotFoundException",
            "UnsatisfiedLinkError",
        )

        val missing = required.filterNot { it in log }
        val present = forbidden.filter { it in log }
        if (missing.isNotEmpty() || present.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("Godot TPS smoke log audit failed; see ${logFile.absolutePath}")
                    if (missing.isNotEmpty()) append("\nmissing: ${missing.joinToString()}")
                    if (present.isNotEmpty()) append("\nforbidden: ${present.joinToString()}")
                },
            )
        }
    }
}

tasks.register("smokeGodotRenderChecked") {
    group = "kanama"
    description = "Run a bounded windowed Metal smoke check and audit the TPS render-path log."
    dependsOn("buildScripts", "importGodot")

    doLast {
        val output = ByteArrayOutputStream()
        val reloadMarker = layout.buildDirectory.file("reports/tps-reload-smoke.marker").get().asFile
        reloadMarker.delete()
        val result = execSupport.execOperations.exec {
            commandLine(
                godotBin,
                "--path",
                projectDir.absolutePath,
                "--windowed",
                "--resolution",
                "960x540",
                "--position",
                "80,80",
                "--audio-driver",
                "Dummy",
                "--disable-vsync",
                "--max-fps",
                "60",
                "--quit-after",
                "3600",
            )
            environment("KANAMA_TPS_SMOKE_KILL_ROBOT", "1")
            environment("KANAMA_TPS_SMOKE_KILL_ROBOTS", "2")
            environment("KANAMA_TPS_SMOKE_KILL_ROBOT_DELAY", "3.0")
            environment("KANAMA_TPS_SMOKE_AUTOSTART", "1")
            environment("KANAMA_TPS_SMOKE_QUIT_AFTER_PARTS_DESTROYED", "1")
            environment("KANAMA_TPS_SMOKE_QUIT_AFTER_PARTS_DESTROYED_DELAY", "20.0")
            isIgnoreExitValue = true
            standardOutput = output
            errorOutput = output
        }
        val log = output.toString(Charsets.UTF_8)
        val logFile = layout.buildDirectory.file("reports/tps-render-smoke.log").get().asFile
        logFile.parentFile.mkdirs()
        logFile.writeText(log)

        if (result.exitValue != 0) {
            throw GradleException("Godot TPS render smoke exited ${result.exitValue}; see ${logFile.absolutePath}")
        }

        val required = listOf(
            "TPS load complete: res://level/level.tscn",
            "TPS Level ready: complete",
            "TPS smoke robot death complete 1/2",
            "TPS smoke robot death complete 2/2",
            "TPS smoke part destroyed",
        )
        val forbidden = listOf(
            "Unable to get the RPC configuration",
            "Parameter \"body\" is null.",
            "Parameter \"uniform_set\" is null.",
            "script method failed",
            "Parse Error:",
            "SIGBUS",
            "SIGABRT",
            "signal 11",
            "handle_crash:",
            "Program crashed",
            "Exception in thread",
            "NoClassDefFoundError",
            "ClassNotFoundException",
            "UnsatisfiedLinkError",
        )

        val missing = required.filterNot { it in log }
        val present = forbidden.filter { it in log }
        if (missing.isNotEmpty() || present.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("Godot TPS render smoke log audit failed; see ${logFile.absolutePath}")
                    if (missing.isNotEmpty()) append("\nmissing: ${missing.joinToString()}")
                    if (present.isNotEmpty()) append("\nforbidden: ${present.joinToString()}")
                },
            )
        }
    }
}

tasks.register("smokeGodotBulletChecked") {
    group = "kanama"
    description = "Run a bounded windowed Metal smoke check through the real TPS bullet collision path."
    dependsOn("buildScripts", "importGodot")

    doLast {
        val output = ByteArrayOutputStream()
        val result = execSupport.execOperations.exec {
            commandLine(
                godotBin,
                "--path",
                projectDir.absolutePath,
                "--windowed",
                "--resolution",
                "960x540",
                "--position",
                "140,140",
                "--audio-driver",
                "Dummy",
                "--disable-vsync",
                "--max-fps",
                "60",
                "--quit-after",
                "3600",
            )
            environment("KANAMA_TPS_SMOKE_KILL_ROBOT", "1")
            environment("KANAMA_TPS_SMOKE_KILL_ROBOTS", "1")
            environment("KANAMA_TPS_SMOKE_AUTOSTART", "1")
            environment("KANAMA_TPS_SMOKE_REAL_BULLETS", "1")
            environment("KANAMA_TPS_SMOKE_QUIT_AFTER_PARTS_DESTROYED", "1")
            environment("KANAMA_TPS_SMOKE_QUIT_AFTER_PARTS_DESTROYED_DELAY", "8.0")
            isIgnoreExitValue = true
            standardOutput = output
            errorOutput = output
        }
        val log = output.toString(Charsets.UTF_8)
        val logFile = layout.buildDirectory.file("reports/tps-bullet-smoke.log").get().asFile
        logFile.parentFile.mkdirs()
        logFile.writeText(log)

        if (result.exitValue != 0) {
            throw GradleException("Godot TPS bullet smoke exited ${result.exitValue}; see ${logFile.absolutePath}")
        }

        val required = listOf(
            "TPS load complete: res://level/level.tscn",
            "TPS Level ready: complete",
            "TPS smoke robot death complete 1/1",
            "TPS smoke part destroyed",
        )
        val forbidden = listOf(
            "Unable to get the RPC configuration",
            "Parameter \"body\" is null.",
            "Parameter \"uniform_set\" is null.",
            "script method failed",
            "Parse Error:",
            "SIGBUS",
            "SIGABRT",
            "signal 11",
            "handle_crash:",
            "Program crashed",
            "Exception in thread",
            "NoClassDefFoundError",
            "ClassNotFoundException",
            "UnsatisfiedLinkError",
        )

        val missing = required.filterNot { it in log }
        val present = forbidden.filter { it in log }
        if (missing.isNotEmpty() || present.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("Godot TPS bullet smoke log audit failed; see ${logFile.absolutePath}")
                    if (missing.isNotEmpty()) append("\nmissing: ${missing.joinToString()}")
                    if (present.isNotEmpty()) append("\nforbidden: ${present.joinToString()}")
                },
            )
        }
    }
}

tasks.register("smokeGodotReloadChecked") {
    group = "kanama"
    description = "Run a bounded windowed smoke check for TPS death, menu return, and level reload."
    dependsOn("buildScripts", "importGodot")

    doLast {
        val output = ByteArrayOutputStream()
        val reloadMarker = layout.buildDirectory.file("reports/tps-reload-smoke.marker").get().asFile
        reloadMarker.delete()
        val result = execSupport.execOperations.exec {
            commandLine(
                godotBin,
                "--path",
                projectDir.absolutePath,
                "--windowed",
                "--resolution",
                "960x540",
                "--position",
                "110,110",
                "--audio-driver",
                "Dummy",
                "--disable-vsync",
                "--max-fps",
                "60",
                "--quit-after",
                "3600",
            )
            environment("KANAMA_TPS_SMOKE_KILL_ROBOT", "1")
            environment("KANAMA_TPS_SMOKE_KILL_ROBOTS", "1")
            environment("KANAMA_TPS_SMOKE_AUTOSTART", "1")
            environment("KANAMA_TPS_SMOKE_FAST_PARTS", "1")
            environment("KANAMA_TPS_SMOKE_RETURN_TO_MENU_AFTER_FIRST_KILL", "1")
            environment("KANAMA_TPS_SMOKE_QUIT_AFTER_SECOND_LEVEL_READY", "1")
            environment("KANAMA_TPS_SMOKE_RELOAD_MARKER", reloadMarker.absolutePath)
            isIgnoreExitValue = true
            standardOutput = output
            errorOutput = output
        }
        val log = output.toString(Charsets.UTF_8)
        val logFile = layout.buildDirectory.file("reports/tps-reload-smoke.log").get().asFile
        logFile.parentFile.mkdirs()
        logFile.writeText(log)

        if (result.exitValue != 0) {
            throw GradleException("Godot TPS reload smoke exited ${result.exitValue}; see ${logFile.absolutePath}")
        }

        val required = listOf(
            "TPS smoke robot death complete 1/1",
            "TPS smoke returning to menu after first kill",
            "TPS smoke second level ready; quitting",
        )
        val forbidden = listOf(
            "Unable to get the RPC configuration",
            "Parameter \"body\" is null.",
            "Parameter \"uniform_set\" is null.",
            "script method failed",
            "Parse Error:",
            "SIGBUS",
            "SIGABRT",
            "signal 11",
            "handle_crash:",
            "Program crashed",
            "Exception in thread",
            "NoClassDefFoundError",
            "ClassNotFoundException",
            "UnsatisfiedLinkError",
        )

        val missing = required.filterNot { it in log }
        val present = forbidden.filter { it in log }
        if (missing.isNotEmpty() || present.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("Godot TPS reload smoke log audit failed; see ${logFile.absolutePath}")
                    if (missing.isNotEmpty()) append("\nmissing: ${missing.joinToString()}")
                    if (present.isNotEmpty()) append("\nforbidden: ${present.joinToString()}")
                },
            )
        }
    }
}

tasks.register<Exec>("runtimeNodeLookupAudit") {
    group = "verification"
    description = "Audit TPS Kotlin scripts for required node lookups in runtime callbacks."
    commandLine("python3", kanamaRoot.resolve("scripts/audit_runtime_node_lookups.py").absolutePath, "kotlin-src")
}

tasks.register<Exec>("replicatedScriptPropertiesAudit") {
    group = "verification"
    description = "Audit TPS scene replication paths against Kotlin script properties."
    commandLine("python3", kanamaRoot.resolve("scripts/audit_replicated_script_properties.py").absolutePath, projectDir.absolutePath)
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
    description = "buildScripts, then runGodot."
    dependsOn("buildScripts", "runGodot")
    tasks.named("runGodot").get().mustRunAfter("buildScripts")
}

tasks.register("buildAndSmokeGodot") {
    group = "kanama"
    description = "buildScripts, import assets, then run the bounded headless smoke check."
    dependsOn("runtimeNodeLookupAudit", "replicatedScriptPropertiesAudit", "smokeGodotChecked")
    tasks.named("smokeGodotChecked").get().mustRunAfter("runtimeNodeLookupAudit", "replicatedScriptPropertiesAudit")
}

tasks.register("buildAndRenderSmokeGodot") {
    group = "kanama"
    description = "buildScripts, import assets, then run the bounded windowed Metal smoke check."
    dependsOn("runtimeNodeLookupAudit", "replicatedScriptPropertiesAudit", "smokeGodotRenderChecked")
    tasks.named("smokeGodotRenderChecked").get().mustRunAfter("runtimeNodeLookupAudit", "replicatedScriptPropertiesAudit")
}

tasks.register("buildAndBulletSmokeGodot") {
    group = "kanama"
    description = "buildScripts, import assets, then run the bounded TPS real-bullet smoke check."
    dependsOn("runtimeNodeLookupAudit", "replicatedScriptPropertiesAudit", "smokeGodotBulletChecked")
    tasks.named("smokeGodotBulletChecked").get().mustRunAfter("runtimeNodeLookupAudit", "replicatedScriptPropertiesAudit")
}

tasks.register("buildAndReloadSmokeGodot") {
    group = "kanama"
    description = "buildScripts, import assets, then run the bounded TPS level reload smoke check."
    dependsOn("runtimeNodeLookupAudit", "replicatedScriptPropertiesAudit", "smokeGodotReloadChecked")
    tasks.named("smokeGodotReloadChecked").get().mustRunAfter("runtimeNodeLookupAudit", "replicatedScriptPropertiesAudit")
}
