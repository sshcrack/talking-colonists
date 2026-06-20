plugins {
    `java-library`
    `maven-publish`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withJavadocJar()
    withSourcesJar()
}

group = "me.sshcrack"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "me.sshcrack"
            artifactId = "talking_colonists_api"
            version = project.version.toString()

            from(components["java"])
        }
    }
}
