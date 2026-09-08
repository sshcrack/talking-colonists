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

val geminiLiveLibVersion = prop("deps.gemini_live_lib_version")
val geminiLiveLibVersionRange = compatibleMajorVersionRange(geminiLiveLibVersion)

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
            forgeLikeVersionRange = geminiLiveLibVersionRange
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

// Create the public addon source set before configuring ModDev runs so the normal
// Talking Colonists mod can expose both implementation and API outputs as one mod.
configureAddonApi()

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

        val forceCreateAutoQuitWorld = providers.gradleProperty("mc_talking.forceCreateWorld")
            .map(String::toBoolean)
            .orElse(false)
            .get()
        val autoQuitWorld = if (forceCreateAutoQuitWorld) "" else providers.gradleProperty("mc_talking.world").orElse("").get().let { name ->
            if (name.isNotEmpty()) name
            else file("run/saves").listFiles()
                ?.filter { it.isDirectory }
                ?.map { it.name }
                ?.sorted()
                ?.firstOrNull()
                .orEmpty()
        }

        register("clientAutoQuit") {
            client()
            gameDirectory = file("run/")
            ideName = "Forge Client AutoQuit (${sc.current.version})"
            programArgument("--username=Dev")
            if (autoQuitWorld.isNotEmpty()) {
                programArgument("--quickPlaySingleplayer=$autoQuitWorld")
            }
            jvmArgument("-Dmc_talking.autoQuit=true")
            jvmArgument("-Djava.io.tmpdir=${file("run").absolutePath}")
        }
    }


    mods {
        register(prop("mod.id")) {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["addonApi"])
        }
    }

    addModdingDependenciesTo(sourceSets["test"])
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

    exclusiveContent {
        forRepository {
            maven {
                name = "sshcrackRepositoryReleases"
                url = uri("https://maven.sshcrack.me/releases")
            }
        }
        filter {
            includeGroup("me.sshcrack")
        }
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
    testImplementation(enforcedPlatform("org.junit:junit-bom:5.14.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

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
    modImplementation("me.sshcrack:gemini_live_lib:$geminiLiveLibVersion-${prop("deps.minecraft")}-forge")

    modImplementation("com.ldtteam:minecolonies:${prop("deps.minecolonies_version")}")
    modRuntimeOnly("com.ldtteam:domum_ornamentum:${prop("deps.domum_version")}:universal")
    modRuntimeOnly("com.ldtteam:structurize:${prop("deps.structurize_version")}")
    modRuntimeOnly("com.ldtteam:blockui:${prop("deps.blockui_version")}")

    modImplementation("dev.isxander:yet-another-config-lib:${prop("deps.yacl_version")}+${prop("deps.minecraft")}-forge")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
    include("**/*Test.class")
    include("**/*Tests.class")
    include("**/*TestCase.class")
    exclude("**/*\$*.class")
    // File-level exclusion prevents JUnit discovery from loading
    // these classes at all on JDK 25 where Forge's signed jar
    // triggers SHA-256 digest errors during class loading.
    exclude("**/AiToolDispatcherTest.class")
    exclude("**/RumorMillServiceTest.class")
    filter {
        includeTestsMatching("*Test")
        includeTestsMatching("*Tests")
        includeTestsMatching("*TestCase")
        excludeTestsMatching("*\$*")
        // Forge 47.2.0's signed jar fails SHA-256 verification after
        // re-obfuscation (e.g. JDK 21/25 CI). These tests load
        // MineColonies/Forge types and are covered on NeoForge.
        excludeTestsMatching("*AiToolDispatcherTest*")
        excludeTestsMatching("*RumorMillServiceTest*")
    }
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
