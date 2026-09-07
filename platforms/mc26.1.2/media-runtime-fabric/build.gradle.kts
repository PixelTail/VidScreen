description = "Client-only Fabric media runtime for Windows x64 and Linux x64"

base {
    archivesName.set("vidscreen-media-runtime-fabric-26.1.2")
}

val javaCppVersion = "1.5.14"
val ffmpegVersion = "8.1.2-1.5.14"
val runtimeContents by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    testImplementation(project(":shared:media-ffmpeg-native"))
    runtimeContents("org.bytedeco:javacpp:$javaCppVersion") { isTransitive = false }
    runtimeContents("org.bytedeco:javacpp:$javaCppVersion:windows-x86_64") { isTransitive = false }
    runtimeContents("org.bytedeco:javacpp:$javaCppVersion:linux-x86_64") { isTransitive = false }
    runtimeContents("org.bytedeco:ffmpeg:$ffmpegVersion") { isTransitive = false }
    runtimeContents("org.bytedeco:ffmpeg:$ffmpegVersion:windows-x86_64") { isTransitive = false }
    runtimeContents("org.bytedeco:ffmpeg:$ffmpegVersion:linux-x86_64") { isTransitive = false }
    runtimeContents("org.bytedeco:javacv:$javaCppVersion") { isTransitive = false }
}

configurations.named("testRuntimeClasspath") {
    extendsFrom(runtimeContents)
}

tasks.jar {
    dependsOn(project(":shared:media-ffmpeg-native").tasks.named("classes"))
    from(project(":shared:media-ffmpeg-native")
        .extensions.getByType<org.gradle.api.tasks.SourceSetContainer>()
        .named("main")
        .map { it.output })
    from(provider {
        runtimeContents.files.map { file -> if (file.isDirectory) file else zipTree(file) }
    })
    exclude(
        "META-INF/MANIFEST.MF",
        "META-INF/*.SF",
        "META-INF/*.RSA",
        "META-INF/*.DSA",
        "META-INF/INDEX.LIST",
        "module-info.class",
        "META-INF/versions/**/module-info.class"
    )
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
