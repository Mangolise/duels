plugins {
    id("java")
    id("maven-publish")
    id("io.github.goooler.shadow") version("8.1.7")
}

var versionStr = System.getenv("GIT_COMMIT") ?: "dev"

group = "net.mangolise"
version = versionStr

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.serble.net/snapshots/")
}

java {
    withSourcesJar()
}

dependencies {
    implementation("net.mangolise:mango-game-sdk:latest")
    implementation("net.mangolise:mango-combat:latest")
    implementation("net.minestom:minestom-snapshots:4fe2993057")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("packageWorlds", net.mangolise.gamesdk.gradle.PackageWorldTask::class.java)
tasks.processResources {
    dependsOn("packageWorlds")
}

publishing {
    repositories {
        maven {
            name = "serbleMaven"
            url = uri("https://maven.serble.net/snapshots/")
            credentials {
                username = System.getenv("SERBLE_REPO_USERNAME") ?: ""
                password = System.getenv("SERBLE_REPO_PASSWORD") ?: ""
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }

    publications {
        create<MavenPublication>("mavenGitCommit") {
            groupId = "net.mangolise"
            artifactId = "duels"
            version = versionStr
            from(components["java"])
        }

        create<MavenPublication>("mavenLatest") {
            groupId = "net.mangolise"
            artifactId = "duels"
            version = "latest"
            from(components["java"])
        }
    }
}

tasks.withType<Jar> {
    manifest {
        // Change this to your main class
        attributes["Main-Class"] = "net.mangolise.duels.Test"
    }
}
