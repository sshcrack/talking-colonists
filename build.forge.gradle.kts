plugins {
    id("mod-platform")
    id("maven-publish")
    id("net.neoforged.moddev.legacyforge")
}

stonecutter {
    val (version, loader) = current.project.split('-', limit = 2)
    properties.tags(version, loader)

    replacements.string(current.parsed >= "1.21.11") {
        replace("ResourceLocation", "Identifier")
        replace("location()", "identifier()")
    }
}

var voicechat_version = "${property("deps.minecraft")}-${property("deps.voice_chat")}"

platform {
    loader = "forge"
    dependencies {
        required("minecraft") {
            forgeLikeVersionRange = prop("deps.minecraft")
        }
        required("forge") {
            forgeLikeVersionRange.set("[1,)")
        }
        required("minecolonies") {
            curseforge = "minecolonies"
            forgeLikeVersionRange = "[${prop("deps.minecolonies_version")},)"
        }
        required("gemini_live_lib") {
            curseforge = "gemini-live-lib"
            forgeLikeVersionRange = "[${prop("deps.gemini_live_lib_version")},)"
        }
        required("voicechat") {
            curseforge = "simple-voice-chat"
            forgeLikeVersionRange = "[${voicechat_version},)"
        }
        required("yet_another_config_lib_v3") {
            curseforge = "yacl"
            forgeLikeVersionRange = "[${prop("deps.yacl_version")},)"
        }
    }
}

legacyForge {
    version = "${prop("deps.minecraft")}-${prop("deps.forge")}"

    validateAccessTransformers = true

    accessTransformers.from(
        rootProject.file("src/main/resources/aw/${sc.current.version}.cfg")
    )

    runs {
        register("client") {
            client()
            gameDirectory = file("run/")
            ideName = "Forge Client (${sc.current.version})"
            programArgument("--username=Dev")
        }
        register("server") {
            server()
            gameDirectory = file("run/")
            ideName = "Forge Server (${sc.current.version})"
        }

        val autoQuitWorld = providers.gradleProperty("mc_talking.world").orElse("").get().let { name ->
            if (name.isNotEmpty()) name
            else file("run/saves").listFiles()
                ?.filter { it.isDirectory }
                ?.map { it.name }
                ?.sorted()
                ?.firstOrNull()
                ?: run {
                    logger.warn(":${sc.current.version} No world in run/saves/ for auto-quit. Use -Pmc_talking.world=<name> or create a world. Defaulting to 'CI_World'.")
                    "CI_World"
                }
        }

        register("clientAutoQuit") {
            client()
            gameDirectory = file("run/")
            ideName = "Forge Client AutoQuit (${sc.current.version})"
            programArgument("--username=Dev")
            programArgument("--quickPlaySingleplayer=$autoQuitWorld")
            jvmArgument("-Dmc_talking.autoQuit=true")
        }
    }


    mods {
        register(prop("mod.id")) {
            sourceSet(sourceSets["main"])
        }
    }
}

mixin {
    add(sourceSets.main.get(), "${prop("mod.id")}.mixins.refmap.json")
    config("${prop("mod.id")}.mixins.json")
}

repositories {
    mavenCentral()
    strictMaven("https://api.modrinth.com/maven", "maven.modrinth") { name = "Modrinth" }
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

    maven("https://maven.isxander.dev/releases") {
        name = "Xander Maven"
    }

    maven {
        name = "Kotlin for Forge"
        setUrl("https://thedarkcolour.github.io/KotlinForForge/")
    }
}

var loader = sc.current.component1().split("-")[1];
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "me.sshcrack"
            artifactId = prop("mod.id")
            version = "${prop("mod.version")}${prop("mod.channel_tag")}-${prop("deps.minecraft")}-${loader}"

            artifact(tasks.named("jar"))
            tasks.findByName("sourcesJar")?.let { artifact(it) }
        }
    }

    repositories {
        maven {
            name = "sshcrackRepository"
            url = uri("https://maven.sshcrack.me/releases")

            credentials {
                username = (findProperty("sshcrackRepoMavenUser") as String?)
                    ?: System.getenv("sshcrackRepoMavenUser")
                password = (findProperty("sshcrackRepoMavenPassword") as String?)
                    ?: System.getenv("sshcrackRepoMavenPassword")
            }
        }
    }
}

dependencies {
    annotationProcessor("org.spongepowered:mixin:${libs.versions.mixin.get()}:processor")
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.4") as Any)

    jarJar("io.github.llamalad7:mixinextras-forge:0.5.4") {
        version {
            strictly("[0.5.4,)")
        }
    }

    implementation(libs.moulberry.mixinconstraints)
    jarJar(libs.moulberry.mixinconstraints)

    modImplementation("de.maxhenkel.voicechat:voicechat-api:${prop("deps.voicechat_api_version")}")
    modRuntimeOnly("maven.modrinth:simple-voice-chat:forge-${voicechat_version}")
    modImplementation("me.sshcrack:gemini_live_lib:${prop("deps.gemini_live_lib_version")}-${prop("deps.minecraft")}-forge")

    modImplementation("com.ldtteam:minecolonies:${prop("deps.minecolonies_version")}")
    modRuntimeOnly("com.ldtteam:domum_ornamentum:${prop("deps.domum_version")}:universal")
    modRuntimeOnly("com.ldtteam:structurize:${prop("deps.structurize_version")}")
    modRuntimeOnly("com.ldtteam:blockui:${prop("deps.blockui_version")}")

    modImplementation("dev.isxander:yet-another-config-lib:${prop("deps.yacl_version")}+${prop("deps.minecraft")}-forge")
}

sourceSets {
    main {
        resources.srcDir(
            "${rootDir}/versions/datagen/${sc.current.version.split("-")[0]}/src/main/generated"
        )
    }
}

tasks.named("createMinecraftArtifacts") {
    dependsOn(tasks.named("stonecutterGenerate"))
}
