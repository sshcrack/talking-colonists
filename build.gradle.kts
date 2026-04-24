import gg.meza.stonecraft.mod

plugins {
    id("gg.meza.stonecraft")
}

val currentLoader = if (project.name.endsWith("-forge")) "forge" else "neoforge"

version = property("mod_version").toString()
group = property("mod_group_id").toString()

base {
    archivesName.set(property("mod_id") as String)
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
    variableReplacements.put("mod_id", property("mod_id").toString())
    variableReplacements.put("mod_name", property("mod_name").toString())
    variableReplacements.put("mod_version", property("mod_version").toString())
    variableReplacements.put("mod_authors", property("mod_authors").toString())
    variableReplacements.put("mod_description", property("mod_description").toString())
    variableReplacements.put("minecraft_version", property("minecraft_version").toString())
    variableReplacements.put("minecraft_version_range", property("minecraft_version_range").toString())
    variableReplacements.put("loader_version_range", property("loader_version_range").toString())
    variableReplacements.put("neo_version_range", property("neo_version_range").toString())
    variableReplacements.put("neoforge_version_range", property("neoforge_version_range").toString())
    variableReplacements.put("forge_version_range", property("forge_version_range").toString())
    variableReplacements.put("voicechat_version", "${property("minecraft_version")}-${property("voicechat_version_suffix")}")
    variableReplacements.put("mod_license", property("mod_license").toString())
    variableReplacements.put(
        "minecolonies_version",
        if (currentLoader == "forge") property("minecolonies_version_forge").toString() else property("minecolonies_version_neoforge").toString()
    )
    variableReplacements.put("gemini_live_lib_version", property("gemini_live_lib_version").toString())
}

dependencies {
    add("implementation", "de.maxhenkel.voicechat:voicechat-api:${property("voicechat_api_version")}")
    add(
        "implementation",
        "com.ldtteam:minecolonies:${
            if (currentLoader == "forge") property("minecolonies_version_forge") else property("minecolonies_version_neoforge")
        }"
    )
    add("implementation", "me.sshcrack:gemini_live_lib:${property("gemini_live_lib_version")}-${property("minecraft_version")}-${currentLoader}")
}
/*
val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "minecraft_version" to property("minecraft_version"),
        "minecraft_version_range" to property("minecraft_version_range"),
        "loader_version_range" to property("loader_version_range"),
        "mod_id" to property("mod_id"),
        "mod_name" to property("mod_name"),
        "mod_license" to property("mod_license"),
        "mod_version" to property("mod_version"),
        "mod_authors" to property("mod_authors"),
        "mod_description" to property("mod_description")
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}

sourceSets.named("main") {
    resources.srcDir(generateModMetadata)
    resources.srcDir("src/generated/resources")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
 */

