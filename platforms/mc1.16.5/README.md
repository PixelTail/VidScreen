# VidScreen Minecraft 1.16.5 lane

This directory is an independent Gradle lane for the legacy 1.16.5 feasibility work. Each incompatible loader surface has its own Gradle build and wrapper; none participates in the Java 25 root build.

## Pinned inputs

| Surface | Pin | Resolution evidence |
|---|---|---|
| Minecraft | `1.16.5` | Forge MDK and Fabric Maven |
| Java target | 8 (`--release 8`) | `build.gradle.kts` |
| Fabric Gradle | 7.6.4 | `fabric/gradle/wrapper/gradle-wrapper.properties` |
| Forge/Paper Gradle | 8.4 | `forge/gradle/wrapper/gradle-wrapper.properties`, `paper/gradle/wrapper/gradle-wrapper.properties` |
| Forge | `1.16.5-36.2.42` | official Forge Maven |
| ForgeGradle | `6.0.54` candidate | official Forge Maven; build verification is required |
| Fabric Loader | `0.11.7` | Fabric Maven |
| Fabric Yarn | `1.16.5+build.10:v2` | Fabric Maven |
| Fabric API | `0.42.0+1.16` | Fabric Maven |
| Fabric Loom | `0.10.7` | Fabric Maven |
| Paper API | `com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT` | Paper repository snapshot metadata |

Run `fabric\\gradlew.bat build`, `forge\\gradlew.bat build`, and `paper\\gradlew.bat build` from this directory with a JDK that can run the pinned wrapper. The builds emit Java 8 bytecode. `toolchain.properties` records the intended target and does not install a JDK. Fabric Loom 0.10.7 is kept on Gradle 7.6.4 because it calls the removed Gradle 8 archive classifier API; ForgeGradle 6.0.54 and the Paper build stay on Gradle 8.4.

## Scope and isolation

* `shared` compiles the existing Minecraft-independent domain and wire protocol sources without depending on the Java 25 root build.
* `fabric` contains the old Fabric custom-payload transport and client-only world-render/texture seam. Client code is in `fabriclegacy.client`; no media bytes cross the server channel.
* `forge` contains the 1.16.5 Forge `SimpleChannel` transport and a `Dist.CLIENT` render/texture seam. The common entrypoint does not initialize renderer or decoder classes on a dedicated server.
* `paper` contains the server-only plugin-message adapter. It sends bounded control messages only and does not resolve, download, or relay media.
* JavaCPP/JavaCV/FFmpeg natives are intentionally absent. The media capability is disabled in this Java 8 lane until an exact native/package/license matrix is proven; an external client-side FFmpeg adapter may be added later.

This lane is **experimental**. A successful compile is not runtime support. The render smoke, two-client synchronization, reconnect/dimension lifecycle, native cleanup, and packaging gates remain open.
