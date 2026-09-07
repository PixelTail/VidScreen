# Development Tooling

## Scope

All Minecraft-specific development tooling in this repository is project-scoped:

- `.claude/skills/cross-version-video-screen/SKILL.md`
- `.mcp.json`
- `.claude/settings.json`
- pinned MCP packages in `package.json` and `package-lock.json`
- ignored portable runtimes under `.tools/`

No user-level or global Claude Code, Node, or Java configuration is modified by the bootstrap script.

## Pinned runtimes

| Runtime | Version | Source | SHA256 |
|---|---|---|---|
| Node.js Windows x64 | 22.23.2 | `nodejs.org/dist` | `1177b4137ba5adaa56354ae40f1080c7450e8ae09cecb47da459d1c52ac99f97` |
| Eclipse Temurin JDK Windows x64 | 25.0.4.1+1 LTS | Adoptium GitHub release | `00c847d804f4a78e9f04f2683faf14fed898535b177b7fc704486cb0284e9283` |

Run `pwsh -File scripts/bootstrap-tools.ps1` to reproduce the local installation.

## Pinned MCPs

| MCP | Version | Purpose |
|---|---:|---|
| `@mcdxai/minecraft-dev-mcp` | 1.3.0 | Target-version source, mappings, hierarchy, JAR, and Mixin analysis |
| `@plugdev/mcp` | 0.4.0 | Paper/Purpur build, deployment, server, log, and test loop |

Both servers were connection-tested in Claude Code on 2026-09-01.

## Client media runtime

Normal VidScreen playback is moving to client-only companion mods rather than user-installed FFmpeg programs:

- `vidscreen-media-runtime-fabric-26.2`
- `vidscreen-media-runtime-neoforge-26.2`

The current experimental packages contain JavaCPP 1.5.14, JavaCV 1.5.14, and the standard JavaCPP Presets FFmpeg 8.1.2-1.5.14 artifacts for Windows x86-64 and Linux x86-64. The optional `ffmpeg-platform-gpl` artifact is not declared. Both operating systems are currently present in each approximately 60.7 MB universal runtime; final releases should be minimized and split by OS/architecture.

The runtime removes normal-player requirements for `ffmpeg.exe` and `ffprobe.exe`. It remains experimental until native loading, real playback, cleanup, connection-boundary security, Linux CI, reproducible-build, and exact license gates pass. Apache-2.0 covers VidScreen source, not the bundled native dependencies. See `docs/adr/0005-client-native-media-runtime.md`.

### Development fallback and provider resolver

The original external-process adapter remains available when the companion runtime is absent. It discovers user-supplied executables in the following order:

| Executable | Required for | Explicit property | Environment variable | Fallback |
|---|---|---|---|---|
| `ffmpeg` | development video-decoder fallback | `-Dvidscreen.ffmpeg=<path>` | `VIDSCREEN_FFMPEG` | executable on `PATH` |
| `ffprobe` | development metadata-probe fallback | `-Dvidscreen.ffprobe=<path>` | `VIDSCREEN_FFPROBE` | executable on `PATH` |
| `yt-dlp` | optional Bilibili/YouTube/Twitch resolution | `-Dvidscreen.ytdlp=<path>` | `VIDSCREEN_YTDLP` | executable on `PATH` |

A client advertises direct MP4/HLS capability when the bundled native runtime loads or both fallback FFmpeg executables are found. Provider capability bits are added only when yt-dlp is also found. Direct MP4/HLS does not require yt-dlp. Missing media backends disable playback without preventing the control mod from loading.

No native runtime or executable version is approved for supported release redistribution yet. Users testing either experimental path accept the third-party terms. Do not place generated binaries or native extracts in Git.

## Known dependency advisory

`npm audit --omit=dev` currently reports two high-severity paths to the same advisory:

- `adm-zip < 0.6.0`
- GHSA-xcpc-8h2w-3j85: a crafted ZIP can trigger an approximately 4 GB memory allocation
- introduced through `@mcdxai/minecraft-dev-mcp`
- no upstream fix was available at installation time

Until the dependency is fixed or replaced:

- use `minecraft-dev` only with official Minecraft artifacts and trusted Mod JARs;
- do not ask it to inspect JAR/ZIP files obtained from untrusted users;
- keep it local and never expose its HTTP transport;
- rerun `npm audit` before dependency upgrades and releases;
- treat unexpected memory growth as a tooling process failure and terminate the MCP process.

This advisory affects development tooling, not a shipped game artifact: MCP dependencies must never be bundled into plugin or mod JARs.
