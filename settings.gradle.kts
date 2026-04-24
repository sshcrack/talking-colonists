pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.parchmentmc.org")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
    }
    plugins {
        id("com.modrinth.minotaur") version "2.+"
        id("gg.meza.stonecraft") version "1.9.+"
        id("dev.kikugie.stonecutter") version "0.8.+"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
    id("gg.meza.stonecraft") version "1.9.+"
    id("dev.kikugie.stonecutter") version "0.8.+"
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true
    shared {
        fun mc(version: String, vararg loaders: String) {
            for (loader in loaders) version("$version-$loader", version)
        }

        mc("1.21.1", "neoforge")
        mc("1.20.1", "forge")
        vcsVersion = "1.21.1-neoforge"
    }
    create(rootProject)
}

rootProject.name = "talking-colonists"
