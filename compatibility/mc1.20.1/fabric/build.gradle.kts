plugins {
    id("fabric-loom") version "1.6.12"
}

base {
    archivesName.set("vidscreen-fabric-1.20.1")
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
    minecraft("com.mojang:minecraft:1.20.1")
    mappings("net.fabricmc:yarn:1.20.1+build.10:v2")
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.92.12+1.20.1")
    implementation(project(":shared-core"))
}

tasks.named("compileJava") {
    dependsOn(project(":shared-core").tasks.named("classes"))
}

tasks.named("compileClientJava") {
    dependsOn(project(":shared-core").tasks.named("classes"))
}

// Keep dependency coordinates visible in the lane's generated reports and prevent
// accidental use of the Java 25 root toolchain.
tasks.register("verifyFabricPins") {
    doLast {
        check(project.version.toString().contains("1.20.1"))
        logger.lifecycle("Fabric pins: MC 1.20.1, Yarn 1.20.1+build.10, Loader 0.19.5, API 0.92.12+1.20.1, Loom 1.6.12")
    }
}
