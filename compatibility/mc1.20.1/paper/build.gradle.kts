import org.gradle.language.jvm.tasks.ProcessResources

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":shared-core"))
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
}

base {
    archivesName.set("vidscreen-paper-1.20.1")
}

tasks.named<ProcessResources>("processResources") {
    val replaceProperties = mapOf("version" to project.version)
    filesMatching("plugin.yml") {
        expand(replaceProperties)
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(configurations.runtimeClasspath.get().buildDependencies)
    from({
        configurations.runtimeClasspath.get().map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    })
}

tasks.register("verifyPaperPins") {
    doLast {
        logger.lifecycle("Paper pins: API 1.20.1-R0.1-SNAPSHOT, Java 17")
    }
}
