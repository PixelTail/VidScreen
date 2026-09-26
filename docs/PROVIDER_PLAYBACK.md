# 网站视频、直播与本地登录验证

验证日期：2026-09-22，18:28–18:42 UTC。测试代码版本：`abdde2681a3a7b289e1822e40e5a713fc6c311d6`。

**结论：当前还没有完成网站音视频或直播支持。B站和 Twitch 直播已有可行的解析与解码基础；抖音直播需要单独适配。本地 Cookie 登录尚未实现，且 Cookie 不能解决现有格式选择、声音和画质限制。**

## 测试方法与结果

使用官方 [yt-dlp 2026.08.19](https://github.com/yt-dlp/yt-dlp/releases/tag/2026.08.19) Windows 可执行文件，核对官方 SHA-256 校验值。所有请求均为匿名请求，禁用用户配置与外部插件，没有读取浏览器、Cookie 文件或账号凭据。没有保存 CDN 签名 URL；仅保留格式、协议、计数和脱敏错误。

先枚举上游格式，再调用 VidScreen 当前 `MediaSources → YtDlpMediaResolver → ScreenPlaybackCoordinator → NativeFfmpegVideoPlayer` 链路。每路进程有有限超时，最多并行两路；结束时解码线程数为 0。另做取消 seek 的原生直播解码对照。没有把诊断对照写入产品逻辑。

| 测试来源 | 匿名上游解析 | 当前 VidScreen 链路 | 结论 |
|---|---|---|---|
| [B站教程视频 BV1Jpja6vEUm](https://www.bilibili.com/video/BV1Jpja6vEUm/) | 成功：15 个格式，12 个仅视频、3 个仅音频；列出 360/480/720/1080p | 在解析阶段失败，0 帧；现有 `best[height<=360]/best` 返回 `Requested format is not available` | 当前选择器不能处理该视频的分离音视频格式。列出 1080p 不等于已验证该格式可播放。 |
| [B站直播 768756](https://live.bilibili.com/768756) | 当时在线；8 个格式，HTTPS / HLS | 在原生解码阶段失败，0 帧；已调用一次 seek | 无 seek 对照成功得到 10 帧，证明该样例的解析器与原生视频解码基础可用。 |
| [Twitch imperialhal__](https://www.twitch.tv/imperialhal__) | 当时在线；6 个 HLS 格式，视频档位 160/360/480/720/1080p | 在原生解码阶段失败，0 帧；已调用一次 seek | 无 seek 对照成功得到 10 帧。仍需专门的直播时钟和恢复策略。 |
| [抖音直播 941738836754](https://live.douyin.com/941738836754) | yt-dlp 返回 `Unsupported URL` | `MediaSources` 校验即拒绝，0 帧 | 项目与本次上游稳定版均未支持该直播地址。 |

直播对照使用相同解析器、相同原生播放器和 640×360 输出，只是不进入点播式 seek / 校时逻辑。B站帧时间戳约为 1,541,732–1,542,665 ms，Twitch 约为 19,582,099–19,582,999 ms，均不是从本次观看开始计时的点播位置。当前协调器却把服务器点播位置送入播放器 seek，之后还会按点播规则纠偏。这是本次两路直播的已确认阻塞点。10 帧对照仅验证短时出图，不证明声音、持续播放、掉线恢复或多人直播同步。

## 当前代码缺口

1. **B站分离音视频。** [解析器](../shared/media-ytdlp/src/main/java/dev/vidscreen/media/ytdlp/YtDlpMediaResolver.java) 只选择一个完整流、只打印一个 URL；[媒体描述](../shared/media-api/src/main/java/dev/vidscreen/media/ResolvedMedia.java) 无独立音轨。B站 DASH 提取器会提供独立的视频和音频格式，详见[固定版本上游实现](https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/yt_dlp/extractor/bilibili.py#L65-L123)。
2. **没有声音输出。** 原生播放器使用 `grabImage()`，外部 FFmpeg 路径使用 `-an`。当前成功出图也不能标成完整音视频支持。
3. **直播语义没有接通。** 解析器能返回 `MediaKind.LIVE`，但[协调器](../shared/client-core/src/main/java/dev/vidscreen/client/ScreenPlaybackCoordinator.java) 没有直播分支；需要禁止点播式自动 seek，定义暂停/继续/回到直播的行为，并实现断流重连、退避和重新解析。
4. **请求上下文与失效恢复缺失。** 当前丢弃上游 headers，并把 `expiresAt` 设为 `null`。本次 B站返回的头名含 Referer，但不能据此认定所有流都强制要求该头；两路直播无额外头的对照已出图。其他来源仍可能要求同 IP、Cookie、UA 或 Referer，需要按站点和跳转边界正确处理，见[上游 FAQ](https://github.com/yt-dlp/yt-dlp/wiki/FAQ#i-extracted-a-video-url-with--g-but-it-does-not-play-on-another-machine--in-my-web-browser)。
5. **画质固定。** 四个现代客户端都以 640×360 创建播放器，并向解析器请求 360 高度。登录后即便多出高清格式，当前输出仍只有 640×360。
6. **能力声明不等于验证。** 当前发现 yt-dlp 文件便声明 provider / live 位。本机普通客户端此前未配置 yt-dlp；本次测试显式传入了临时工具路径，未更改用户的全局 PATH 或客户端配置。
7. **抖音直播单独缺失。** 项目没有 Douyin resolver/capability；上游的 `DouyinIE` 匹配普通 `/video/` 页面，`TikTokLiveIE` 匹配 TikTok，不能视为抖音直播支持。见[上游对应实现](https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/yt_dlp/extractor/tiktok.py#L1288-L1525)。

## 本地登录与 Cookie 的可行方式

可以增加客户端本地登录状态，但应由用户在对应站点完成登录，并按站点授权 VidScreen 使用登录状态。登录凭据只用于该客户端向对应站点发起请求；不进入 Minecraft 服务器消息、屏幕存档、其他玩家客户端或日志。

yt-dlp 支持 Cookie 文件和指定浏览器配置，但官方提示“浏览器导出到文件”可能包含全部站点的 Cookie。因此不能默认导出整个日常浏览器；应使用独立登录配置或经过域名筛选的专用文件，并提供删除登录状态的入口。见[官方 Cookie FAQ](https://github.com/yt-dlp/yt-dlp/wiki/FAQ#how-do-i-pass-cookies-to-yt-dlp)。

Cookie 能携带账号已经拥有的访问权限；高清档位仍受账号资格和站点返回格式限制。[B站提取器](https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/yt_dlp/extractor/bilibili.py#L46-L101) 使用 `SESSDATA` 判断登录，并对部分缺失档位提示会员要求。当前项目传入 `--ignore-config` 且没有 Cookie 参数，所以现在即使用户已在浏览器登录，也不会自动改善 VidScreen 的播放。

## 建议实现顺序

1. 完成结构化解析结果：视频/音频轨道、格式、直播标记、请求头、刷新入口；补齐 PCM 音频输出与音画同步。
2. 为 B站 / Twitch 增加独立直播模式、回到直播、断流重连和临时链接刷新；验证真正的长时播放和多人同步。
3. 开放可配置分辨率和资源预算，再接入按站点授权的本地登录状态；先完成真实请求边界，防止不可信媒体跳转错误使用凭据。
4. 单独实现并维护抖音直播解析器，加入用户给定房间的测试。Cookie 无法补上当前不存在的解析器。

本次是验证与可行性审查，没有修改上述媒体功能，也没有进行登录态、会员画质、声音或持续直播的成功验收。

## 2026-09-26：跳出单 URL 取流方案的本地管道试验

针对上面的失败，单独验证了一个**尚未接入 VidScreen 产品**的候选：客户端 [Streamlink 8.6.1](https://github.com/streamlink/streamlink/releases/tag/8.6.1) 负责站点解析和连续取流，以 [`--stdout`](https://streamlink.github.io/cli.html#cmdoption-O) 输出容器字节；现有 JavaCV 用 `FFmpegFrameGrabber(InputStream, 0)` 直接从本地管道解码。试验禁用了 Streamlink 用户配置、插件缓存和第三方插件，没有读取 Cookie，也没有把媒体字节送到 Minecraft 服务端。进程有 35 秒看门狗及外层 48 秒上限，结束时子进程已退出。

| 来源 | 替代管道的短时结果 |
|---|---|
| [B站直播 768756](https://live.bilibili.com/768756) | 成功：原生视频 1280×720、双声道；10 个视频帧、43 个音频帧，约 4.5 秒完成测试。 |
| [抖音直播 941738836754](https://live.douyin.com/941738836754) | 成功：原生视频 1920×1080、双声道；10 个视频帧、18 个音频帧，约 3.7 秒完成测试。[Streamlink 抖音直播插件](https://github.com/streamlink/streamlink/blob/8.6.1/src/streamlink/plugins/douyin.py) 能匹配这个域名。 |
| [苹果官方 HLS 样例](https://developer.apple.com/streaming/examples/) | 成功：HLS 经同一本地管道输出并解出 1920×1080 视频及双声道音频；10 个视频帧、15 个音频帧。这个样例用于验证 HLS 容器通路，不代表 Twitch 本次实测通过。 |
| [Twitch imperialhal__](https://www.twitch.tv/imperialhal__) | 当次返回 `No playable streams found`；另试一个公开频道同样无流。插件是否能在当前环境取得正在直播的 Twitch 流仍需用在线房间验证。 |

这是一个有价值的架构变化：媒体容器在客户端管道里交给同一解码器，直播不需要先拆出临时 CDN URL 再按点播位置 seek；音频和原生画质也能进入解码边界。本次只**解出了**音频样本，没有让 Minecraft 发声，也没有证明长时重连、音画同步、多客户端同步或带登录态的播放。

代价同样具体：试验的隔离 Streamlink 环境安装了 22 个包，占约 46 MB，正式交付还需考虑 Python 运行时和平台包。原[媒体运行库决策](adr/0005-client-native-media-runtime.md)选择不要求普通玩家安装外部工具；是否将 Streamlink 管道做成可选客户端后端或替换默认路线，需要单独决定。Streamlink 进程仍会主动访问不可信页面及其媒体地址，因此正式版本仍需在实际连接处限制地址、跳转、凭据范围和资源占用。B站普通视频的分离 DASH 音视频问题，也不会由这个**直播**试验自动解决。

## 2026-09-26：Streamlink 本地管道的替代路线实测

重新评估单 URL 和手动跟踪 CDN 的实现后，使用官方 [Streamlink 8.6.1](https://github.com/streamlink/streamlink/releases/tag/8.6.1) 在项目忽略的临时目录做隔离试验。其 [`--stdout` 模式](https://streamlink.github.io/cli.html#cmdoption-O) 连续获取直播容器字节，JavaCV `FFmpegFrameGrabber(InputStream, 0)` 从本地管道解码。禁用 Streamlink 用户配置、插件缓存和第三方插件；没有读取浏览器或 Cookie。每个流有 35 秒看门狗和 48 秒外层超时，最终子进程均已退出。

| 来源 | 本地管道结果 |
|---|---|
| [B站直播 768756](https://live.bilibili.com/768756) | 1280×720、双声道，10 个视频帧和 43 个音频帧；约 4.5 秒结束。 |
| [抖音直播 941738836754](https://live.douyin.com/941738836754) | 1920×1080、双声道，10 个视频帧和 18 个音频帧；约 3.7 秒结束。[Streamlink 内置抖音直播插件](https://github.com/streamlink/streamlink/blob/8.6.1/src/streamlink/plugins/douyin.py) 可直接匹配该房间。 |
| [苹果官方 HLS 样例](https://developer.apple.com/streaming/examples/) | 相同管道解出 1920×1080 视频及双声道：10 个视频帧、15 个音频帧。它验证了 HLS 通路；不代替 Twitch 直播测试。 |
| [Twitch imperialhal__](https://www.twitch.tv/imperialhal__) | 当前返回 `No playable streams found`；另一个公开频道同样没有取到流。这不能证明当前 Twitch 房间的播放能力，也不能判定插件失败的具体原因。 |

这是**取流与解码边界**的实验，不是 VidScreen 功能验收：现有 JavaCV 解码器仍在使用，但不再让它直接连接 provider/CDN；Streamlink 在客户端处理 B站 HLS/HTTP、抖音 FLV 等媒体请求后提供统一字节流。它绕过了此次两个直播因点播式 seek 而得到 0 帧的旧路径，并使音频样本与原生画质可进入客户端。然而 Minecraft 音频播放、画面上传、真人登录、直播重连、多人同步和长时运行均未在这个实验中完成。

试验的隔离 Streamlink 环境有 22 个 Python 包，约 46 MB，**未包含**可独立再分发的 Python 解释器；正式客户端若采用这条路线，需要解决平台打包和许可。Streamlink 自身仍会发起网络请求，必须将不可信 URL、跳转与可能的 Cookie 限制在适当的连接边界。当前 MCEF 浏览器模组的[官方版本页](https://modrinth.com/mod/mcef/versions)未列出 Minecraft 26.2，因此不能把嵌入浏览器当作已验证的替代。此处只记录候选实测；尚未改动产品依赖或 [ADR 0005](adr/0005-client-native-media-runtime.md) 的默认方案。
