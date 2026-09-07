# Project Guidance

This repository builds a synchronized, cross-version Minecraft video-screen system.

## Required development posture

- Start with Minecraft 26.2 and Java 25.
- Preserve version and loader boundaries; do not disguise incompatible APIs behind unchecked assumptions.
- Keep media bytes off the Minecraft server. Servers synchronize screen definitions and playback state; clients fetch and decode media.
- Keep client/native classes out of dedicated-server classpaths.
- Treat every URL and protocol payload as untrusted input.
- Do not claim support for a version/platform combination until its compatibility tests pass.

Load and follow the project Skill at `.claude/skills/cross-version-video-screen/SKILL.md` for architecture, security, compatibility, and testing work.

The authoritative implementation sequence is `docs/PLAN.md`. Update that plan when a validated discovery changes scope or ordering.

## Project MCPs

- Use `minecraft-dev` for Minecraft source, mappings, hierarchy, JAR, and Mixin analysis.
- Use `plugdev` for the Paper/Purpur plugin loop after the Paper module is scaffolded.
- MCP output is supporting evidence, not a substitute for compiling against the exact target lane.

## Git and generated content

- Do not commit Gradle caches, run directories, worlds, media test files, native extracts, or MCP caches.
- Do not commit generated binaries.
- Keep protocol/schema changes backward-compatible within a protocol major version and test them before platform edits.
