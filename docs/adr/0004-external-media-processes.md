# ADR 0004: Decode Through External FFmpeg Processes

- Status: Superseded as the default by ADR 0005; retained as a development fallback
- Date: 2026-09-01
- Superseded: 2026-09-02

## Context

VidScreen needs MP4/HLS decoding, frame extraction, seeking, rate control, and eventually spatial audio on Windows x64 and Linux x64. Embedding a native binding would couple loader artifacts to native extraction and redistribution obligations. Many broadly distributed FFmpeg builds are GPL configurations, while VidScreen source is Apache-2.0. Decoder crashes and malformed media must not crash a dedicated server or cause client/native classes to load there.

Provider pages also require a replaceable resolver. Reimplementing Bilibili, YouTube, and Twitch extraction in the renderer would tightly couple volatile provider behavior to Minecraft versions.

## Decision

- Keep executable bytes outside VidScreen's mod/plugin JARs.
- Discover user-supplied `ffmpeg` and `ffprobe` through explicit system properties, environment variables, or `PATH`.
- Launch processes directly with `ProcessBuilder`; never invoke a shell.
- Probe metadata with bounded output and time limits.
- Decode video to a fixed-size raw RGBA stdout stream and feed a one-frame latest-value queue.
- Drain stderr without logging it because diagnostics may contain signed media URLs.
- Restrict FFmpeg input protocols to `crypto,https,tls,tcp` for the current adapter.
- Keep media classes out of dedicated-server entrypoints and packaged server-plugin artifacts.
- Discover optional user-supplied yt-dlp separately. Use it only behind provider-specific, HTTPS-host-bound `MediaResolver` instances.
- Advertise direct/provider capabilities only when all required local executables are discovered.
- Do not bundle or auto-download FFmpeg, ffprobe, or yt-dlp until checksums, source, update policy, OS/architecture packaging, and license notices are approved and tested.

## Rejected alternatives

### Embedded FFmpeg/native binding

Deferred because it adds native extraction, loader, crash, artifact-size, and redistribution work before the rendering and protocol vertical slice is proven.

### Sending media through the Minecraft server

Rejected because it violates the product boundary, multiplies bandwidth by client count, and puts untrusted media on the server path.

### Browser/MCEF playback

Deferred because browser distribution and GPU/lifecycle complexity exceed the first vertical slice.

### Provider logic inside platform renderers

Rejected because provider behavior must update independently of Minecraft rendering and loaders.

## Consequences

- Users currently install and update the executables themselves.
- Executable process arguments can be visible to local operating-system process inspection; signed URLs are therefore not protected from a user who controls the client machine.
- External process isolation reduces JVM-native coupling but is not an OS sandbox. Only maintained executable versions should be used.
- Temporary drift-rate correction is disabled for players that cannot change rate without restarting; large drift still seeks.
- Provider extraction remains experimental. Signed-URL expiry, required non-secret headers, and live recovery are not complete.
- Spatial audio needs a separate bounded PCM path and Minecraft audio-source adapter.

## Security work required before support promotion

- Validate every redirect and resolved address, including HLS playlists, segments, keys, and provider stream URLs.
- Prevent public-to-private redirects and DNS rebinding at the actual connection boundary; initial URI validation alone is insufficient.
- Bound playlist depth, segment count, response size, decode dimensions, process count, and retry behavior.
- Add timeout, cancellation, malformed-media, process-leak, and repeated-playback tests.
- Record exact accepted executable versions and redistribution obligations for Windows x64 and Linux x64.

## Verification

The adapter, discovery logic, provider resolver, bounded frame queue, client coordinator, and Fabric/NeoForge texture upload paths compile and their current unit tests pass. No real-media runtime or packaging gate has passed yet, so every related compatibility entry remains `experimental`.
