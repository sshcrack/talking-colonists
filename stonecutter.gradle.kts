@file:OptIn(dev.kikugie.stonecutter.StonecutterExperimentalAPI::class)

plugins {
	alias(libs.plugins.stonecutter)
	alias(libs.plugins.dotenv)
	alias(libs.plugins.fabric.loom).apply(false)
	alias(libs.plugins.fabric.loom.remap).apply(false)
	alias(libs.plugins.neoforged.moddev).apply(false)
	alias(libs.plugins.jsonlang.postprocess).apply(false)
	alias(libs.plugins.mod.publish.plugin).apply(false)
	alias(libs.plugins.kotlin.jvm).apply(false)
	alias(libs.plugins.devtools.ksp).apply(false)
	alias(libs.plugins.fletching.table).apply(false)
	alias(libs.plugins.legacyforge.moddev).apply(false)
}

stonecutter active file(".sc_active_version")

for (version in stonecutter.versions.map { it.version }.distinct()) tasks.register("publish$version") {
	group = "publishing"
	dependsOn(stonecutter.tasks.named("publishMods") { metadata.version == version })
}

stonecutter tasks {
	val ordering = versionComparator.thenComparingInt { task ->
		if (task.metadata.project.endsWith("fabric")) 1 else 0
	}

	listOf("publishModrinth", "publishCurseforge").forEach { taskName ->
		gradle.allprojects {
			if (project.tasks.findByName(taskName) != null) {
				order(taskName, ordering)
			}
		}
	}
}

tasks.register("runActiveClient") {
	group = "stonecutter"
	description = "Run client of the active Stonecutter version"
	dependsOn(stonecutter.current!!.project + ":runClient")
}

tasks.register("runActiveServer") {
	group = "stonecutter"
	description = "Run server of the active Stonecutter version"
	dependsOn(stonecutter.current!!.project + ":runServer")
}

stonecutter parameters {
	constants.match(node.metadata.project.substringAfterLast('-'), "fabric", "neoforge", "forge")
	filters.include("**/*.fsh", "**/*.vsh")
	swaps["mod_version"] = "\"${sc.properties.get<String>("mod.version")}\";"
	swaps["mod_id"] = "\"${sc.properties.get<String>("mod.id")}\";"
	swaps["mod_name"] = "\"${sc.properties.get<String>("mod.name")}\";"
	swaps["mod_group"] = "\"${sc.properties.get<String>("mod.group")}\";"
	swaps["minecraft"] = "\"${node.metadata.version}\";"
	constants["release"] = sc.properties.get<String>("mod.id") != "modtemplate"
}

subprojects {
    tasks.matching { it.name.startsWith("publish") }.configureEach {
        doFirst {
            val libraryDir = file("../gemini-live-library")
            val confirmed = project.findProperty("geminiPublished") == "true"
                || System.getenv("GEMINI_PUBLISHED") == "true"

            if (libraryDir.exists() && !confirmed) {
                throw GradleException(
                    "gemini-live-library is still included locally! " +
                        "Either publish it first, or confirm with -PgeminiPublished=true"
                )
            }
        }
    }
}
