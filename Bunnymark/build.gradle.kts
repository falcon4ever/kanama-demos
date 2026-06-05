plugins {
    kotlin("jvm") version "2.3.21"
    id("com.google.devtools.ksp") version "2.3.9"
}

kotlin {
    jvmToolchain(25)
    sourceSets.named("main") {
        kotlin.srcDir("kotlin-src")
        kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/main/kotlin"))
    }
}

extra["kanamaBuildScriptsDependsOnCompileKotlin"] = true

apply(from = "../gradle/kanama-demo.gradle.kts")
