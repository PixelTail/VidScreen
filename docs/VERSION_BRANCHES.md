# Version Branches

VidScreen keeps protocol and product behavior aligned while allowing incompatible Minecraft toolchains to live on independent Git branches.

## Branch model

| Branch | Minecraft | Java | Intended loaders/surfaces | Status |
|---|---|---:|---|---|
| `main` | newest integration baseline | 25 | shared protocol plus validated modern lanes | active |
| `version/26.2` | 26.2 | 25 | Paper/Purpur, Fabric, NeoForge | experimental |
| `version/26.1.2` | 26.1.2 | 25 | Paper/Purpur, Fabric, NeoForge | experimental |
| `version/1.21.11` | 1.21.11 | 21 | Paper/Purpur, Fabric, NeoForge | planned |
| `version/1.20.1` | 1.20.1 | 17 | Paper/Purpur, Fabric, Forge | planned |
| `version/1.19.2` | 1.19.2 | 17 | Paper/Purpur, Fabric, Forge | planned |
| `version/1.18.2` | 1.18.2 | 17 | Paper/Purpur, Fabric, Forge | planned |
| `version/1.16.5` | 1.16.5 | 8 runtime target | Paper-family, Fabric, Forge | experimental feasibility scaffold |
| `version/1.12.2` | 1.12.2 | 8 | Paper/Spigot, Forge | stretch feasibility |

A planned branch is not created or published as a compatibility claim. It is created when it contains at least a pinned, independently buildable scaffold or a checked-in feasibility result explaining why a surface cannot yet build.

## Rules

1. `main` owns protocol major/minor definitions, golden fixtures, security rules, and the authoritative plan.
2. Version branches consume the same protocol major and replay the same golden fixtures. A version branch must not silently fork message semantics.
3. Platform source may be copied when Minecraft APIs differ. Do not hide incompatible render, networking, loader, mapping, or scheduler APIs behind reflection merely to reduce files.
4. Backport shared fixes by cherry-picking focused commits. Do not merge an old version branch wholesale into a newer one.
5. Each branch pins its own Minecraft, Java, Gradle, loader/API, mappings, and plugin coordinates and carries its own dependency locks or equivalent reproducibility metadata.
6. Branch CI builds only the surfaces declared by that branch. `version/**` pushes are included in the workflow trigger, but legacy branches may replace the workflow with a toolchain-specific matrix.
7. Client media bytes remain off the Minecraft server in every branch. Native media and client renderer classes remain absent from dedicated-server artifacts.
8. `planned`, `experimental`, and `supported` retain their definitions from `compatibility/targets.yaml`. A branch name or successful compile does not imply runtime support.

## Release flow

- Develop shared protocol/security fixes on `main` first.
- Create or update a version branch from the latest compatible shared baseline.
- Pin and compile the exact platform lane.
- Run protocol fixtures, dedicated-server loading, client rendering, real media, reconnect/lifecycle, native cleanup, and synchronization gates.
- Publish artifacts only for the exact tested version and loader. Tag releases with both product and Minecraft version, for example `v0.1.0-mc26.2`.

## Current repository

The GitHub repository is `https://github.com/PixelTail/VidScreen`. `main`, `version/26.2`, and `version/26.1.2` were initialized from the first tested source baseline. Older branches are added incrementally because their Gradle, mappings, loader, rendering, networking, and Java constraints are not interchangeable with 26.x.
