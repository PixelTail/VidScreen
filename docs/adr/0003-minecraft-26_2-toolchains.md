# ADR 0003: Use Official 26.2 Names and Toolchains

- Status: Accepted
- Date: 2026-09-01

## Context

Minecraft 26.2 uses Java 25 and publishes unobfuscated Mojang names. Fabric 26.2 moved to the current non-remapping Loom path, NeoForge 26.2 uses ModDevGradle, and Paper exposes a `26.2.build` API line. Applying older Yarn/intermediary or remapping assumptions to this lane would produce misleading source references and brittle artifacts.

## Decision

- Use Temurin JDK 25 for the 26.2 build.
- Use Gradle 9.5.1.
- Compile Fabric 26.2 against Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, and Loom 1.17.20.
- Compile NeoForge 26.2 against NeoForge 26.2.0.72 and ModDevGradle 2.0.144.
- Compile Paper against `paper-api:26.2.build.121-stable`.
- Use Mojang's unobfuscated 26.2 names in source.
- Use `minecraft-dev` for source/mapping evidence and `plugdev` for the Paper/Purpur runtime loop, but treat exact compilation and runtime tests as the authority.
- Do not claim Purpur or Folia compatibility based only on Paper API compilation.

## Consequences

- Older version lanes will need their own recorded mappings and build-plugin decisions.
- 26.2 platform source may use Java 25 language features; shared source remains conservative for future reuse.
- Updating any pinned coordinate requires an exact lane rebuild and relevant runtime tests.

## Verification

The Paper, Fabric, and NeoForge 26.2 modules compile under the project-local JDK 25. Runtime launch, client render, and dedicated-server classloading remain separate gates in `compatibility/targets.yaml`.
