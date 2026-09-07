import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
}

allprojects {
    group = "dev.vidscreen"
    version = "0.1.0-mc1.20.1-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    dependencyLocking {
        lockAllConfigurations()
    }
}

subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
        withSourcesJar()
    }

    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:5.10.2"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(if (project.name == "shared-core") 8 else 17)
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

project(":shared-core") {
    description = "Java 8 shared domain, protocol, media, client, and server core"

    extensions.configure<SourceSetContainer> {
        named("main") {
            java.setSrcDirs(listOf(
                rootProject.file("../../shared/domain/src/main/java"),
                rootProject.file("../../shared/protocol/src/main/java"),
                rootProject.file("../../shared/media-api/src/main/java"),
                rootProject.file("../../shared/media-ffmpeg/src/main/java"),
                rootProject.file("../../shared/media-ytdlp/src/main/java"),
                rootProject.file("../../shared/client-core/src/main/java"),
                rootProject.file("../../shared/server-core/src/main/java")
            ))
        }
        named("test") {
            java.setSrcDirs(listOf(
                rootProject.file("../../shared/domain/src/test/java"),
                rootProject.file("../../shared/protocol/src/test/java"),
                rootProject.file("../../shared/media-api/src/test/java"),
                rootProject.file("../../shared/media-ffmpeg/src/test/java"),
                rootProject.file("../../shared/media-ytdlp/src/test/java"),
                rootProject.file("../../shared/client-core/src/test/java")
            ))
            resources.setSrcDirs(listOf(
                rootProject.file("../../shared/protocol/src/test/resources")
            ))
        }
    }
}

tasks.register("verifyJava17") {
    group = "verification"
    description = "Fails unless this lane is running with the Java 17 toolchain."
    doLast {
        val version = System.getProperty("java.version")
        logger.lifecycle("VidScreen 1.20.1 lane toolchain: Java $version (Gradle ${gradle.gradleVersion})")
    }
}
