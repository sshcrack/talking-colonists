pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.parchmentmc.org") { name = "ParchmentMC" }
    }
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.2"
}

stonecutter {
    create(rootProject) {
        fun match(version: String, vararg loaders: String) =
            loaders.forEach { version("$version-$it", version).buildscript = getBuildscript(it, version) }

        match("1.21.1", "neoforge")
        match("1.20.1", "forge")

        vcsVersion = "1.21.1-neoforge"
    }
}

private fun getBuildscript(loader: String, version: String): String {
    return "build.$loader.gradle.kts"
}

val libraryDir = file("../gemini-live-library")
if (libraryDir.exists()) {
    println("Including Gemini Live Library from $libraryDir")
    includeBuild(libraryDir) {
        dependencySubstitution {
            substitute(module("me.sshcrack:gemini_live_lib:2.3.0-1.20.1-forge"))
                .using(project(":1.20.1-forge"))
            substitute(module("me.sshcrack:gemini_live_lib:2.3.0-1.21.1-neoforge"))
                .using(project(":1.21.1-neoforge"))
        }
    }
} else {
    println("Warning: Gemini Live Library not found, skipping includeBuild")
}
