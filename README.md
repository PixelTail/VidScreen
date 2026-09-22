# VidScreen

A synchronized, cross-version Minecraft in-world video-screen project.

The product is planned as:

- a Paper/Purpur/Folia server plugin;
- Fabric and NeoForge server mods;
- required Fabric and NeoForge client mods;
- shared, versioned screen and playback protocol;
- client-side media retrieval, decoding, rendering, and spatial audio.

Development starts with Minecraft 26.2 and ports downward only after each version lane passes the compatibility test gates.

## 创建与管理（26.2 / 26.1.2）

客户端操作参照 [MusesPlayer 屏幕快速创建指南](https://www.bilibili.com/video/BV1Jpja6vEUm/) 的观影区、四角选屏、确认创建和曲面设置流程实现。使用匹配版本的 VidScreen 客户端与服务端；视频解码还需要对应的 `vidscreen-media-runtime` 客户端包。服务端不安装媒体运行库。

1. 按 **N** 打开观影菜单，进入 **新建 → 选择观影区两点**。左键选择空间对角，右键撤销；没有选点时右键取消。
2. 再按 **N**，填写区域名称并确认。未确认的绿色边框只是本地预览。
3. 选择 **屏幕四角**，面对屏幕按 **左上 → 右上 → 右下 → 左下** 顺时针选取同一方块平面上的四角。按 **N**，命名、选择绑定区域并确认。
4. 在观影页选择屏幕，粘贴 HTTPS 视频地址并点击 **播放**。可暂停、继续、停止、跳转、循环、调速和重新同步。
5. 按 **U** 或点击 **屏幕设置 / 移动**，调整曲率、细分和 XYZ 小数位移。曲率为 0 时是平面；正曲率使屏幕中心向背面凹入。可以先回到世界预览，确认后保存，取消不修改服务端。
6. 在 **观影区** 页查看边界、重选范围或删除区域。已绑定屏幕的区域需要先解除绑定。离开绑定区域会停止该客户端的解码。

名称使用英文、数字、下划线或连字符。服务端创建、修改和删除需要 `vidscreen.admin`（Paper）或游戏管理员权限（Fabric / NeoForge）；`/vidscreen status` 可查看 Paper 客户端连接状态。旧命令仍可使用。旧屏幕存档自动读取，新保存的场景同时保留观影区、曲率和位移。

直接 MP4/HLS 使用媒体运行库；Bilibili、YouTube 和 Twitch 链接还依赖客户端可用的 yt-dlp。空间音频、弹幕及完整提供商兼容性尚未验证，不能把这次创建与管理流程重构视为完整 MusesPlayer 功能兼容。

## Build and runtime status

The Minecraft 26.2 shared protocol/domain/media stack and the previously validated Paper, Fabric, and NeoForge modules use Java 25. The Paper plugin has loaded successfully on Paper 26.2 build 121 and passed plugin discovery plus `/vidscreen list`. An exact Minecraft 26.1.2 experimental lane now also compiles and packages Paper, Fabric, NeoForge, and both client media runtimes with Java 25. VidScreen uses a shared protocol/core while keeping the 26.1.2 rendering and texture-upload API differences in that lane.

VidScreen has an experimental in-process JavaCPP/JavaCV FFmpeg adapter and separate Fabric/NeoForge client media-runtime companion JARs containing Windows x86-64 and Linux x86-64 natives; each current universal runtime is approximately 60.7 MB. The clients prefer this bundled runtime when installed and retain the external FFmpeg adapter as a development fallback.

All combinations remain **experimental** or **planned**. The 26.2 + 26.1.2 Gradle build, shared behavior tests and Windows native decoder integration test have passed during the refactor. The opt-in decoder test opens an official Blender HTTPS MP4, checks frames, pause/seek/resume and decoder-thread exit. This does not establish Linux/native packaging, HLS/provider playback, spatial audio, connection-boundary redirect/DNS-rebinding enforcement, prolonged resource soak, two-client synchronization, Purpur/Folia support or release readiness.

On 2026-09-22, the Windows 26.2 Fabric client also passed a real local Paper integration run: negotiated handshake, area/screen creation, decoded GPU video texture, playback, curved-screen update, menus, area/corner overlays and deletion. The test client exited and temporary OP was revoked. NeoForge and 26.1.2 have build/test coverage, not equivalent in-game evidence. Local screenshots and the application test marker are retained under `.tools/runtime-smoke/`.

The optional local Fabric/Paper application test is in `scripts/runtime-smoke.gradle` and `scripts/runtime-smoke/`. It uses only `127.0.0.1:25565`, requires explicitly authorized temporary permissions for `VidScreenSmoke`, creates only `smoke_area` / `smoke_screen`, captures the actual world/menu/settings, deletes its scene objects and exits. It is excluded from production mods. Start the existing local test server first, then run `gradlew -I scripts/runtime-smoke.gradle :platforms:mc26.2:fabric:runClient --no-configuration-cache`. A `VIDSCREEN_SMOKE_PASS` marker is required; a zero Gradle exit alone is insufficient. On failure, remove only the named test objects, revoke the test account's permissions and stop the task-owned server/client.

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
