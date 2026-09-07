import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
}

val targetLane = providers.gradleProperty("vidscreen.targetLane").orNull

allprojects {
    group = "dev.vidscreen"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    dependencyLocking {
        lockAllConfigurations()
    }
}

subprojects {
    apply(plugin = "java-library")

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(
                if (targetLane == "mc1.21.1" || project.path.startsWith(":platforms:mc1.21.1")) 21 else 25
            ))
            withSourcesJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(when {
                project.path.startsWith(":shared") -> 8
                project.path.startsWith(":platforms:mc1.21.1") -> 21
                else -> 25
            })
            options.encoding = "UTF-8"
        }

        dependencies {
            add("testImplementation", platform("org.junit:junit-bom:5.12.2"))
            add("testImplementation", "org.junit.jupiter:junit-jupiter")
            add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}
