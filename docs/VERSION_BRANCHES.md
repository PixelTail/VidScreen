# Version Branches

VidScreen keeps protocol and product behavior aligned while allowing incompatible Minecraft toolchains to live on independent Git branches.

## Branch model

| Branch | Minecraft | Java | Intended loaders/surfaces | Status |
|---|---|---:|---|---|
| `main` | newest integration baseline | 25 | shared protocol plus validated modern lanes | active |
| `version/26.2` | 26.2 | 25 | Paper/Purpur, Fabric, NeoForge | experimental |
| `version/26.1.2` | 26.1.2 | 25 | Paper/Purpur, Fabric, NeoForge | experimental |
| `version/1.21.1` | 1.21.1 | 21 | Paper/Purpur, Fabric, NeoForge | experimental |
| `version/1.20.1` | 1.20.1 | 17 | Paper/Purpur, Fabric, Forge | experimental |
| `version/1.19.2` | 1.19.2 | 17 | Paper/Purpur, Fabric, Forge | experimental |
| `version/1.18.2` | 1.18.2 | 17 | Paper/Purpur, Fabric, Forge | experimental |
| `version/1.16.5` | 1.16.5 | 8 runtime target | Paper-family, Fabric, Forge | experimental scaffold |
| `version/1.12.2` | 1.12.2 | 8 runtime target | Paper/Spigot, Forge | experimental scaffold |

A planned branch is not created or published as a compatibility claim. It is created when it contains at least a pinned, independently buildable scaffold or a checked-in feasibility result explaining why a surface cannot yet build.

## Rules

1. `main` owns protocol major/minor definitions, golden fixtures, security rules, and the authoritative plan.
2. Version branches consume the same protocol major and replay the same golden fixtures. A version branch must not silently fork message semantics.
3. Platform source may be copied when Minecraft APIs differ. Do not hide incompatible render, networking, loader, mapping, or scheduler APIs behind reflection merely to reduce files.
4. Backport shared fixes by cherry-picking focused commits. Do not merge an old version branch wholesale into a newer one.
5. Each branch pins its own Minecraft, Java, Gradle, loader/API, mappings, and plugin coordinates and carries its own dependency locks or equivalent reproducibility metadata.
6. Branch CI builds only the surfaces declared by that branch. Experimental workflows use deliberate manual dispatch: trigger them when clean or cross-platform coverage adds useful confidence, including when an exact toolchain is unavailable locally. Use only standard runners covered by free public-repository Actions, then wait for and inspect the result; branch pushes do not start Actions automatically.
7. Client media bytes remain off the Minecraft server in every branch. Native media and client renderer classes remain absent from dedicated-server artifacts.
8. `planned`, `experimental`, and `supported` retain their definitions from `compatibility/targets.yaml`. A branch name or successful compile does not imply runtime support.

## Release flow

- Develop shared protocol/security fixes on `main` first.
- Create or update a version branch from the latest compatible shared baseline.
- Pin and compile the exact platform lane.
- Run protocol fixtures, dedicated-server loading, client rendering, real media, reconnect/lifecycle, native cleanup, and synchronization gates.
- Publish artifacts only for the exact tested version and loader. Tag releases with both product and Minecraft version, for example `v0.1.0-mc26.2`.

## Current repository

The GitHub repository is `https://github.com/PixelTail/VidScreen`. All version branches in the table are published independently. Their Gradle, mappings, loader, rendering, networking, and Java constraints are not interchangeable with 26.x; checking out `main` does not include the older branch source trees.

### Verified build checkpoint (2026-09-07)

| Lane | Exact tested commit | Successful Actions run | Build scope |
|---|---|---|---|
| 26.2 / 26.1.2 | `ed6f77e` | [34137909502](https://github.com/PixelTail/VidScreen/actions/runs/34137909502) | Root build and tests, Windows/Linux, Java 25 |
| 1.21.1 | `32acce3` | [34141874572](https://github.com/PixelTail/VidScreen/actions/runs/34141874572) | Paper/Fabric/NeoForge and client runtime packaging, Linux, Java 21 |
| 1.20.1 | `7f58a09` | [34144307127](https://github.com/PixelTail/VidScreen/actions/runs/34144307127) | Shared tests, Paper/Fabric/Forge builds, Linux, Java 17 |
| 1.19.2 | `58cf4b8` | [34111464983](https://github.com/PixelTail/VidScreen/actions/runs/34111464983) | Exact lane build, Java 17 |
| 1.18.2 | `86d057e` | [34111605266](https://github.com/PixelTail/VidScreen/actions/runs/34111605266) | Fabric/Paper and isolated Forge builds, Java 17 |
| 1.16.5 | `d64bfcb` | [34158785906](https://github.com/PixelTail/VidScreen/actions/runs/34158785906) | Fabric/Paper/Forge builds, Linux; Java 17 build JVM, Java 8 Forge compiler |
| 1.12.2 | `f4f1e7b` | [34158692032](https://github.com/PixelTail/VidScreen/actions/runs/34158692032) | Forge build/reobfuscation on Java 8; Paper build/tests on Java 17 with Java 8 target |

Later documentation and manual-trigger-policy commits do not automatically rerun CI; the table identifies tested code, not an assertion that every later branch head was run. All lanes remain **experimental**. Purpur, Folia, actual video/audio, client rendering, dedicated-server startup, lifecycle cleanup, and two-client synchronization require their own exact runtime evidence. In particular, Java 8 legacy media is disabled and 1.12.2 Paper-to-Forge channel framing interoperability remains open.
