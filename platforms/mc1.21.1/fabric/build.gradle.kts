plugins {
    id("fabric-loom") version "1.10.5"
}

base {
    archivesName.set("vidscreen-fabric-1.21.1")
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
    minecraft("com.mojang:minecraft:1.21.1")
    mappings("net.fabricmc:yarn:1.21.1+build.3:v2")
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.116.17+1.21.1")
    implementation(project(":shared:domain"))
    implementation(project(":shared:protocol"))
    implementation(project(":shared:media-api"))
    implementation(project(":shared:media-ffmpeg"))
    add("clientCompileOnly", project(":shared:media-ffmpeg-native"))
    implementation(project(":shared:media-ytdlp"))
    implementation(project(":shared:client-core"))
    implementation(project(":shared:server-core"))
}

tasks.named("remapJar") {
    notCompatibleWithConfigurationCache("Fabric Loom 1.10.5 remap state is not configuration-cache serializable under Gradle 9.5.1")
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
