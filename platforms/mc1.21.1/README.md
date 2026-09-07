# VidScreen Minecraft 1.21.1 lane

This directory is an **experimental compatibility lane** for Minecraft 1.21.1 and Java 21 bytecode. It is not a runtime support claim.

## Exact pins

- Fabric Loader `0.19.5`
- Fabric API `0.116.17+1.21.1`
- Fabric Loom `1.10.5`
- Yarn mappings `1.21.1+build.3:v2`
- NeoForge `21.1.250`
- ModDevGradle `2.0.146`
- Paper API `1.21.1-R0.1-SNAPSHOT`
- Platform compilation release `21`; shared modules remain Java 8 compatible.

Loom candidate evidence is recorded in the branch history and compatibility metadata. Loom `1.7.4` was rejected under this repository's Gradle 9 toolchain because its problem-reporting API is incompatible; Loom `1.8.12` was rejected after the required Yarn mapping was added because of a `dependencyProject` reflection failure. Loom `1.10.5` configures and compiles the lane with the exact Yarn build, so it is pinned here instead of relying on the non-remapping 26.x setup.

## Version-specific seams

- Fabric uses the 1.21.1 Yarn `CustomPayload`/`PacketCodec` API and `PayloadTypeRegistry.playS2C()`/`playC2S()` registration.
- Fabric rendering uses `WorldRenderEvents.AFTER_TRANSLUCENT` and 1.21.1 dynamic textures.
- NeoForge rendering uses `RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS`.
- NeoForge networking uses the 1.21.1 `CustomPacketPayload`/`StreamCodec` API.
- Server modules only depend on shared domain, protocol, and server code. Client rendering and native FFmpeg companion classes are not server dependencies.
- Media remains client-side. The native FFmpeg runtime is optional; external FFmpeg remains an experimental fallback.

## Verification

From the repository root, run with Java 21. The root settings isolate this lane when `vidscreen.targetLane=mc1.21.1` is supplied, avoiding incompatible Fabric Loom classpaths from newer lanes.

```powershell
$env:JAVA_HOME = "D:\\Git\\vid\\.tools\\jdk-21.0.12.1+1"
.\\gradlew.bat -Pvidscreen.targetLane=mc1.21.1 :platforms:mc1.21.1:fabric:build --no-configuration-cache
.\\gradlew.bat -Pvidscreen.targetLane=mc1.21.1 :platforms:mc1.21.1:neoforge:build --no-configuration-cache
.\\gradlew.bat -Pvidscreen.targetLane=mc1.21.1 :platforms:mc1.21.1:server-paper:build --no-configuration-cache
.\\gradlew.bat -Pvidscreen.targetLane=mc1.21.1 :platforms:mc1.21.1:media-runtime-fabric:build --no-configuration-cache
.\\gradlew.bat -Pvidscreen.targetLane=mc1.21.1 :platforms:mc1.21.1:media-runtime-neoforge:build --no-configuration-cache
```

A successful build is only a compile/package gate. Runtime launch, client render screenshots, direct MP4/HLS playback, native cleanup, reconnect/dimension/chunk lifecycle, secure media connection-boundary checks, and two-client synchronization remain required before any support promotion.
