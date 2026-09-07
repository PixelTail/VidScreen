base {
    archivesName.set("vidscreen-paper-1.18.2")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    implementation(project(":shared"))
}

tasks.jar {
    dependsOn(project(":shared").tasks.named("classes"))
    from(project(":shared").extensions.getByType<org.gradle.api.tasks.SourceSetContainer>().named("main").map { it.output })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
