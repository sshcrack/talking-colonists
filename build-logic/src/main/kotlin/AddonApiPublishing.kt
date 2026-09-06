import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * Configures the developer-only Talking Colonists addon API artifact.
 *
 * The normal mod jar still embeds both public API classes and runtime bridge classes. The separate
 * API artifact deliberately contains only supported addon-facing classes and no mod metadata, so
 * it is a compile/source dependency for addon developers rather than a second mod users install.
 */
fun Project.configureAddonApi() {
    val javaExtension = extensions.getByType(JavaPluginExtension::class.java)
    val addonApi = javaExtension.sourceSets.create("addonApi") {
        java.setSrcDirs(listOf(rootProject.file("src/api/java")))
        compileClasspath = configurations.getByName("compileClasspath")
    }

    javaExtension.sourceSets.named("main") {
        compileClasspath += addonApi.output
        runtimeClasspath += addonApi.output
    }
    javaExtension.sourceSets.named("test") {
        compileClasspath += addonApi.output
        runtimeClasspath += addonApi.output
    }

    tasks.named<Jar>("jar") {
        from(addonApi.output)
    }
    tasks.matching { it.name == "sourcesJar" }.configureEach {
        (this as Jar).from(rootProject.file("src/api/java"))
    }

    val apiJar = tasks.register<Jar>("apiJar") {
        archiveBaseName.set("${prop("mod.id")}-api")
        archiveClassifier.set("")
        from(addonApi.output)
        include("me/sshcrack/mc_talking/api/**")
        exclude("me/sshcrack/mc_talking/api/internal/**")
    }

    val apiSourcesJar = tasks.register<Jar>("apiSourcesJar") {
        archiveBaseName.set("${prop("mod.id")}-api")
        archiveClassifier.set("sources")
        from(rootProject.file("src/api/java"))
        include("me/sshcrack/mc_talking/api/**")
        exclude("me/sshcrack/mc_talking/api/internal/**")
    }

    // Compile examples against the actual stripped developer artifact rather than main/addonApi
    // output. This catches accidental dependencies on runtime-only bridge classes.
    val compileApiExamples = tasks.register<JavaCompile>("compileAddonApiExamples") {
        source(rootProject.fileTree("src/apiTest/java") { include("**/*.java") })
        classpath = files(apiJar) + configurations.getByName("compileClasspath")
        destinationDirectory.set(layout.buildDirectory.dir("classes/java/addonApiExamples"))
        options.release.set(javaExtension.targetCompatibility.majorVersion.toInt())
        dependsOn(apiJar)
    }

    val verifyApiJar = tasks.register("verifyApiJar") {
        group = "verification"
        description = "Ensures the developer addon API artifact exposes only supported API classes"
        dependsOn(apiJar, apiSourcesJar, compileApiExamples)
        doLast {
            val archive = zipTree(apiJar.get().archiveFile)
            val leakedImplementation = archive.matching {
                include("me/sshcrack/mc_talking/**/*.class")
                exclude("me/sshcrack/mc_talking/api/**")
            }.files
            if (leakedImplementation.isNotEmpty()) {
                throw GradleException(
                    "Implementation classes leaked into API jar: ${leakedImplementation.joinToString { it.name }}"
                )
            }

            val leakedRuntimeBridge = archive.matching {
                include("me/sshcrack/mc_talking/api/internal/**")
            }.files
            if (leakedRuntimeBridge.isNotEmpty()) {
                throw GradleException(
                    "Runtime-only API bridge classes leaked into developer API jar: " +
                        leakedRuntimeBridge.joinToString { it.name }
                )
            }

            val forbiddenModMetadata = archive.matching {
                include("META-INF/mods.toml")
                include("META-INF/neoforge.mods.toml")
                include("META-INF/MANIFEST.MF")
                include("*.mixins.json")
            }.files.filter { file ->
                // Every jar has a minimal manifest. Only loader/mixin metadata is forbidden; the
                // manifest itself is harmless unless it contains a Forge mixin declaration.
                if (file.name != "MANIFEST.MF") true
                else file.readText().contains("MixinConfigs")
            }
            if (forbiddenModMetadata.isNotEmpty()) {
                throw GradleException(
                    "Developer API jar must not be installable mod metadata: " +
                        forbiddenModMetadata.joinToString { it.name }
                )
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
                    "API sources reference Talking Colonists implementation packages: " +
                        leakingSources.joinToString { it.relativeTo(rootProject.projectDir).path }
                )
            }
        }
    }

    tasks.named("build") {
        dependsOn(apiJar, apiSourcesJar)
    }
    tasks.named("check") {
        dependsOn(verifyApiJar)
    }
    tasks.matching { it.name == "buildAndCollect" }.configureEach {
        this as Copy
        from(apiJar, apiSourcesJar)
    }

    val loader = sc.current.component1().split("-")[1]
    extensions.configure<PublishingExtension> {
        publications.create<MavenPublication>("api") {
            groupId = "me.sshcrack"
            artifactId = "${prop("mod.id")}-api"
            version = "${prop("mod.version")}${prop("mod.channel_tag")}-${prop("deps.minecraft")}-${loader}"
            artifact(apiJar)
            artifact(apiSourcesJar)
            pom {
                name.set("${prop("mod.name")} Addon API")
                description.set("Developer-only compile API for addons. Runtime is provided by the normal Talking Colonists mod.")
            }
        }
    }
}
