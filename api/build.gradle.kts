plugins {
    id("java-library")
    id("maven-publish")
}

group = "me.sshcrack"
version = "1.0.0"

base {
    archivesName.set("mc_talking_api")
}

java {
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("org.jetbrains:annotations:24.0.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "me.sshcrack"
            artifactId = "mc_talking_api"
            version = project.version.toString()

            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "sshcrackReleases"
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
