import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import dev.kikugie.stonecutter.data.deserialization.SCList
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

@OptIn(StonecutterExperimentalAPI::class)
class Context(
	val project: Project,
	val extension: ModPlatformExtension,
	val loader: Loader,
	val stonecutter: StonecutterBuildExtension
) {
	val currentMcVersion: String = stonecutter.current.version

	val modId: String = project.sc.properties["mod.id"]
	val modName: String = project.sc.properties["mod.name"]
	val modGroup: String = project.sc.properties["mod.group"]
	val modVersion: String = project.sc.properties["mod.version"]
	val channelTag: String = project.sc.properties["mod.channel_tag"]
	val description: String = project.sc.properties["mod.description"]
	val licenseName: String = project.sc.properties["mod.license.name"]
	val licenseUrl: String = project.sc.properties["mod.license.url"]
	val licenseDist: String = project.sc.properties["mod.license.dist"]
	val inceptionYear: String = project.sc.properties["mod.inception_year"]

	val authors: List<String> = project.sc.properties.raw("mod", "authors").asList().map { it.toString() }
	val contributors: List<String> = project.sc.properties.raw("mod", "contributors").asList().map { it.toString() }

	val sourcesUrl: String = project.sc.properties["mod.sources_url"]
	val homepageUrl: String = project.sc.properties["mod.homepage_url"]
	val discordUrl: String = project.sc.properties["mod.discord_url"]
	val issuesUrl: String = project.sc.properties.get<String>("mod.issues_url").ifEmpty { "$sourcesUrl/issues" }

	val isSnapshot: Boolean = !project.envTrue("MOD_IS_RELEASE")
	val baseVersion: String = "$modVersion$channelTag"
	val snapshotSuffix: String = if (isSnapshot) "-SNAPSHOT" else ""
	val fullVersion: String = "$baseVersion-${loader.id}+$currentMcVersion$snapshotSuffix"
	val basicVersion: String = "$baseVersion$snapshotSuffix"

	val publishAdditionalVersions: List<String> = (project.sc.properties.rawOrNull("publish", "additionalVersions") as? SCList)?.asList()?.map { it.toString() }
			?: emptyList()

	val javaVersion: JavaVersion = when {
		stonecutter.eval(currentMcVersion, ">=26") -> JavaVersion.VERSION_25
		stonecutter.eval(currentMcVersion, ">=1.20.6") -> JavaVersion.VERSION_21
		stonecutter.eval(currentMcVersion, ">=1.18") -> JavaVersion.VERSION_17
		stonecutter.eval(currentMcVersion, ">=1.17") -> JavaVersion.VERSION_16
		else -> JavaVersion.VERSION_1_8
	}
}

