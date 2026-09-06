plugins {
    id("mod-platform")
    id("maven-publish")
    id("net.neoforged.moddev")
}

stonecutter {
    val (version, loader) = current.project.split('-', limit = 2)
    properties.tags(version, loader)

    replacements.string(current.parsed >= "1.21.11") {
        replace("ResourceLocation", "Identifier")
        replace("location()", "identifier()")
    }
}

platform {
    loader = "neoforge"
    dependencies {
        required("minecraft") {
            forgeLikeVersionRange = prop("deps.minecraft")
        }
        required("neoforge") {
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

neoForge {
    version = prop("deps.neoforge")
    accessTransformers.from(rootProject.file("src/main/resources/aw/${stonecutter.current.version}.cfg"))
    validateAccessTransformers = true

    if (hasProperty("deps.parchment")) parchment {
        val (mc, ver) = prop("deps.parchment").split(':')
        mappingsVersion = ver
        minecraftVersion = mc
    }

    runs {
        register("client") {
            client()
            gameDirectory = file("run/")
            ideName = "NeoForge Client (${stonecutter.current.version})"
            programArgument("--username=Dev")
        }
        register("server") {
            server()
            gameDirectory = file("run/")
            ideName = "NeoForge Server (${stonecutter.current.version})"
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
            ideName = "NeoForge Client AutoQuit (${stonecutter.current.version})"
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

    addModdingDependenciesTo(sourceSets["test"])

    sourceSets["main"].resources.srcDir("${rootDir}/versions/datagen/${sc.current.version.split("-")[0]}/src/main/generated")
}

var voicechat_version = "${property("deps.minecraft")}-${property("deps.voice_chat")}"

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

// The supported addon API is a real, separately compiled source set. Its compiler sees the
// loader's external/mapped dependencies, but never main output, so an API source cannot import
// Talking Colonists implementation classes by accident. Main/test consume the compiled API output.
val addonApi = sourceSets.create("addonApi") {
    java.setSrcDirs(listOf(rootProject.file("src/api/java")))
    compileClasspath = configurations.getByName("compileClasspath")
}

sourceSets.named("main") {
    compileClasspath += addonApi.output
    runtimeClasspath += addonApi.output
}
sourceSets.named("test") {
    compileClasspath += addonApi.output
    runtimeClasspath += addonApi.output
}

// The normal mod still contains the API at runtime; the separate artifact below is only the
// supported compile/IDE surface for addons.
tasks.named<org.gradle.jvm.tasks.Jar>("jar") {
    from(addonApi.output)
}
tasks.matching { it.name == "sourcesJar" }.configureEach {
    (this as org.gradle.jvm.tasks.Jar).from(rootProject.file("src/api/java"))
}

val apiJar = tasks.register<org.gradle.jvm.tasks.Jar>("apiJar") {
    archiveBaseName.set("${prop("mod.id")}-api")
    archiveClassifier.set("")
    from(addonApi.output)
    include("me/sshcrack/mc_talking/api/**")
}

val apiSourcesJar = tasks.register<org.gradle.jvm.tasks.Jar>("apiSourcesJar") {
    archiveBaseName.set("${prop("mod.id")}-api")
    archiveClassifier.set("sources")
    from(rootProject.file("src/api/java"))
    include("me/sshcrack/mc_talking/api/**")
}

tasks.named("build") {
    dependsOn(apiJar, apiSourcesJar)
}

val verifyApiJar = tasks.register("verifyApiJar") {
    group = "verification"
    description = "Ensures the addon API artifact contains no Talking Colonists implementation classes"
    dependsOn(apiJar)
    doLast {
        val leaked = zipTree(apiJar.get().archiveFile).matching {
            include("me/sshcrack/mc_talking/**/*.class")
            exclude("me/sshcrack/mc_talking/api/**")
        }.files
        if (leaked.isNotEmpty()) {
            throw GradleException("Implementation classes leaked into API jar: ${leaked.joinToString { it.name }}")
        }
        val misplacedApiSources = rootProject.fileTree("src/main/java/me/sshcrack/mc_talking/api") {
            include("**/*.java")
        }.files
        if (misplacedApiSources.isNotEmpty()) {
            throw GradleException("Public API sources must live under src/api/java, not src/main/java")
        }

        val forbiddenImplementationRef = Regex(
            "(?m)^\\s*import\\s+me\\.sshcrack\\.mc_talking\\.(?!api(?:\\.|;))"
        )
        val leakingSources = rootProject.fileTree("src/api/java") { include("**/*.java") }.files
            .filter { forbiddenImplementationRef.containsMatchIn(it.readText()) }
        if (leakingSources.isNotEmpty()) {
            throw GradleException(
                "Public API sources reference Talking Colonists implementation packages: " +
                    leakingSources.joinToString { it.relativeTo(rootProject.projectDir).path }
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyApiJar)
}

tasks.matching { it.name == "buildAndCollect" }.configureEach {
    this as Copy
    from(apiJar, apiSourcesJar)
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
        create<MavenPublication>("api") {
            groupId = "me.sshcrack"
            artifactId = "${prop("mod.id")}-api"
            version = "${prop("mod.version")}${prop("mod.channel_tag")}-${prop("deps.minecraft")}-${loader}"

            artifact(apiJar)
            artifact(apiSourcesJar)
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

    implementation(libs.moulberry.mixinconstraints)
    jarJar(libs.moulberry.mixinconstraints)

    implementation("de.maxhenkel.voicechat:voicechat-api:${prop("deps.voicechat_api_version")}")
    runtimeOnly("maven.modrinth:simple-voice-chat:neoforge-${voicechat_version}")
    implementation("me.sshcrack:gemini_live_lib:${prop("deps.gemini_live_lib_version")}-${prop("deps.minecraft")}-neoforge")

    implementation("com.ldtteam:minecolonies:${prop("deps.minecolonies_version")}")
    runtimeOnly("com.ldtteam:domum-ornamentum:${prop("deps.domum_version")}")
    runtimeOnly("com.ldtteam:structurize:${prop("deps.structurize_version")}")
    runtimeOnly("com.ldtteam:blockui:${prop("deps.blockui_version")}")

    implementation("dev.isxander:yet-another-config-lib:${prop("deps.yacl_version")}+1.21.1-neoforge")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}

tasks.named("createMinecraftArtifacts") {
    dependsOn(tasks.named("stonecutterGenerate"))
}
