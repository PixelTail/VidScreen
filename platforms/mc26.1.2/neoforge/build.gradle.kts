plugins {
    id("net.neoforged.moddev") version "2.0.144"
}

base {
    archivesName.set("vidscreen-neoforge-26.1.2")
}

neoForge {
    version = "26.1.2.94"

    runs {
        create("client") {
            client()
        }
        create("server") {
            server()
            programArgument("--nogui")
        }
        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", "vidscreen")
        }
    }

    mods {
        create("vidscreen") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation(project(":shared:domain"))
    implementation(project(":shared:protocol"))
    implementation(project(":shared:media-api"))
    implementation(project(":shared:media-ffmpeg"))
    compileOnly(project(":shared:media-ffmpeg-native"))
    implementation(project(":shared:media-ytdlp"))
    implementation(project(":shared:client-core"))
    implementation(project(":shared:server-core"))
}

configurations.configureEach {
    if (name.startsWith("neoFormRuntimeDependencies")) {
        resolutionStrategy.deactivateDependencyLocking()
    }
}

tasks.jar {
    dependsOn(
        project(":shared:domain").tasks.named("classes"),
        project(":shared:protocol").tasks.named("classes"),
        project(":shared:media-api").tasks.named("classes"),
        project(":shared:media-ffmpeg").tasks.named("classes"),
        project(":shared:media-ytdlp").tasks.named("classes"),
        project(":shared:client-core").tasks.named("classes"),
        project(":shared:server-core").tasks.named("classes")
    )
    from(project(":shared:domain").extensions.getByType<org.gradle.api.tasks.SourceSetContainer>().named("main").map { it.output })
    from(project(":shared:protocol").extensions.getByType<org.gradle.api.tasks.SourceSetContainer>().named("main").map { it.output })
    from(project(":shared:media-api").extensions.getByType<org.gradle.api.tasks.SourceSetContainer>().named("main").map { it.output })
    from(project(":shared:media-ffmpeg").extensions.getByType<org.gradle.api.tasks.SourceSetContainer>().named("main").map { it.output })
    from(project(":shared:media-ytdlp").extensions.getByType<org.gradle.api.tasks.SourceSetContainer>().named("main").map { it.output })
    from(project(":shared:client-core").extensions.getByType<org.gradle.api.tasks.SourceSetContainer>().named("main").map { it.output })
    from(project(":shared:server-core").extensions.getByType<org.gradle.api.tasks.SourceSetContainer>().named("main").map { it.output })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
