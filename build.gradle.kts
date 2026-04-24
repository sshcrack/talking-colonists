import gg.meza.stonecraft.mod

plugins {
    id("gg.meza.stonecraft")
}

val currentLoader = if (mod.isForge) "forge" else "neoforge"
var voicechat_version = "${property("minecraft_version")}-${property("voicechat_version_suffix")}"

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

modSettings {
    clientOptions {
        fov = 90
        guiScale = 3
        narrator = false
        darkBackground = true
        musicVolume = 0.0
    }
    variableReplacements.put("authors", property("mod.authors").toString())
    variableReplacements.put("voicechat_version", voicechat_version)
    variableReplacements.put("minecolonies_version", property("minecolonies_version")!!)
    variableReplacements.put("license", property("mod.license").toString())
    variableReplacements.put(
        "minecolonies_version", property("minecolonies_version").toString()!!
    )
    variableReplacements.put("gemini_live_lib_version", property("gemini_live_lib_version").toString())
}

publishMods {
    curseforge {
        clientRequired = true // Set as needed
        serverRequired = true // Set as needed
    }
}

dependencies {
    modImplementation("de.maxhenkel.voicechat:voicechat-api:${property("voicechat_api_version")}")
    modRuntimeOnly("maven.modrinth:simple-voice-chat:${currentLoader}-${voicechat_version}")
    modImplementation("me.sshcrack:gemini_live_lib:${property("gemini_live_lib_version")}-${property("minecraft_version")}-${currentLoader}")

    var domum_variant = if (mod.isForge) {
        "domum_ornamentum"
    } else {
        "domum-ornamentum"
    }

    modImplementation("com.ldtteam:minecolonies:${property("minecolonies_version")}")
    modRuntimeOnly("com.ldtteam:${domum_variant}:${property("domum_version")}")
    modRuntimeOnly("com.ldtteam:structurize:${property("structurize_version")}")
    modRuntimeOnly("com.ldtteam:blockui:${property("blockui_version")}")
}
