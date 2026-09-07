# VidScreen Minecraft 1.12.2 lane

This directory is an independent, experimental Gradle lane for the 1.12.2 feasibility work. It does not participate in the Java 25 root build. Forge and Paper are separate builds because ForgeGradle 3.0.197 and modern Paper build tooling have different Gradle requirements.

## Pinned inputs

| Surface | Pin | Status |
|---|---|---|
| Minecraft | `1.12.2` | exact target |
| Java bytecode | 8 (`--release 8` where supported) | configured |
| Forge | `1.12.2-14.23.5.2864` | exact dependency declared |
| ForgeGradle | `3.0.197` | exact plugin declared |
| Forge Gradle wrapper | `5.6.4` | pinned; runtime JDK 8 is still required by this legacy toolchain |
| MCP mappings | `snapshot_20171003` (`20171003-1.12`) | configured in ForgeGradle |
| Paper API | `com.destroystokyo.paper:paper-api:1.12.2-R0.1-SNAPSHOT` | metadata resolves from Paper repository |
| Paper Gradle wrapper | `8.4` | independent server-only build |

The Forge wrapper uses the requested MCP snapshot and exact Forge coordinate. The Paper API is compile-only; no Paper server binary is bundled. Build with a JDK appropriate for the pinned wrapper. This environment has no JDK 8 installed, so a ForgeGradle execution failure caused by the missing Java 8 runtime must not be reported as runtime support.

## Scope and isolation

* `forge` contains a bounded legacy `SimpleNetworkWrapper` control channel, a common proxy boundary, and client-only render/texture classes.
* `forge` includes a dedicated `TileEntity`/TESR seam. It is a renderer scaffold only; no media decoder or frame transport is installed.
* `paper` contains only the server plugin-message adapter and uses the valid pre-1.13 `VIDSCREEN` channel alias. It sends bounded metadata/control bytes and never resolves, downloads, or relays media.
* Paper-to-Forge framing interoperability is not yet claimed: Forge `SimpleNetworkWrapper` adds loader framing, so a raw legacy transport adapter and exact integration fixture remain required.
* Shared domain/protocol sources are included directly so this lane is independent of the Java 25 root build.
* JavaCPP, JavaCV, FFmpeg natives, cookies, authorization headers, and server-side media bytes are intentionally absent.

This lane is **experimental**. A dependency resolution or Java compilation result is not runtime support. Dedicated-server classloading, render screenshots, reconnect/dimension/chunk lifecycle, native cleanup, two-client synchronization, and packaging gates remain open.

## Commands

```text
forge\\gradlew.bat build
paper\\gradlew.bat build
```

Run the Forge build with a real Java 8 installation for the legacy Gradle/ForgeGradle combination. The Paper build only compiles the plugin against the resolved API snapshot. Do not commit `run/`, Gradle caches, extracted natives, worlds, or generated binaries.
