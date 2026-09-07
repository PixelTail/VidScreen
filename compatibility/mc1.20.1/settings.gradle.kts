pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "vidscreen-mc1.20.1"

include("shared-core", "fabric", "forge", "paper")

// This build is intentionally standalone. It never applies the Java 25 conventions
// or plugin classpaths from the repository root.
