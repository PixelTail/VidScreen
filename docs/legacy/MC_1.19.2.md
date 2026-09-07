# Minecraft 1.19.2 experimental lane

This lane is an independently configured Java 17 feasibility build for Minecraft 1.19.2. It is intentionally separate from the 26.x Gradle/Loom/NeoForge builds.

## Pinned candidates

| Surface | Pin |
|---|---|
| Minecraft | `1.19.2` |
| Java | `17` |
| Gradle | `8.6` (CI invokes this exact version) |
| Fabric Loader | `0.19.5` |
| Fabric API | `0.77.0+1.19.2` |
| Yarn | `1.19.2+build.28` |
| Fabric Loom | `1.6.12` |
| Forge | `1.19.2-43.5.1` |
| ForgeGradle | `6.0.54` |
| Paper API | `1.19.2-R0.1-SNAPSHOT` |

## Build

From the repository root, use a Java 17 installation and Gradle 8.6:

```text
gradle --no-daemon -p platforms/mc1.19.2 clean build
```

The `shared` project compiles the existing Minecraft-free domain, protocol, media API, client coordinator, and server service sources with Java 8 bytecode. The Fabric, Forge, and Paper subprojects then compile their version-specific adapters with Java 17 and package the shared classes into their own artifacts.

## Implemented subset

- Fabric has separate server and client entrypoints and a bounded `Identifier`/`PacketByteBuf` envelope around the shared wire codec.
- Forge registers a bounded `SimpleChannel` packet around the shared wire codec.
- Paper packages a server-only status command and does not depend on client or native media classes.
- All payloads reject negative, oversized, or length-mismatched buffers before allocation or decode.

## Feasibility gate

This is an experimental compiling scaffold, not a runtime support claim. The exact Java 17 / Gradle 8.6 build passes locally for shared, Paper, ForgeGradle 6.0.54, and Fabric Loom 1.6.12; branch CI and all runtime gates remain pending. The lane does not yet implement legacy rendering, dynamic textures, media decoding, audio, persistence integration, reconnect/dimension lifecycle, dedicated-server startup tests, screenshot tests, or two-client synchronization tests. Those gates must pass before this combination can be marked supported. The Paper, Fabric, and Forge artifacts are intentionally server-safe where applicable; no native media runtime is included.
