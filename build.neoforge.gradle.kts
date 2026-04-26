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
			curseforge = "voicechat"
			forgeLikeVersionRange = "[${voicechat_version},)"
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
	}

	mods {
		register(prop("mod.id")) {
			sourceSet(sourceSets["main"])
		}
	}
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
}


publishing {
	publications {
		create<MavenPublication>("maven") {
			groupId = "me.sshcrack"
			artifactId = prop("mod.id")
			version = "${prop("mod.version")}-${prop("deps.minecraft")}-neoforge"

			artifact(tasks.named("jar"))
			artifact(tasks.named("kotlinSourcesJar"))
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

tasks.register("publishSshcrack") {
	dependsOn("publishMavenPublicationToSshcrackRepositoryRepository")
}

dependencies {
	implementation(libs.moulberry.mixinconstraints)
	jarJar(libs.moulberry.mixinconstraints)

	implementation("de.maxhenkel.voicechat:voicechat-api:${prop("deps.voicechat_api_version")}")
	runtimeOnly("maven.modrinth:simple-voice-chat:neoforge-${voicechat_version}")
	implementation("me.sshcrack:gemini_live_lib:${prop("deps.gemini_live_lib_version")}-${prop("deps.minecraft")}-neoforge")

	implementation("com.ldtteam:minecolonies:${prop("deps.minecolonies_version")}")
	runtimeOnly("com.ldtteam:domum-ornamentum:${prop("deps.domum_version")}")
	runtimeOnly("com.ldtteam:structurize:${prop("deps.structurize_version")}")
	runtimeOnly("com.ldtteam:blockui:${prop("deps.blockui_version")}")
}

tasks.named("createMinecraftArtifacts") {
	dependsOn(tasks.named("stonecutterGenerate"))
}
