import org.gradle.api.tasks.bundling.Jar
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("net.minecraftforge.gradle") version "6.0.54"
}

base {
    archivesName.set("vidscreen-forge-1.20.1")
}

minecraft {
    mappings("official", "1.20.1")
    copyIdeResources = true

    runs {
        create("client") {
            workingDirectory(project.file("run"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            mods {
                create("vidscreen") {
                    source(sourceSets.main.get())
                }
            }
        }
        create("server") {
            workingDirectory(project.file("run"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            mods {
                create("vidscreen") {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

dependencies {
    minecraft("net.minecraftforge:forge:1.20.1-47.4.23")
    implementation(project(":shared-core"))
}

// ForgeGradle synthesizes underscore-prefixed configurations whose native Netty
// modules differ by runner OS. Keep SHA-256 verification enabled, but do not apply
// a Windows-generated lock state to those generated configurations on Linux.
configurations.configureEach {
    if (name.startsWith("_")) {
        resolutionStrategy.deactivateDependencyLocking()
    }
}

tasks.named<Jar>("jar") {
    finalizedBy("reobfJar")
}

tasks.named("compileJava") {
    dependsOn(project(":shared-core").tasks.named("classes"))
}

tasks.named<ProcessResources>("processResources") {
    val replaceProperties = mapOf("version" to project.version)
    filesMatching("META-INF/mods.toml") {
        expand(replaceProperties)
    }
}

tasks.register("verifyForgePins") {
    doLast {
        logger.lifecycle("Forge pins: MC 1.20.1, Forge 47.4.23, ForgeGradle 6.0.54, Java 17")
    }
}
