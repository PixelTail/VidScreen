# VidScreen

A synchronized, cross-version Minecraft in-world video-screen project.

The product is planned as:

- a Paper/Purpur/Folia server plugin;
- Fabric and NeoForge server mods;
- required Fabric and NeoForge client mods;
- shared, versioned screen and playback protocol;
- client-side media retrieval, decoding, rendering, and spatial audio.

Development starts with Minecraft 26.2 and ports downward only after each version lane passes the compatibility test gates.

## Current status

The Minecraft 26.2 shared protocol/domain/media stack and the previously validated Paper, Fabric, and NeoForge modules use Java 25. The Paper plugin has loaded successfully on Paper 26.2 build 121 and passed plugin discovery plus `/vidscreen list`. Exact Minecraft 26.1.2 and 1.21.1 experimental lanes now have isolated platform scaffolds; the 1.21.1 lane pins Java 21 bytecode, Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1, Fabric Loom 1.10.5 with Yarn 1.21.1+build.3, NeoForge 21.1.250, ModDevGradle 2.0.146, and Paper API 1.21.1-R0.1-SNAPSHOT.

VidScreen has an experimental in-process JavaCPP/JavaCV FFmpeg adapter and separate Fabric/NeoForge client media-runtime companion JARs containing Windows x86-64 and Linux x86-64 natives; each current universal runtime is approximately 60.7 MB. The clients prefer this bundled runtime when installed and retain the external FFmpeg adapter as a development fallback.

All combinations remain **experimental** or **planned**. The full 26.2 + 26.1.2 Gradle build and Windows x86-64 native-load tests pass; the 1.21.1 lane has now passed its exact local compile/package tasks, but no 1.21.1 runtime support is claimed. Real-media client tests, Linux native loading, spatial audio, actual connection-boundary redirect/DNS-rebinding enforcement, lifecycle/resource soak tests, two-client synchronization measurements, one-file/minimized per-platform client packaging, exact native-license audit, Purpur/Folia validation, CI runs, and older-version ports remain open.

See:

- [`docs/PLAN.md`](docs/PLAN.md) — authoritative implementation sequence and validated checkpoint;
- [`docs/VERSION_BRANCHES.md`](docs/VERSION_BRANCHES.md) — branch-per-version policy and release flow;
- [`compatibility/targets.yaml`](compatibility/targets.yaml) — machine-readable support status;
- [`docs/adr/`](docs/adr/) — architecture decisions;
- [`docs/TOOLING.md`](docs/TOOLING.md) — reproducible tooling, bundled media runtime, fallback executable discovery, and advisories.

## Claude Code project tooling

- Skill: `.claude/skills/cross-version-video-screen/SKILL.md`
- MCP config: `.mcp.json`
- MCP packages: `package.json` and `package-lock.json`

The configured MCP servers are project-scoped:

- `minecraft-dev` — target-version source, mappings, hierarchy, JAR, and Mixin analysis.
- `plugdev` — Paper/Purpur plugin build, deployment, server, and test loop once the plugin module exists.

Install the pinned project-local Node 22, Temurin JDK 25, and MCP dependencies on Windows with:

```powershell
pwsh -File scripts/bootstrap-tools.ps1
```

The archives are checksum-verified and extracted under ignored `.tools/`; no global Node or Java installation is changed.
