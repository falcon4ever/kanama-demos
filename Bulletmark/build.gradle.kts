import java.util.concurrent.TimeUnit

plugins {
    kotlin("jvm") version "2.4.21"
    id("com.google.devtools.ksp") version "2.3.12"
}

kotlin {
    jvmToolchain(25)
    sourceSets.named("main") {
        kotlin.srcDir("kotlin-src")
        kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/main/kotlin"))
    }
}

apply(from = "../gradle/kanama-demo.gradle.kts")

// Bulletmark's runner tasks, after the godot-kotlin-jvm harness's runBulletmark and watchBulletmark (see README.md).
// The measuring is the runner's (runner/BulletmarkRunner.gd); these tasks only start Godot with its options, stop it
// after a timeout, and fail on an engine error printed before the result.

// Godot reports failures as error lines on stdout and keeps running, so a task has to read them. Only lines printed
// before the result marker count: Godot's own shutdown prints RID and ObjectDB leaks of its servers.
val failurePatterns = listOf(Regex("^ERROR: "), Regex("^SCRIPT ERROR: "), Regex("^USER ERROR: "))
val resultMarker = "BULLETMARK_RESULT:"

// The runner's options, each passed as a Gradle property of the same name: -Pframes=600 for --frames=600, and
// -Praw-samples with no value for the --raw-samples flag.
val runnerOptions = listOf(
    "label", "frames", "warmup-frames", "seed", "bullets-per-frame", "lifetime-frames", "max-live", "render", "hits",
    "reactions", "bookkeeping", "baseline", "results-dir", "raw-samples", "screenshots", "lang",
)

fun Project.runnerArguments(): List<String> = runnerOptions.mapNotNull { name ->
    findProperty(name)?.toString()?.let { value -> if (value.isEmpty()) "--$name" else "--$name=$value" }
}

// The same Godot the other demo tasks use (gradle/kanama-demo.gradle.kts), or for C# the .NET build of the editor.
fun godotExecutable(dotnet: Boolean): String {
    if (dotnet) {
        return providers.gradleProperty("godotMono").orNull
            ?: System.getenv("GODOT_EDITOR_MONO")
            ?: "/Applications/Godot_mono.app/Contents/MacOS/Godot"
    }
    return providers.gradleProperty("kanama.godot.executable").orNull
        ?: providers.gradleProperty("godotBin").orNull
        ?: System.getenv("KANAMA_GODOT")
        ?: file("/Applications/Godot.app/Contents/MacOS/Godot").takeIf { it.exists() }?.absolutePath
        ?: "godot"
}

// The dotnet command that builds the C# scripts: on the PATH, or where the macOS installer puts it.
fun dotnetExecutable(): File =
    (System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, "dotnet") } +
        File("/usr/local/share/dotnet/dotnet") + File(System.getProperty("user.home"), ".dotnet/dotnet"))
        .firstOrNull(File::isFile)
        ?: error("dotnet was not found: install a .NET SDK to build the C# scripts.")

fun gitCommit(directory: File): String {
    val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD").directory(directory).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText().trim()
    return if (process.waitFor() == 0 && output.isNotEmpty()) output else "unknown"
}

/** Fails when kotlin-src changed after the scripts jar was built, so a run cannot measure the previous build. */
fun checkScriptsAreFresh(projectDirectory: File) {
    val jar = projectDirectory.resolve("addons/kanama/kanama-scripts.jar")
    check(jar.isFile) { "No scripts jar in addons/kanama: run buildScripts and importGodot first." }
    val newestSource = projectDirectory.resolve("kotlin-src").walk().filter { it.isFile }.maxOfOrNull(File::lastModified) ?: 0L
    check(newestSource <= jar.lastModified()) { "kotlin-src changed since the scripts were built: run buildScripts first." }
}

/**
 * Runs the project with the runner [arguments], echoing its output to the console and [logFile], and stops Godot
 * after [timeoutMinutes]. Fails on a non-zero exit, a timeout, or any engine error printed before the result.
 *
 * A measured run uses [fixedFps]: every frame then advances the game by exactly 1/60 s however long it takes to
 * compute, so every run simulates the same game whatever the machine. [headless] also keeps the rendering out of the
 * frame time. A run to watch drops both, to play in a window in real time.
 */
fun runGodot(
    projectDirectory: File,
    arguments: List<String>,
    logFile: File,
    headless: Boolean,
    fixedFps: Boolean,
    dotnet: Boolean,
    timeoutMinutes: Long,
) {
    val command = listOf(godotExecutable(dotnet)) +
        (if (headless) listOf("--headless") else emptyList()) +
        (if (fixedFps) listOf("--fixed-fps", "60") else emptyList()) +
        listOf("--path", projectDirectory.absolutePath, "--") +
        arguments
    println(command.joinToString(" "))

    val process = ProcessBuilder(command)
        .directory(projectDirectory)
        .redirectErrorStream(true)
        .apply {
            // The .NET editor finds its runtime through the dotnet command, so the run does not depend on the caller's PATH.
            if (dotnet) environment()["PATH"] = dotnetExecutable().parent + File.pathSeparator + System.getenv("PATH").orEmpty()
        }
        .start()
    val offending = mutableListOf<String>()
    var running = true
    logFile.parentFile.mkdirs()
    val reader = Thread {
        logFile.bufferedWriter().use { log ->
            process.inputStream.bufferedReader().forEachLine { line ->
                println(line)
                log.write(line)
                log.newLine()
                log.flush()
                if (running && failurePatterns.any { it.containsMatchIn(line) }) synchronized(offending) { offending.add(line) }
                if (line.startsWith(resultMarker)) running = false
            }
        }
    }.apply { start() }
    if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
        process.descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly()
        process.waitFor(1, TimeUnit.MINUTES)
        reader.join(TimeUnit.SECONDS.toMillis(10))
        error("Godot did not finish within $timeoutMinutes minutes and was stopped; raise -Ptimeout-minutes for a longer run.")
    }
    reader.join()
    check(offending.isEmpty()) {
        "Godot reported ${offending.size} error line(s) during the run:\n" + offending.joinToString("\n")
    }
    check(process.exitValue() == 0) { "The bulletmark runner exited with code ${process.exitValue()}, see the summary above." }
}

// The language a task runs: -Plang=kt (the default), cs or gd. A C# run needs the .NET editor and its scripts built.
val dotnet = findProperty("lang") == "cs"
val timeoutMinutes = (findProperty("timeout-minutes") as String?)?.toLong() ?: 15L

val buildCSharp = tasks.register<Exec>("buildCSharp") {
    group = "verification"
    description = "Builds the C# scripts the .NET editor runs with -Plang=cs."
    workingDir = projectDir
    doFirst { commandLine(dotnetExecutable().absolutePath, "build", "Bulletmark.csproj", "-nologo", "-v", "quiet") }
}

tasks.register("runBulletmark") {
    group = "verification"
    description = "Measures bulletmark at a fixed 60 fps of game time, in a window; -Pheadless measures without one. " +
        "Requires buildScripts and importGodot first. Runner options are Gradle properties, for example -Plabel=base."
    if (dotnet) dependsOn(buildCSharp)
    val workingDirectory = projectDir
    val arguments = project.runnerArguments()
    val headless = findProperty("headless") != null
    val logFile = layout.buildDirectory.file("bulletmark.log").get().asFile
    val lang = findProperty("lang") as String? ?: "kt"

    doLast {
        if (lang == "kt") checkScriptsAreFresh(workingDirectory)
        runGodot(
            workingDirectory, listOf("--commit=${gitCommit(workingDirectory)}") + arguments, logFile,
            headless = headless, fixedFps = true, dotnet = dotnet, timeoutMinutes = timeoutMinutes,
        )
    }
}

tasks.register("watchBulletmark") {
    group = "verification"
    description = "Opens bulletmark in a window to watch, in real time, for a minute; not a measurement, since each " +
        "frame advances the game by however long it took. Takes the same options as runBulletmark."
    if (dotnet) dependsOn(buildCSharp)
    val workingDirectory = projectDir
    // Later options win in the runner, so these defaults give way to any the command passes.
    val arguments = listOf("--label=watch", "--warmup-frames=0", "--frames=3600") + project.runnerArguments()
    val logFile = layout.buildDirectory.file("watch.log").get().asFile
    val lang = findProperty("lang") as String? ?: "kt"

    doLast {
        if (lang == "kt") checkScriptsAreFresh(workingDirectory)
        runGodot(
            workingDirectory, listOf("--commit=${gitCommit(workingDirectory)}") + arguments, logFile,
            headless = false, fixedFps = false, dotnet = dotnet, timeoutMinutes = timeoutMinutes,
        )
    }
}
