description = "Java 8-compatible shared VidScreen domain, protocol, and service logic"

sourceSets {
    main {
        java.srcDirs(
            "../../../shared/domain/src/main/java",
            "../../../shared/protocol/src/main/java",
            "../../../shared/media-api/src/main/java",
            "../../../shared/client-core/src/main/java",
            "../../../shared/server-core/src/main/java"
        )
    }
}
