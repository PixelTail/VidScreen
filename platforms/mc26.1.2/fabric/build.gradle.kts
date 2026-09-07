plugins {
    id("net.fabricmc.fabric-loom") version "1.17.20"
}

base {
    archivesName.set("vidscreen-fabric-26.1.2")
}

loom {
    splitEnvironmentSourceSets()

    mods {
        create("vidscreen") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:26.1.2")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    implementation("net.fabricmc.fabric-api:fabric-api:0.155.2+26.1.2")
    implementation(project(":shared:domain"))
    implementation(project(":shared:protocol"))
    implementation(project(":shared:media-api"))
    implementation(project(":shared:media-ffmpeg"))
    add("clientCompileOnly", project(":shared:media-ffmpeg-native"))
    implementation(project(":shared:media-ytdlp"))
    implementation(project(":shared:client-core"))
    implementation(project(":shared:server-core"))
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
