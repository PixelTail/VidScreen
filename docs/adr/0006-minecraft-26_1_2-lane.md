# ADR 0006: Add the Minecraft 26.1.2 Compatibility Lane

- Status: Accepted as an experimental compile/package lane
- Date: 2026-09-04

## Context

VidScreen starts with Minecraft 26.2 and ports downward only through explicit, independently testable platform lanes. The first downward target is the latest 26.1 patch rather than assuming one binary works across the whole 26.1 family.

Minecraft 26.1.2 still uses Java 25 and unobfuscated Mojang names, so the shared domain, protocol, server, playback, resolver, and media-engine modules can remain unchanged. Loader and platform APIs are pinned separately and the resulting artifacts identify 26.1.2 in their metadata and client handshake.

## Decision

Add `platforms:mc26.1.2` with independent Paper, Fabric, NeoForge, Fabric media-runtime, and NeoForge media-runtime modules.

Pin the lane to:

- Minecraft 26.1.2;
- Java 25;
- Fabric Loom 1.17.20;
- Fabric Loader 0.19.3;
- Fabric API 0.155.2+26.1.2;
- NeoForge 26.1.2.94;
- ModDevGradle 2.0.144;
- Paper API 26.1.2.build.74-stable.

Loom 1.17.20 and Fabric Loader 0.19.3 are intentionally shared with the 26.2 build to avoid loading incompatible versions of the same Gradle plugin in the current root multi-project build. Compatibility is accepted only because the exact 26.1.2 lane compiles and packages successfully. This does not claim that every 26.1 patch accepts the same artifact.

Continue to reuse protocol major 1. A 26.1.2 client reports `26.1.2` in its hello message, while capability negotiation remains media-feature based rather than Minecraft-version based.

## Platform deltas

The initial port exposed two concrete client API differences from 26.2:

1. Fabric API 26.1.2 registers end-of-extraction work through `LevelRenderEvents.END_EXTRACTION`; 26.2 moved that event to `LevelExtractionEvents.END_EXTRACTION`.
2. Minecraft 26.1.2 `NativeImage` does not expose `getPixelBytes()`. The 26.1.2 texture adapters obtain a bounded `ByteBuffer` from the public `getPointer()` via LWJGL `MemoryUtil.memByteBuffer`. The copy remains render-thread-only and validates frame/image dimensions before constructing the view.

NeoForge networking, extraction/submission events, dynamic texture creation/upload, and the server adapter otherwise compile unchanged against the pinned 26.1.2 lane.

## Consequences

- The root build now compiles two exact Minecraft lanes: 26.2 and 26.1.2.
- Every loader/version produces a distinct artifact; no JAR claims both Minecraft versions.
- Shared protocol/domain/media modules are reused without loader imports or protocol forks.
- Runtime companion JARs remain client-only and core Fabric/NeoForge JAR inspection confirms no JavaCPP/JavaCV/FFmpeg classes are included.
- Duplicated platform source is accepted at this stage because the two versions already require explicit API differences. Common behavior remains in shared modules rather than being hidden behind reflection or unchecked version assumptions.
- Root-build time and dependency-lock/verification metadata grow with each exact lane.

## Verification

Completed:

- Paper 26.1.2 compiles and packages against `paper-api:26.1.2.build.74-stable`;
- Fabric 26.1.2 main and client source sets compile against Fabric API 0.155.2+26.1.2;
- NeoForge 26.1.2 compiles and packages against NeoForge 26.1.2.94;
- both 26.1.2 media-runtime companion JARs build;
- the Windows x86-64 native-runtime load test passes through the 26.1.2 Fabric runtime test task;
- the complete two-lane Gradle build passes;
- dependency locks and SHA-256 verification metadata include the new lane;
- distribution JAR checksums verify;
- core Fabric and NeoForge JARs contain zero native-runtime entries.

Not completed, so the lane remains `experimental`:

- Paper/Purpur runtime startup;
- Fabric and NeoForge dedicated-server startup;
- Fabric and NeoForge client launch;
- real direct HTTPS MP4/HLS playback and screenshot evidence;
- spatial audio;
- secure media connection boundary;
- lifecycle/resource soak and two-client synchronization tests;
- Linux CI execution;
- one-file client packaging and final native-license/minimization work.
