# Development Plan

Status: active implementation; 26.2 compile/unit-test baseline is green, runtime support is not yet claimed  
Primary target: Minecraft 26.2  
Porting direction: newest to oldest  
Last updated: 2026-09-04

## 1. Product goal

Build an in-world synchronized video-screen system with a shared protocol and multiple platform adapters:

- Paper/Purpur server plugin, with Folia compatibility designed in rather than retrofitted;
- Fabric and NeoForge server mods;
- required Fabric and NeoForge client mods;
- two-corner, axis-aligned screen creation and preview;
- direct client-side media retrieval, decoding, texture rendering, and spatial audio;
- authoritative server-side screen definitions, permissions, playback state, persistence, and synchronization;
- a version strategy that proves 26.2 first and then ports downward without falsely claiming untested combinations.

The Minecraft server must never relay decoded frames or full media streams. It distributes bounded metadata and timestamps; clients fetch media independently.

## 2. Definition of success

The first supported release is complete when two modded clients can join a Minecraft 26.2 Paper/Purpur test server, receive the same persisted screen, play a direct HTTPS MP4 or HLS source on one world-space quad with spatial audio, remain acceptably synchronized, recover after reconnect/dimension change, and release all native/render resources without crashing either client or the dedicated server.

A platform/version combination is only `supported` when its exact build and runtime matrix passes CI and the end-to-end gates in section 9.

### 2.1 Validated implementation checkpoint

As of 2026-09-02:

- the shared domain, bounded protocol, persistence/service, URL policy, client playback coordinator, external FFmpeg fallback, in-process JavaCPP/JavaCV FFmpeg adapter, and yt-dlp provider resolver modules compile and their completed unit tests pass;
- experimental Fabric and NeoForge client-only media runtime companion JARs now package the standard JavaCPP 1.5.14 / JavaCV 1.5.14 / FFmpeg 8.1.2-1.5.14 artifacts for Windows x86-64 and Linux x86-64; each current universal runtime is approximately 60.7 MB;
- package inspection confirms both target native sets and the native player classes are present; an authorized Windows x86-64 test loads the bundled libraries successfully on Java 25, while Linux native loading and real playback remain unverified;
- the Paper 26.2 plugin, Fabric 26.2 mod, and NeoForge 26.2 mod previously built successfully with Java 25;
- an exact Minecraft 26.1.2 experimental lane now compiles and packages Paper, Fabric, NeoForge, and both client media runtimes with Java 25; it uses the same protocol major but distinct loader/version metadata and artifacts;
- the 26.1.2 port records real API seams rather than pretending binary compatibility: Fabric uses `LevelRenderEvents.END_EXTRACTION`, and both client texture adapters use the 26.1.2 `NativeImage#getPointer()` path instead of 26.2's `getPixelBytes()`;
- the Paper plugin has runtime-loaded on Paper 26.2 build 121, appeared in the plugin list, answered `/vidscreen list`, and stopped cleanly;
- client adapters prefer the bundled native runtime when installed and retain user-supplied external FFmpeg as a development fallback; the full Gradle build passes with this integration;
- both clients sample the server clock, reconcile authoritative playback, apply periodic drift correction, upload RGBA frames to dynamic textures, and submit textured world-space quads through their 26.2 render APIs;
- Bilibili, YouTube, and Twitch source resolution is implemented behind `MediaResolver` through an optional external yt-dlp executable; it remains experimental until fixture, legal, expiry/re-resolution, and live-stream tests pass;
- no platform/version combination is yet marked `supported`: runtime video, redirect/DNS-rebinding defenses, audio, resource-soak tests, two-client synchronization measurements, exact native-license audit, minimized per-platform packaging, and release packaging remain open;
- the 1.19.2 Java 17 feasibility lane has an isolated Gradle 8.6 scaffold for Fabric, Forge, and Paper with bounded legacy transport adapters; its exact local clean build passes, while branch CI and every runtime gate remain pending, so this is not a support claim.

## 3. Scope

### 3.1 First release

- Minecraft 26.2 and Java 25.
- Paper/Purpur plugin.
- Fabric 26.2 client mod.
- NeoForge 26.2 client mod.
- Fabric and NeoForge server-mod adapters after the plugin/client vertical slice works.
- Axis-aligned planar screens selected by two corners.
- Screen create, preview, list, inspect, delete, source, play, pause, seek, stop, loop, volume, and permission operations.
- Direct HTTPS MP4 and HLS sources.
- Optional Bilibili, YouTube, Twitch, and live-source resolution through a separately installed yt-dlp executable, capability-gated per client.
- Server-authoritative timestamp synchronization.
- One OpenGL render backend initially, isolated behind an interface.
- Spatial audio with per-client mute/volume.
- Atomic versioned persistence.
- Windows x64 and Linux x64 release targets, promoted only after exact native-runtime package tests pass.

### 3.2 Deferred

- Arbitrary rotated or non-rectangular screens.
- 4K as a release promise.
- Browser/MCEF playback.
- Vanilla-client video rendering.
- Proxies that forward video bytes through the Minecraft server.
- Public resolver services, accounts, cookies, DRM, or protected content.
- Forge and very old Minecraft versions until the modern architecture is stable.

## 4. Architecture

### 4.1 Build and source layout

Use independent version-lane builds so Java, Gradle plugins, mappings, and loader generations cannot destabilize each other:

```text
shared/
  domain/                          screen geometry and playback state
  protocol/                        versioned bounded wire codec
  media-api/                       resolver/player/frame abstractions
  media-ffmpeg/                    external ffmpeg/ffprobe development fallback
  media-ffmpeg-native/             in-process JavaCPP/JavaCV FFmpeg adapter
  media-ytdlp/                     optional provider-source resolver adapter
  client-core/                     playback reconciliation and drift correction
  server-core/                     service and atomic persistence
  testkit/                         fake clock and shared fixtures
platforms/
  mc26.2/
    media-runtime-fabric/           client-only Fabric native runtime companion
    media-runtime-neoforge/         client-only NeoForge native runtime companion
    server-paper/                  Paper/Purpur plugin
    fabric/                        Fabric client and server entrypoints
    neoforge/                      NeoForge client and server entrypoints
  mc26.1.2/                         exact experimental downward-port lane
    media-runtime-fabric/
    media-runtime-neoforge/
    server-paper/
    fabric/
    neoforge/
  mc1.21.x/
  mc1.20.1/
compatibility/
  targets.yaml                     planned/experimental/supported matrix
  protocol-fixtures/
docs/
  adr/
  protocol/
  security/
```

The root coordinates the currently compatible modern builds. Toolchain generations that cannot safely share Gradle, Java, Loom, ModDevGradle, or ForgeGradle run from independent `version/<minecraft>` Git branches as defined in `docs/VERSION_BRANCHES.md`; do not force them into one fragile buildscript.

### 4.2 Shared-code constraints

- `shared/domain`, `shared/protocol`, and `shared/media-api` have no Minecraft or loader imports.
- Keep their bytecode and language features compatible with the lowest practical future lane. If Java 8 reuse is retained as a goal, avoid records, sealed classes, and Java 9+ runtime APIs in these artifacts.
- Platform code may use the Java version required by its Minecraft lane.
- Use official/unobfuscated Mojang names for 26.2.
- Fabric 26.2 uses the current non-remapping Loom path.
- NeoForge 26.2 starts from the official current generator and 26.2 primer.
- Paper 26.2 uses the current `26.2.build` API coordinate and Java 25 toolchain.
- Exact dependency versions are copied from official metadata into a checked-in version catalog; none are guessed in the initial scaffold.

### 4.3 Server responsibilities

- Validate and persist screen geometry.
- Check permissions for every mutation.
- Validate media descriptors without resolving or downloading media on the tick thread.
- Own playback revision, status, source, loop, rate, base position, and effective server time.
- Negotiate client capabilities and protocol versions.
- Send screen snapshots on join, reconnect, respawn, and dimension change.
- Schedule Paper/Folia work through a platform scheduler abstraction.
- Keep native media and client-render classes out of dedicated-server classpaths.

### 4.4 Client responsibilities

- Negotiate protocol, codecs, and graphics limits.
- Resolve and fetch media subject to URL and resource policy.
- Decode off-thread into a bounded queue.
- Upload frames through the target-version render-thread API.
- Render one mesh/quad and one dynamic texture per visible screen.
- Play spatial audio from screen center.
- Correct clock/playback drift.
- Suspend and release resources on distance, visibility, chunk, dimension, source, connection, and shutdown transitions.

### 4.5 Protocol

Use the Minecraft connection and custom payloads for control data. Define an explicit major/minor protocol version and bounded message codecs.

Initial message families:

1. `client_hello` / `server_hello`
2. `clock_request` / `clock_response`
3. `screen_snapshot`
4. `screen_upsert`
5. `screen_delete`
6. `playback_state`
7. `playback_command`
8. `operation_result`

Each mutable object carries a monotonic revision. New clients may ignore unknown optional fields in the same major version; incompatible major versions fail with an actionable message.

### 4.6 Synchronization

Use a fake-clock-testable algorithm:

- server state contains media position, effective server time, playback rate, status, loop, source revision, and state revision;
- client estimates server clock offset and computes target media position;
- small drift is ignored;
- medium drift uses bounded temporary rate correction;
- large drift or source revision change performs seek;
- lightweight heartbeats refresh clock/state every few seconds;
- no per-frame network messages.

Thresholds are selected from measurements, not hard-coded from the planning document.

## 5. Compatibility strategy

### 5.1 Target waves

| Wave | Minecraft | Java | Planned server plugin | Planned server mods | Planned client mods | Initial status |
|---|---|---:|---|---|---|---|
| A | 26.2 | 25 | Paper/Purpur; Folia validation | Fabric, NeoForge | Fabric, NeoForge | primary |
| B | 26.1.2 | 25 | Paper/Purpur | Fabric, NeoForge | Fabric, NeoForge | experimental compile/package lane |
| C | 1.21.x | 21 | Paper/Purpur | Fabric, NeoForge | Fabric, NeoForge | planned |
| D | 1.20.1 | 17 | Paper/Purpur | Fabric, Forge | Fabric, Forge | planned |
| E | 1.19.2, 1.18.2 | 17 | Paper-family feasibility | Fabric, Forge | Fabric, Forge | feasibility |
| F | 1.16.5 | 8 | Paper/Spigot feasibility | Fabric, Forge | Fabric, Forge | stretch |
| G | 1.12.2 | 8 | Spigot/Paper legacy feasibility | Forge | Forge | stretch |

The table is a roadmap, not a compatibility claim.

### 5.2 Down-port rule

For every lower lane:

1. Generate a clean official loader/plugin scaffold.
2. Record Java, mappings, loader, API, Gradle plugin, and native-media constraints.
3. Compile shared artifacts unchanged if possible.
4. Implement only platform/version adapters.
5. Replay wire-protocol fixtures from newer lanes.
6. Pass dedicated-server classloading and two-client synchronization tests.
7. Publish as `experimental` before promoting to `supported`.

If a lane requires weakening URL security, protocol bounds, cleanup guarantees, or testability, it remains unsupported rather than contaminating newer lanes.

## 6. Media-engine decision and remaining gate

ADR 0005 supersedes the external-process spike as the intended default installation path. The 26.2 client now has an in-process `MediaPlayer` adapter using JavaCPP 1.5.14, JavaCV 1.5.14, and the standard JavaCPP Presets FFmpeg 8.1.2-1.5.14 artifacts. Separate client-only Fabric and NeoForge runtime companion JARs carry Windows x86-64 and Linux x86-64 native libraries, so normal players do not install or configure `ffmpeg` or `ffprobe`. The external process adapter remains a development fallback until the native path clears its gates.

The current companion packages are broad upstream integration artifacts, not final minimized releases. Each loader-specific universal runtime is approximately 60.7 MB and currently contains both target operating systems. Direct MP4/HLS does not require yt-dlp; optional provider extraction remains separately capability-gated.

Release promotion still requires:

- direct MP4 and HLS runtime playback evidence on Fabric and NeoForge;
- Windows x64 and Linux x64 native-load and package evidence;
- a reproducible minimized non-GPL/nonfree FFmpeg build and exact third-party license audit;
- per-platform artifacts so clients download only their operating-system/architecture runtime;
- spatial audio output through a bounded PCM path;
- redirect and DNS-rebinding enforcement at every actual media, manifest, segment, and key request;
- native cancellation, startup-size, memory, cleanup, crash, and repeated-playback tests;
- hardware acceleration measurements after the software baseline is correct.

Keep platform code behind `MediaPlayer`, `VideoFrameSink`, and future `AudioSink` interfaces. The runtime companion must remain absent from dedicated-server installations, and no compatibility lane may be promoted while native loading or actual connection-boundary security is untested.

## 7. Security requirements

### 7.1 URL policy

Allow direct HTTPS media by default. Reject:

- `file:`, `jar:`, `ftp:`, and unknown schemes;
- loopback, private, link-local, multicast, and cloud metadata destinations;
- public-to-private redirects;
- server-provided cookies, authorization headers, or arbitrary headers;
- unbounded redirects, manifests, playlists, subtitles, files, and decompression.

Resolve every redirect target again against the address policy. Do not log signed URLs or credentials.

### 7.2 Protocol and permissions

- Bound every string, collection, payload, coordinate range, screen area, and allocation before use.
- Require explicit permissions for screen creation, mutation, source changes, and playback control.
- Do not execute server console commands from the product protocol.
- Reject unsupported clients before sending screen/source state.
- Persist atomically and retain a recoverable previous snapshot.

### 7.3 Native isolation

- Never load media natives on a dedicated server.
- Verify downloaded native artifacts by checksum/signature.
- Extract to a versioned private cache.
- Prevent path traversal and DLL search-order abuse.
- Release decoder, audio, buffer, texture, and temporary-file resources deterministically.

## 8. Implementation phases

### Phase 0 — Build and decision foundation

Tasks:

- choose project name, Maven group, mod/plugin ID, and repository license;
- create root Gradle composite, build conventions, wrapper, dependency verification, and version catalogs;
- create `compatibility/targets.yaml`;
- add CI skeleton for Windows and Linux with JDK 25;
- create ADRs for build isolation, protocol transport, mappings, and license posture;
- verify exact 26.2 Paper, Fabric, NeoForge, Loom, ModDevGradle, and Gradle coordinates from official metadata;
- add formatting, static analysis, unit-test, and dependency-locking tasks.

Exit criteria:

- empty 26.2 platform modules compile;
- dedicated-server modules cannot resolve client/native packages;
- dependency verification and lockfiles are committed;
- no guessed or floating dependency versions.

### Phase 1 — Domain, geometry, and protocol

Tasks:

- implement screen ID, dimension ID, axis-aligned geometry, facing, fit mode, and view distance;
- implement two-corner validation and UV orientation;
- implement playback/media state and monotonic revisions;
- implement bounded major/minor wire codec;
- implement capability negotiation and fake-clock synchronization core;
- build golden protocol fixtures and malformed-input/property tests.

Exit criteria:

- shared modules have no Minecraft imports;
- codecs round-trip golden fixtures;
- fuzz/property tests cannot trigger unbounded allocation;
- geometry handles all three planes and reversed corner selection.

### Phase 2 — 26.2 server vertical slice

Tasks:

- implement `server-common` screen service, permissions, commands, persistence, and snapshot generation;
- implement Paper/Purpur 26.2 adapter and Folia-aware scheduler seam;
- add wand/two-corner selection, client preview request, confirm, list, inspect, and delete;
- add client capability tracking and no-mod message;
- persist through atomic replace with schema version and backup.

Exit criteria:

- create/delete survives restart;
- every mutation has permission tests;
- malformed screen/source inputs are rejected;
- no disk/network work blocks the main or region thread.

### Phase 3 — 26.2 Fabric client and static renderer

Tasks:

- implement handshake and custom-payload adapter;
- receive full snapshot and incremental updates;
- render solid-color/checkerboard texture on one quad;
- add preview outline and debug overlay;
- implement distance, visibility, chunk, dimension, disconnect, and shutdown lifecycle;
- add screenshot smoke tests and render-resource counters.

Exit criteria:

- Fabric client displays the correct screen position/facing/UV against Paper/Purpur;
- reconnect and dimension transitions restore state;
- no texture/buffer growth after repeated create/delete/unload cycles.

### Phase 4 — 26.2 NeoForge parity and server mods

Tasks:

- implement NeoForge client network/render adapters against the same shared tests;
- implement Fabric and NeoForge server adapters;
- ensure integrated single-player uses the same server-authoritative state model;
- run cross-combinations allowed by the protocol and document unsupported combinations.

Exit criteria:

- Fabric and NeoForge clients pass identical protocol/geometry fixtures;
- dedicated Fabric and NeoForge servers start without client/native classes;
- persistence and commands behave consistently across adapters.

### Phase 5 — Media engine and direct sources

Tasks:

- complete the media-engine spike and ADR;
- implement HTTPS policy, resolver SPI, redirects, size/time limits, and cache keys;
- implement MP4 and HLS adapters;
- integrate bounded decode queue and render-thread upload;
- add source change, pause, resume, seek, loop, end-of-stream, and failure states;
- implement spatial audio and per-client volume/mute.

Exit criteria:

- direct MP4 and HLS play on Fabric and NeoForge 26.2 clients;
- decode does not block client tick/render threads;
- resources return to baseline after repeated playback cycles;
- blocked URL/redirect cases pass security tests.

### Phase 6 — Synchronization and recovery

Tasks:

- implement clock sampling and offset estimation;
- implement drift measurement, rate correction, and seek policy;
- handle late join, buffering, reconnect, source revision, lag spikes, pause, and loop boundary;
- add two-client automated measurement harness.

Exit criteria:

- measured drift remains within the release budget under normal conditions;
- clients converge after induced lag/reconnect;
- no per-frame synchronization traffic.

### Phase 7 — Hardening and 26.2 release candidate

Tasks:

- run malformed protocol, URL, cache, native, and persistence threat tests;
- add metrics/debug bundle without sensitive URLs;
- test Windows and Linux packaging; add macOS only if the media-engine matrix passes;
- run Paper, Purpur, Fabric dedicated, NeoForge dedicated, and integrated-server suites;
- document installation, client requirements, commands, permissions, limits, and troubleshooting.

Exit criteria:

- all 26.2 target combinations are explicitly marked supported or excluded;
- reproducible artifacts and checksums are produced;
- license notices include every native/media component;
- release notes match the machine-readable compatibility matrix.

### Phase 8 — Provider resolver hardening

Tasks:

- validate the existing optional yt-dlp adapters for Bilibili, YouTube, Twitch, and live streams against legally redistributable fixtures;
- implement signed-URL expiry and bounded re-resolution without exposing URLs in logs;
- capture required non-secret request headers without accepting server-supplied cookies or authorization;
- define executable compatibility/update behavior and provider-specific failure states;
- keep provider capability bits disabled unless both the decoder and resolver executable are locally available;
- never distribute account credentials to clients.

Exit criteria:

- provider failure does not affect direct sources or the player core;
- resolvers can be updated independently of rendering/protocol code;
- live and VOD expiry/recovery tests pass;
- terms/licensing review is recorded.

### Phase 9 — Downward ports

Minecraft 26.1.2 is now present as the first exact downward compile/package lane; see ADR 0006. It remains experimental until its runtime gates pass. Continue with 1.21.x only after the 26.2 runtime vertical slice remains the reference behavior and the 26.1.2 runtime delta is measured.

Port subsequent waves C through G. Each lane gets its own feasibility note, build pins, adapter changes, CI matrix, and promotion gate. Prioritize versions with active server populations and maintainable native media support rather than maximizing a number on the project page.

## 9. Test and release gates

Every supported combination must pass:

1. domain and wire-codec unit tests;
2. malformed/bounded payload tests;
3. fake-clock synchronization tests;
4. screen geometry/property tests;
5. dedicated-server classloading test;
6. loader/plugin integration test;
7. join/reconnect/respawn/dimension/chunk/source lifecycle tests;
8. client screenshot/render smoke test;
9. native cleanup and repeated-playback soak test;
10. two-client synchronization test;
11. persistence migration and interrupted-write recovery test;
12. packaging test on every advertised OS/architecture.

Compatibility statuses:

- `planned`: no distributable claim;
- `experimental`: artifacts may exist, but support is incomplete;
- `supported`: exact matrix is continuously tested and documented.

## 10. Tooling installed for the project

### Project Skill

`.claude/skills/cross-version-video-screen/SKILL.md`

It enforces the architecture, newest-to-oldest version policy, security boundaries, and support gates in this plan.

### Project MCPs

`.mcp.json` enables only:

- `minecraft-dev` using `@mcdxai/minecraft-dev-mcp@1.3.0`;
- `plugdev` using `@plugdev/mcp@0.4.0`.

Packages are pinned in `package.json`/`package-lock.json`. Local Node 22 and Temurin JDK 25 runtimes are stored under ignored `.tools/`; they do not modify global runtimes.

The current `minecraft-dev` dependency graph contains the unfixed `adm-zip` GHSA-xcpc-8h2w-3j85 memory-allocation advisory. It is restricted to trusted official Minecraft artifacts and trusted Mod JARs until upstream fixes or replaces the dependency; MCP dependencies are development-only and must never enter shipped game artifacts. See `docs/TOOLING.md`.

Use `minecraft-dev` to verify 26.2 source/mappings/render/network details before implementation. Use `plugdev` after the Paper module exists.

## 11. Decision gates before Phase 0 implementation

The following choices must be recorded in ADRs, but they do not block repository/tool bootstrap:

1. project display name, mod/plugin ID, Maven group, and package namespace;
2. open-source license and whether commercial redistribution is intended;
3. release OS/architecture scope;
4. media-engine selection after the spike;
5. exact first-release server matrix: Paper/Purpur required, Folia support or experimental;
6. whether Bilibili is required for the first provider-resolver milestone.

## 12. Immediate next action

Run each 26.2 client with its companion runtime installed. Exercise a controlled direct HTTPS MP4 fixture first: frame/UV verification, pause, seek, rate correction, reconnect, and repeated cleanup. Replay the same validated fixture and lifecycle sequence on the new 26.1.2 Fabric/NeoForge clients before expanding to 1.21.x. Next add the VidScreen-owned connection boundary, HLS fixture coverage, spatial audio, Linux CI runtime loading, per-platform minimized packages, and a two-client Paper synchronization measurement. Do not mark any combination supported until those gates and the exact native-license audit pass.
