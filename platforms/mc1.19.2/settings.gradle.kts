pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "vidscreen-mc1.19.2"
include(":shared", ":fabric", ":forge", ":paper")
