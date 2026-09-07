# Minecraft 1.18.2 experimental lane

This lane is an independently configured Java 17 feasibility build for Minecraft 1.18.2. It is intentionally separate from the 26.x Gradle/Loom/NeoForge builds and from the 1.19.2 build.

## Pinned candidates

| Surface | Pin |
|---|---|
| Minecraft | `1.18.2` |
| Java | `17` |
| Gradle | `8.6` for Fabric/Paper; `7.6.4` for the isolated ForgeGradle build |
| Fabric Loader | `0.19.5` |
| Fabric API | `0.77.0+1.18.2` |
| Yarn | `1.18.2+build.4` |
| Fabric Loom | `1.6.12` |
| Forge | `1.18.2-40.3.11` |
| ForgeGradle | `5.1.77` |
| Paper API | `1.18.2-R0.1-SNAPSHOT` |

## Build

From the repository root, use a Java 17 installation. Fabric/Paper use Gradle 8.6; ForgeGradle 5.1.77 is isolated in its own nested build and uses Gradle 7.6.4 because ForgeGradle 5 rejects Gradle 8 and newer:

```text
gradle --no-daemon -p platforms/mc1.18.2 clean build
gradle --no-daemon -p platforms/mc1.18.2/forge clean build
```

The root lane `shared` project compiles the existing Minecraft-free domain, protocol, media API, client coordinator, and server service sources with Java 8 bytecode. Fabric and Paper compile against that shared project with Java 17. The nested Forge build compiles the same shared source set directly so its incompatible ForgeGradle toolchain remains independent.

## Implemented subset

- Fabric has separate server and client entrypoints and a bounded `Identifier`/`PacketByteBuf` envelope around the shared wire codec.
- Forge registers a bounded `SimpleChannel` packet around the shared wire codec.
- Paper packages a server-only status command and does not depend on client or native media classes.
- All payloads reject negative, oversized, or length-mismatched buffers before allocation or decode.

## Feasibility gate

This is an experimental compiling scaffold, not a runtime support claim. The exact Java 17 builds pass with Gradle 8.6 for Fabric/Paper and Gradle 7.6.4 for the isolated ForgeGradle build. The lane does not yet implement legacy rendering, dynamic textures, media decoding, audio, persistence integration, reconnect/dimension lifecycle, dedicated-server startup tests, screenshot tests, or two-client synchronization tests. Those gates must pass before this combination can be marked supported. The Paper, Fabric, and Forge artifacts are intentionally server-safe where applicable; no native media runtime is included.
