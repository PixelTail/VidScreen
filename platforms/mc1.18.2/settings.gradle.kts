pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "vidscreen-mc1.18.2"
include(":shared", ":fabric", ":forge", ":paper")
