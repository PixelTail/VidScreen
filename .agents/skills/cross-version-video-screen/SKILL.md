---
name: cross-version-video-screen
description: Develop this repository's synchronized Minecraft video-screen system across Paper/Purpur server plugins, Fabric/NeoForge server mods, and required Fabric/NeoForge client mods. Start with Minecraft 26.2, preserve explicit seams for older version lanes, and use this skill for architecture, networking, rendering, media, compatibility, security, build, and test work in this repository.
---

# Cross-version Minecraft Video Screen

## Mission

Build a synchronized in-world video-screen system with three product surfaces:

1. Paper/Purpur/Folia-compatible server plugin.
2. Fabric/NeoForge server mod for modded servers and integrated single-player.
3. Required Fabric/NeoForge client mod that resolves, decodes, renders, and plays media.

The server owns screen definitions, permissions, and authoritative playback state. Clients fetch media directly and render locally. Never relay video frames through the Minecraft server.

## Version policy

- Implement and verify Minecraft **26.2 first**.
- Minecraft 26.2 uses Java 25 and unobfuscated Mojang names.
- Fabric 26.2 must use the current non-remapping Fabric Loom path; do not introduce Yarn/intermediary assumptions into the 26.2 lane.
- NeoForge 26.2 must start from the current official generator/example and 26.2 primer.
- Paper 26.2 must use the current `26.2.build` API line and Java 25 toolchain.
- Pin exact loader, API, Gradle plugin, and dependency versions in a checked-in compatibility catalog after verifying them from official metadata. Never invent version coordinates.
- Port downward only after the 26.2 behavior and protocol conformance suite pass.

Planned compatibility lanes, in priority order:

1. `26.2` — Java 25; Paper/Purpur, Fabric, NeoForge.
2. `26.1` — Java 25; only after assessing the 26.1/26.2 rendering and networking delta.
3. `1.21.x` — Java 21; Paper/Purpur, Fabric, NeoForge as supported by their official toolchains.
4. `1.20.1` — Java 17; Paper/Purpur, Fabric, Forge; keep Forge separate from NeoForge.
5. `1.19.2` and `1.18.2` — Java 17 feasibility lanes.
6. `1.16.5` — Java 8 legacy lane if media natives and rendering hooks remain maintainable.
7. `1.12.2` — stretch lane, not an architectural constraint for the first release.

A listed lane is a target, not a support claim. Support begins only when its build, protocol, render, media, reconnect, and dedicated-server tests pass in CI.

## Repository boundaries

Keep Minecraft-independent code separate from platform code:

```text
shared/
  protocol/        Versioned wire messages and compatibility negotiation
  domain/          Screen geometry, playback state, media descriptors
  media-api/       Resolver/player abstractions with no Minecraft imports

platforms/
  mc26.2/
    client-common/
    client-fabric/
    client-neoforge/
    server-common/
    server-paper/
    server-fabric/
    server-neoforge/
```

Additional version lanes should be independent Gradle builds or carefully isolated source sets. Do not force incompatible Loom, ModDevGradle, ForgeGradle, Java, or mapping generations into one fragile buildscript.

The shared wire/domain artifacts should use the lowest practical Java bytecode level and avoid records/sealed classes if a future Java 8 lane must consume the same binary. Platform implementations may use their lane's Java version.

## Protocol rules

- Use the existing Minecraft connection for control messages where possible.
- Define a namespaced custom-payload protocol with an explicit integer protocol version.
- Start every connection with capability negotiation: product version, protocol versions, loader, supported codecs, maximum texture size, and optional features.
- Keep messages bounded and validate all lengths, counts, coordinates, URLs, and enum values before allocation or execution.
- Required message families:
  - hello/capabilities
  - clock request/response
  - full screen snapshot
  - screen upsert/delete
  - playback state/command
  - permission/error response
- The server sends timestamps and playback intent, never frames.
- Joining, changing dimension, respawning, and reconnecting must produce a fresh authoritative snapshot.
- Unknown fields or newer optional features must degrade safely; incompatible major protocol versions must fail with a clear message.

## Playback synchronization

Represent authoritative state with screen ID, monotonic revision, status, media position, effective server time, rate, loop mode, and source revision.

Clients estimate target media position from clock offset. Apply drift policy approximately as follows, tuning with measurements:

- Small drift: ignore.
- Medium drift: temporary bounded rate correction.
- Large drift or source revision change: seek.
- Periodically refresh clock and playback state without per-frame traffic.

Tests must use a fake clock. Avoid direct calls to wall-clock APIs in synchronization logic.

## Screen creation

The first release supports axis-aligned planar screens selected by two corners:

1. Select corner A and corner B.
2. Validate same world, bounded dimensions, and exactly one constant axis.
3. Derive facing, width, height, origin, and UV orientation.
4. Show a client-side wireframe preview.
5. Persist only after explicit confirmation.

Do not implement arbitrary quadrilaterals or rotation until axis-aligned behavior is complete and tested.

Render one quad/mesh and one dynamic texture per visible screen, not one entity or texture per block.

## Client rendering and media

- Decode off the render thread.
- Upload textures only on the supported render-thread path for the target version.
- Use a small bounded frame queue; drop late frames instead of blocking the client tick/render loop.
- Suspend decoding when the screen is out of range, unloaded, hidden, or in another dimension.
- Release native decoders, textures, buffers, temporary files, and audio sources deterministically on source change, disconnect, world unload, and shutdown.
- Start the 26.2 renderer with OpenGL unless an official platform requirement makes Vulkan unavoidable. Isolate graphics backends behind an interface.
- Position spatial audio at the screen center and expose per-client volume/mute controls.
- Enforce configurable resolution, bitrate, download-size, redirect, timeout, and cache limits.

Treat the media engine as a replaceable adapter. Before adopting WaterMedia, FFmpeg/JavaCPP, VLCJ, or another native stack, document:

- supported OS/architecture matrix;
- loader and Minecraft compatibility;
- native extraction/update behavior;
- license obligations and commercial restrictions;
- hardware acceleration behavior;
- crash isolation and resource cleanup.

Do not copy code from GPL projects unless this repository intentionally adopts a GPL-compatible license.

## Media source security

The first resolver should support direct HTTPS MP4/HLS only. Provider-specific resolvers such as Bilibili belong behind `MediaResolver` and come later.

Reject by default:

- `file:`, `jar:`, `ftp:`, and unrecognized schemes;
- localhost, link-local, private-network, and cloud metadata destinations;
- redirects from public hosts to blocked destinations;
- credentials, cookies, or arbitrary headers supplied by a server;
- unbounded playlists, manifests, subtitles, downloads, and decompression.

Never log signed URLs, cookies, tokens, or authorization headers. A future resolver service must issue short-lived scoped results rather than distributing account credentials to clients.

## Server rules

- All screen mutation requires an explicit permission check.
- Keep URL validation and screen bounds checks in shared server logic so every adapter behaves consistently.
- Persist screens atomically with schema versioning and backup/rollback support.
- Paper/Folia code must respect the platform scheduler and region-thread rules. Never block the main or region thread on network, disk, media resolution, or database work.
- Dedicated server modules must never load client rendering or native media classes.
- Players without a compatible client mod receive a clear capability message; do not pretend vanilla clients can render video.

## Development workflow

Before changing a platform lane:

1. Read its version catalog and build files.
2. Verify Java, Minecraft, loader/API, mappings, and Gradle plugin versions.
3. Use the `minecraft-dev` MCP for target-version source, mapping, hierarchy, and Mixin questions.
4. Use `plugdev` for the Paper/Purpur build-and-test loop once the Paper module exists.
5. Keep loader-specific imports out of shared modules.
6. Add or update protocol and conformance tests before adding another version lane.
7. Build the touched lane and run the smallest relevant test suite, then the cross-lane protocol suite.

Do not quote MCP output as authority without checking it against the target lane's dependency sources or official documentation.

## Test gates

A version/platform combination is supported only after passing:

- shared domain and wire-codec unit tests;
- malformed/bounded payload tests;
- fake-clock synchronization tests;
- screen geometry/property tests;
- dedicated-server classloading test;
- Paper or loader integration test;
- reconnect, dimension-change, chunk-unload, and source-change tests;
- client render smoke test with screenshot evidence;
- native resource leak/cleanup test;
- two-client synchronization test;
- packaging test on every advertised OS/architecture for native media dependencies.

Maintain a machine-readable compatibility matrix with statuses `planned`, `experimental`, and `supported`. Release notes must not claim a combination that CI does not test.

## Initial implementation order

1. Build skeleton and compatibility catalog for 26.2.
2. Pure-Java screen geometry and protocol codec.
3. Paper/Purpur server adapter with create/delete/list and persistence.
4. Fabric 26.2 client handshake and solid-color screen renderer.
5. NeoForge 26.2 client adapter with the same conformance fixtures.
6. Direct HTTPS MP4/HLS media adapter and spatial audio.
7. Playback synchronization and recovery.
8. Fabric/NeoForge server-mod adapters.
9. Harden security, native packaging, and end-to-end tests.
10. Port downward one compatibility lane at a time.

## Official references

- Fabric setup: https://docs.fabricmc.net/develop/getting-started/setting-up
- Fabric Loom: https://docs.fabricmc.net/develop/loom/
- Fabric 26.2 porting: https://docs.fabricmc.net/develop/porting/
- NeoForge 26.2 primer: https://docs.neoforged.net/primer/docs/26.2/
- NeoForge versioning: https://docs.neoforged.net/docs/gettingstarted/versioning/
- Paper project setup: https://docs.papermc.io/paper/dev/project-setup/
- Paper internals/mappings: https://docs.papermc.io/paper/dev/internals/
