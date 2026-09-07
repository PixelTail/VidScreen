# ADR 0005: Ship a Client-Only Native Media Runtime

- Status: Accepted for the 26.2 experimental implementation
- Date: 2026-09-02

## Context

The external-process spike in ADR 0004 proved the media, frame-queue, texture, rendering, and synchronization seams, but requiring every player to install `ffmpeg`, `ffprobe`, and optionally `yt-dlp` is not an acceptable normal installation path. VidScreen needs a self-contained client experience while keeping media bytes off the Minecraft server and native classes out of dedicated-server installations.

A decoder is still required. Pure-Java decoders can cover a narrow MP4/H.264 subset but do not provide the codec, HLS/live, audio, seeking, and platform breadth required by the product. WaterMedia v3 reduces integration work but introduces a PolyForm Strict dependency, a larger general-purpose native bundle, and unresolved network-policy integration. Operating-system media APIs would require unrelated Windows and Linux implementations.

JavaCPP 1.5.14, JavaCV 1.5.14, and the standard JavaCPP Presets FFmpeg 8.1.2-1.5.14 artifacts provide a practical in-process spike. The optional `ffmpeg-platform-gpl` artifact is not used. The current universal Windows x86-64 plus Linux x86-64 experimental runtime JAR is approximately 60.7 MB per loader before future minimization.

## Decision

- Keep `MediaPlayer` as the engine boundary.
- Add `shared:media-ffmpeg-native` as an in-process decoder adapter.
- Use JavaCV's `FFmpegFrameGrabber` over JavaCPP FFmpeg bindings for the initial native implementation.
- Request RGBA output, copy each reused native frame into the existing bounded latest-frame queue, preserve aspect ratio with opaque letterboxing, and pace frames from media timestamps off the render thread.
- Probe through the same decoder context; do not ship or invoke `ffmpeg.exe` or `ffprobe.exe` in the normal path.
- Package native code only in client runtime companion mods:
  - `vidscreen-media-runtime-fabric-26.2`
  - `vidscreen-media-runtime-neoforge-26.2`
- Include only Windows x86-64 and Linux x86-64 native artifacts in the first experimental package. Keep the runtime optional for the combined client/server platform JAR so dedicated servers do not install it.
- Prefer the bundled native runtime when present. Retain ADR 0004's external-process adapter as a development fallback until native runtime and crash/cleanup gates pass.
- Keep yt-dlp separate and optional. Direct HTTPS MP4/HLS must not depend on it.
- Continue advertising only capabilities available on the current client.
- Do not promote the runtime or any platform lane to `supported` until the license, security, runtime, packaging, and cleanup gates below pass.

## Security boundary

The current native spike still allows FFmpeg to open the validated HTTPS URL. Initial hostname/IP validation is not enough to prevent redirect-to-private-address behavior or DNS rebinding at the actual connection boundary.

Before support promotion, add a VidScreen-owned media gateway or custom AVIO boundary that:

- performs every remote HTTPS request itself;
- pins validated public addresses per connection;
- validates every redirect again;
- bounds response headers, ranges, manifests, segments, encryption keys, retries, and total bytes;
- rewrites all HLS child URIs to unguessable loopback URLs, or supplies the bytes through a custom input boundary;
- prevents the decoder from connecting to arbitrary remote destinations;
- never logs signed URLs, credentials, cookies, tokens, or authorization headers.

Until this exists, the native backend remains `experimental` and must not be described as SSRF-safe.

## Packaging and license gates

The current runtime uses upstream standard JavaCPP Presets artifacts as an integration spike. Before a release candidate:

1. Audit the exact native binaries and every bundled dependency; do not infer license posture from the Maven artifact name alone.
2. Verify that no GPL or nonfree build is included. Never add `ffmpeg-platform-gpl` accidentally.
3. Record complete FFmpeg configure flags, source revisions, checksums, notices, and source-availability obligations.
4. Prefer a reproducible VidScreen-minimized dynamic FFmpeg build with programs, encoders, devices, documentation, and unused codecs disabled.
5. Produce per-OS/per-architecture artifacts so a player downloads only the relevant native runtime.
6. Verify extraction paths and hashes and prevent DLL/shared-library search-order injection.
7. Measure compressed size and enforce a release budget rather than assuming the current spike size.

Apache-2.0 covers VidScreen source, not third-party native binaries. Their notices and redistribution terms remain independently applicable.

## Consequences

### Benefits

- Players do not install or configure FFmpeg/ffprobe.
- The normal client path has no shell or external command invocation.
- The player has direct access to decoded frames and can later receive PCM audio through an `AudioSink`.
- Pause, seek, preview-frame requests, and video pacing no longer require restarting a CLI process.
- Media runtime updates can be distributed separately from the Paper plugin and control protocol.
- Dedicated servers can omit the client runtime entirely.

### Costs and risks

- A native crash can terminate the Minecraft client JVM; the external fallback has better crash isolation.
- Native reads may not be immediately interruptible. I/O limits and deterministic cancellation need runtime tests.
- Upstream artifacts are broader than the planned minimized release runtime.
- The current implementation copies RGBA into managed memory once per submitted frame; zero-copy/GPU paths remain future optimization work.
- Spatial audio is not implemented yet.
- Provider URL extraction remains a separate maintenance and legal concern.
- Both Fabric and NeoForge runtime packages currently contain Windows and Linux natives; per-platform packaging remains a release task.
- Java 25 currently warns that JavaCPP calls restricted native-loading methods without `--enable-native-access=ALL-UNNAMED`; the tested target still loads successfully, but a launcher/module-compatible fix is required before a future Java release begins blocking the call.

## Verification status

Completed at this checkpoint:

- native adapter and RGBA letterbox-copy tests compile and pass;
- the authorized Windows x86-64 runtime-load test successfully loads the bundled JavaCPP/FFmpeg libraries on Java 25;
- the full Gradle build passes with the Fabric and NeoForge integrations preferring the companion runtime;
- Fabric and NeoForge client runtime JARs build;
- each universal runtime JAR is approximately 60.7 MB;
- package inspection confirms JavaCV, VidScreen's native player, and Windows/Linux `avcodec`, `avformat`, `avutil`, `swscale`, and `swresample` libraries are present;
- package inspection confirms the core Fabric and NeoForge JARs do not contain JavaCPP, JavaCV, FFmpeg, or VidScreen native-player classes.

Still required:

- Linux x64 native load test in CI;
- Fabric and NeoForge client launch with the companion runtime installed;
- direct MP4/HLS playback, pause, seek, rate, end-of-stream, and reconnect tests;
- native cleanup and repeated-playback soak tests;
- secure connection-boundary implementation;
- spatial audio;
- exact license audit and minimized reproducible native builds.
