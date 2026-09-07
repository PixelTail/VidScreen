# 1.20.1 feasibility record

Status: experimental; no support claim.

## Verified

- The requested coordinates are available from official repositories: Fabric Loom 1.6.12, Fabric Loader 0.19.5, Fabric API 0.92.12+1.20.1, Yarn 1.20.1+build.10:v2, ForgeGradle 6.0.54, Forge 1.20.1-47.4.23, and Paper API 1.20.1-R0.1-SNAPSHOT.
- ForgeGradle 6.0.54 was verified from the official Forge Maven metadata and module file rather than inferred from a third-party mirror.
- This lane has its own Gradle 8.8 wrapper, Java 17 toolchain, dependency locking, and source roots. It does not add the lane to the Java 25 root settings file.
- The exact locked Java 17 command `:shared-core:test :paper:build :fabric:build :forge:build` passed on 2026-09-07. Shared-core tests passed (37 tests), and all three platform artifacts compiled and packaged.
- Server adapters send only bounded protocol payloads. No media resolver, frame queue, native runtime, or renderer is on a dedicated-server dependency path.
- Fabric and Forge client adapters have explicit 1.20.1 networking and render/texture seams instead of reusing 26.x classes through reflection.

## Remaining blockers

- Platform-specific malformed-payload tests, dedicated Paper/Fabric/Forge server startup/classloading, client startup, render screenshots, reconnect/dimension/chunk lifecycle, two-client synchronization, and OS packaging have not been run in CI.
- No 1.20.1 JavaCPP/native companion is supplied in this branch. The client fallback requires locally installed `ffmpeg` and `ffprobe`; native cleanup and real playback gates are therefore not passed.
- The Paper API is a historical snapshot coordinate. It is compile-verified only when the exact repository artifact resolves; no runtime compatibility is inferred from the API jar.
- Shared server persistence was missing from the starting repository and is now implemented as a Java 8 `ScreenRepository`/`FileScreenRepository`/`ScreenService` seam. Its migration and interrupted-write tests are still required.

The lane remains experimental until each gate in `compatibility.yaml` is tested against Minecraft 1.20.1 and the exact loader/plugin pins.
