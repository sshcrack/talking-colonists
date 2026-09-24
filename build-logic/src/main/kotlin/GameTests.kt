import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.named

/** Package that holds every GameTest class; it must never appear in a release jar. */
const val GAME_TEST_PACKAGE_PATH = "me/sshcrack/mc_talking/gametest/"

/**
 * Creates the dev-only `gameTest` source set (`src/gameTest/{java,resources}`).
 *
 * GameTest code lives in its own source set rather than behind the `devtools` Stonecutter switch:
 * the normal `jar`/`reobfJar` only package `main` + `addonApi`, so tests and their structure
 * templates can never ship, and running GameTests does not require rewriting the active source
 * view. The loader build scripts add this source set to the dev mod definition and point the
 * `gameTestServer` run at its runtime classpath. As a belt-and-braces check,
 * `verifyReleaseJarExcludesGameTests` (run after every `jar` and by `check`) fails if any GameTest
 * class or template ever ends up inside the release jar.
 */
fun Project.configureGameTests(): SourceSet {
    val javaExtension = extensions.getByType(JavaPluginExtension::class.java)
    val main = javaExtension.sourceSets.getByName("main")
    val addonApi = javaExtension.sourceSets.getByName("addonApi")
    val gameTest = javaExtension.sourceSets.create("gameTest") {
        compileClasspath += main.output + addonApi.output
        runtimeClasspath += main.output + addonApi.output
    }

    configurations.getByName(gameTest.implementationConfigurationName)
        .extendsFrom(configurations.getByName(main.implementationConfigurationName))
    configurations.getByName(gameTest.compileOnlyConfigurationName)
        .extendsFrom(configurations.getByName(main.compileOnlyConfigurationName))
    configurations.getByName(gameTest.runtimeOnlyConfigurationName)
        .extendsFrom(configurations.getByName(main.runtimeOnlyConfigurationName))

    val jarFile = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    val verifyJar = tasks.register("verifyReleaseJarExcludesGameTests") {
        group = "verification"
        description = "Fails if GameTest classes or resources leaked into the release jar"
        inputs.file(jarFile)
        doLast {
            val leaked = project.zipTree(jarFile.get()).matching {
                include("$GAME_TEST_PACKAGE_PATH**")
                include("data/*/structure/empty_floor.nbt", "data/*/structures/empty_floor.nbt")
            }.files
            if (leaked.isNotEmpty()) {
                throw GradleException("GameTest code leaked into the release jar: ${leaked.joinToString { it.name }}")
            }
        }
    }
    tasks.named("jar") { finalizedBy(verifyJar) }
    tasks.named("check") { dependsOn(verifyJar) }
    return gameTest
}
