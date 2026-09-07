# ADR 0001: Isolate Minecraft Version and Loader Lanes

- Status: Accepted
- Date: 2026-09-01

## Context

VidScreen targets server plugins, server mods, and required client mods across Minecraft generations. Minecraft, Java, mappings, payload APIs, rendering APIs, and loader build plugins change independently. Sharing loader-specific source or one mutable build-plugin classpath across every target would hide incompatibilities and make older ports destabilize the primary lane.

## Decision

- Implement and prove Minecraft 26.2 first.
- Keep Minecraft-independent domain, protocol, server, client-coordination, and media adapters in `shared/` modules.
- Keep Minecraft and loader imports in `platforms/<version>/<platform>` modules.
- Compile shared modules to Java 8 bytecode while that remains practical; compile the 26.2 platform lane for Java 25.
- Pin exact platform dependencies in each lane and compile against the exact target API.
- Add lower-version lanes only after 26.2 protocol and runtime gates pass.
- Treat `compatibility/targets.yaml` as the machine-readable support-claim boundary.

## Consequences

- Some loader adapter code will be deliberately duplicated rather than hidden behind unsafe assumptions.
- A lower lane may select Forge instead of NeoForge and may require a separate Gradle build.
- Shared APIs must avoid accidental Minecraft classes and post-Java-8 runtime methods.
- A successful root compile does not establish runtime support; every platform/version remains `experimental` or `planned` until its exact runtime gates pass.

## Verification

- `./gradlew build` compiles shared modules and all 26.2 platform modules.
- Dedicated-server classpath tests and lower-lane builds remain required before support promotion.
