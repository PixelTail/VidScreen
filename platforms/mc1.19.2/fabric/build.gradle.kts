plugins {
    id("fabric-loom") version "1.6.12"
}

base {
    archivesName.set("vidscreen-fabric-1.19.2")
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
    minecraft("com.mojang:minecraft:1.19.2")
    mappings("net.fabricmc:yarn:1.19.2+build.28:v2")
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.77.0+1.19.2")
    implementation(project(":shared"))
}

tasks.jar {
    dependsOn(project(":shared").tasks.named("classes"))
    from(project(":shared").extensions.getByType<org.gradle.api.tasks.SourceSetContainer>().named("main").map { it.output })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
