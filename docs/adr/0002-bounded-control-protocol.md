# ADR 0002: Use a Bounded Minecraft Control Protocol

- Status: Accepted
- Date: 2026-09-01

## Context

All clients need the same screen definitions and playback intent, but relaying media bytes through the Minecraft server would waste bandwidth, expand the attack surface, and prevent scalable multi-client playback. Loader networking APIs differ, while the product state and compatibility rules should not.

## Decision

- Use one binary control protocol carried by each platform's namespaced Minecraft custom-payload channel.
- Prefix every message with magic `VIDS` and protocol version `1.0`.
- Negotiate loader, product version, media capabilities, and limits before sending state.
- Keep screen and playback revisions monotonic.
- Support full snapshots plus screen upsert/delete and playback updates.
- Estimate server time with bounded clock request/response sampling; send no per-frame synchronization traffic.
- Bound payload bytes, strings, collections, screen dimensions, and enum values before allocation or execution.
- Reject incompatible protocol major versions. A newer minor may only append optional fields to the end of an existing top-level message; older decoders ignore that bounded tail. Changes inside repeated/nested structures require a new message type or protocol major.
- Preserve exact trailing-byte rejection for the current or older minor, so malformed current payloads do not silently pass.
- Send media descriptors and timestamps only. Clients resolve, fetch, decode, render, and play media directly.

## Consequences

- Paper, Fabric, and NeoForge adapters share `WireCodec` and message classes while retaining platform-specific registration.
- Media capability availability can differ per client because external executables may be absent.
- Servers must track negotiated capabilities and eventually filter or annotate unsupported source definitions per connection.
- Protocol fixture compatibility is required before editing platform adapters or starting a lower version lane.

## Security and privacy

- Protocol sources may contain signed query strings and must never be logged.
- The protocol cannot carry cookies, authorization headers, arbitrary request headers, server commands, native binaries, or media frames.
- Malformed and trailing bytes are rejected rather than ignored silently.

## Verification

`WireCodecTest` round-trips all initial message families and rejects bad magic, truncation, trailing data, unknown message types, unsupported majors, and oversized payloads. Golden cross-version fixtures and property/fuzz tests remain open release gates.
