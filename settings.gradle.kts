pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "vidscreen"

val targetLane = providers.gradleProperty("vidscreen.targetLane").orNull

fun includeLaneProject(projectPath: String, lane: String) {
    if (targetLane == null || targetLane == lane) {
        include(projectPath)
    }
}

include(
    "shared:client-core",
    "shared:domain",
    "shared:protocol",
    "shared:media-api",
    "shared:media-ffmpeg",
    "shared:media-ffmpeg-native",
    "shared:media-ytdlp",
    "shared:server-core",
    "shared:testkit"
)

listOf("mc26.2", "mc26.1.2", "mc1.21.1").forEach { lane ->
    includeLaneProject("platforms:$lane:server-paper", lane)
    includeLaneProject("platforms:$lane:media-runtime-fabric", lane)
    includeLaneProject("platforms:$lane:media-runtime-neoforge", lane)
    includeLaneProject("platforms:$lane:fabric", lane)
    includeLaneProject("platforms:$lane:neoforge", lane)
}
