import gg.meza.stonecraft.mod

plugins {
    id("gg.meza.stonecraft")
}

val currentLoader = if (project.name.endsWith("-forge")) "forge" else "neoforge"

version = property("mod.version").toString()
group = property("mod.group").toString()

base {
    archivesName.set(property("mod.id") as String)
}

repositories {
    mavenLocal()

    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }

    exclusiveContent {
        forRepository {
            maven {
                url = uri("https://cursemaven.com")
            }
        }
        filter {
            includeGroup("curse.maven")
        }
    }

    maven {
        name = "henkelmax.public"
        url = uri("https://maven.maxhenkel.de/repository/public")
    }

    maven {
        name = "LDTTeam - Mods Maven"
        url = uri("https://ldtteam.jfrog.io/ldtteam/mods-maven/")
    }

    maven {
        name = "Jared's maven"
        url = uri("https://maven.blamejared.com/")
    }

    maven {
        name = "ModMaven"
        url = uri("https://modmaven.dev")
    }

    maven {
        name = "sshcrackRepositoryReleases"
        url = uri("https://maven.sshcrack.me/releases")
    }
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(if (currentLoader == "forge") 17 else 21))

modSettings {
    clientOptions {
        fov = 90
        guiScale = 3
        narrator = false
        darkBackground = true
        musicVolume = 0.0
    }
    variableReplacements.put("authors", property("mod.authors").toString())
    variableReplacements.put("minecraft_version", property("minecraft_version").toString())
    variableReplacements.put("minecraft_version_range", property("minecraft_version_range").toString())
    variableReplacements.put("loader_version_range", property("loader_version_range").toString())
    variableReplacements.put("neo_version_range", property("neo_version_range").toString())
    variableReplacements.put("neoforge_version_range", property("neoforge_version_range").toString())
    variableReplacements.put("forge_version_range", property("forge_version_range").toString())
    variableReplacements.put("voicechat_version", "${property("minecraft_version")}-${property("voicechat_version_suffix")}")
    variableReplacements.put("license", property("mod.license").toString())
    variableReplacements.put(
        "minecolonies_version",property("minecolonies_version").toString()!!
    )
    variableReplacements.put("gemini_live_lib_version", property("gemini_live_lib_version").toString())
}

dependencies {
    var voicechat_version = "${property("minecraft_version")}-${property("voicechat_version_suffix")}"

    modImplementation("de.maxhenkel.voicechat:voicechat-api:${property("voicechat_api_version")}")
    localRuntime("maven.modrinth:simple-voice-chat:${currentLoader}-${voicechat_version}")
    modImplementation("com.ldtteam:minecolonies:${property("minecolonies_version")}")
    modImplementation("me.sshcrack:gemini_live_lib:${property("gemini_live_lib_version")}-${property("minecraft_version")}-${currentLoader}")
}
