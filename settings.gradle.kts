pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "vidscreen"

include(
    "shared:client-core",
    "shared:domain",
    "shared:protocol",
    "shared:media-api",
    "shared:media-ffmpeg",
    "shared:media-ffmpeg-native",
    "shared:media-ytdlp",
    "shared:server-core",
    "shared:testkit",
    "platforms:mc26.2:server-paper",
    "platforms:mc26.2:media-runtime-fabric",
    "platforms:mc26.2:media-runtime-neoforge",
    "platforms:mc26.2:fabric",
    "platforms:mc26.2:neoforge",
    "platforms:mc26.1.2:server-paper",
    "platforms:mc26.1.2:media-runtime-fabric",
    "platforms:mc26.1.2:media-runtime-neoforge",
    "platforms:mc26.1.2:fabric",
    "platforms:mc26.1.2:neoforge"
)
