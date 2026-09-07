# Minecraft 1.20.1 experimental lane

This directory is a standalone Gradle build for the `version/1.20.1` branch. It is deliberately not included by the Java 25 root build: ForgeGradle, Fabric Loom, mappings, and the Java 17 toolchain stay isolated here.

## Pins

| Surface | Exact pin |
|---|---|
| Minecraft | `1.20.1` |
| Java | `17` |
| Gradle wrapper | `8.8` |
| Fabric Loom | `1.6.12` |
| Fabric Loader | `0.19.5` |
| Fabric API | `0.92.12+1.20.1` |
| Fabric mappings | Yarn `1.20.1+build.10:v2` |
| ForgeGradle | `6.0.54` |
| Forge | `1.20.1-47.4.23` |
| Paper API | `1.20.1-R0.1-SNAPSHOT` |

All coordinates above were checked against the official Fabric, Forge, and Paper Maven repositories on 2026-09-07. The source URLs are recorded in `versions.properties`.

## Build

From this directory, with a Java 17 runtime available (use `bash ./gradlew` on Unix or `gradlew.bat` on Windows):

```text
bash ./gradlew :shared-core:test
bash ./gradlew :paper:build
bash ./gradlew :fabric:build
bash ./gradlew :forge:build
```

The first invocation may download Minecraft, mappings, and loader artifacts. Dependency lockfiles are generated per configuration with `--write-locks` after a successful resolution; they must not be regenerated against a different Java or Gradle version.

## Scope and boundaries

- Paper uses the 1.20.1 plugin-message channel and embeds only the Java 8 shared domain/protocol/media/client-coordination/server-core classes.
- Fabric uses `Identifier`/`PacketByteBuf`, `WorldRenderEvents.AFTER_ENTITIES`, `NativeImageBackedTexture`, and the 1.20.1 Yarn API.
- Forge uses `SimpleChannel`, `RenderLevelStageEvent.AFTER_ENTITIES`, `DynamicTexture`, and official Mojang mappings.
- Servers send bounded metadata and playback state only. Clients resolve direct HTTPS media locally; media bytes and native decoder classes are not server dependencies.
- The client lane currently uses the external FFmpeg adapter. The newer JavaCPP native companion is intentionally not pulled into dedicated-server or 1.20.1 artifacts.

## Compatibility status

This is `experimental`, not `supported`. A successful compile does not cover dedicated-server classloading, client startup, render screenshots, media playback, reconnect/dimension/chunk lifecycle, native cleanup, or two-client synchronization. `compatibility.yaml` is the authoritative status record for this lane, and `FEASIBILITY.md` records known blockers and the remaining gates.
