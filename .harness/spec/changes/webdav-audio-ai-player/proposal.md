# 变更提案：WebDAV音频播放与自定义AI字幕翻译支持 (webdav-audio-ai-player)

## 1. 变更背景 (Context)

mpvRx 目前作为一款基于 `mpvlib` 与 Jetpack Compose 开发的高性能 Android 播放器，在本地与局域网网络串流（WebDAV/SMB/FTP）播放视频方面已具备坚实的基础。然而在日常使用与网络远程挂载场景中，存在如下明显痛点：

1. **默认语言与本地化体验不足**：应用安装首次启动时，若系统语言非中文，应用默认以英文显示，且缺乏直观的应用内语言切换配置，影响中文用户的开箱即用体验。
2. **WebDAV 仅限视频文件**：网络浏览器（Network Browser）对远程文件严格限制为仅识别视频格式（`isPlayableVideoFile`），海量的远程无损及主流音频文件（MP3, M4A, FLAC, OGG, WAV 等）在文件列表中被过滤隐藏，无法浏览和点击串流播放。
3. **音频播放缺少专门的交互与视觉反馈**：当启动音频播放时，播放界面仍沿用视频控制条（大量无关的视频画质、着色器、画面比例、硬解等控件暴露），缺乏精美居中的音频封面展示（Album Cover / Artwork）以及与音频紧密相关的滚动歌词/字幕联动能力。
4. **AI 服务商受限且缺少双语字幕/歌词翻译**：内置的 AI 翻译目前仅支持硬编码的固定平台（如官方 OpenAI、Groq、Anthropic），无法配置第三方大模型代理（如 DeepSeek、SiliconFlow、OneAPI、Ollama、LocalAI、Moonshot 等自定义 Base URL + API Key + Model）。此外，目前的字幕翻译只支持目标语言替换（单语翻译），无法输出原语言与目标语言并存的“双语字幕/歌词”，降低了外语影视与音乐鉴赏的学习与对比价值。

---

## 2. 解决的问题 (Problem Statement)

本变更聚焦解决以下四个维度的系统性问题：

1. **界面本地化痛点**：提供默认简体中文的开箱即用体验，并在外观设置中增加明确的应用语言切换功能，补全音频、网络流与 AI 相关的中文本地化字串。
2. **WebDAV 媒体类型过滤痛点**：放开 WebDAV/SMB/FTP 网络浏览层的文件类型限制，将 MP3、M4A、FLAC 等音频格式纳入媒体浏览体系，并打通基于 HTTP 206 Range 请求的无损网络流音频播放。
3. **音频播放专属交互体验痛点**：适配音频播放模式（`isAudioOnly`），提供专辑封面展示（提取自内嵌封面或同目录 cover 图片）、专为音乐精简的播放控制条、以及同步时间轴滚动的高亮歌词/字幕面板（支持点击歌词跳转播放进度）。
4. **AI 端点扩展与双语翻译痛点**：新增 OpenAI 兼容标准的自定义 AI 提供商配置，允许自由填入 Base URL、API Key 与 Model 名称并提供连通性验证；改造翻译执行管道，支持 SRT/VTT/LRC/ASS 的智能双语对照翻译。

---

## 3. 用户价值 (User Value)

- **开箱即用的母语体验**：中文用户安装后直接进入全中文交互界面，无需繁琐寻找语言开关；同时保留国际化切换自由。
- **个人 NAS / 私有云音乐流媒体中心**：用户可直接通过 WebDAV 挂载 Alist、群晖 Synology、QNAP、Nextcloud 或 InfiniCLOUD 中的无损音乐库，流畅点播 FLAC/M4A/MP3，无需额外下载或安装笨重的第三方音乐播放器。
- **现代化音乐播放与沉浸式歌词**：优雅的专辑封面展示、纯净的音乐控制条，配合精准同步高亮、支持触控拖曳点播的歌词面板，带来接近主流专业音乐流媒体的交互体验。
- **自由定制的大模型能力与双语学习**：用户能够使用超高性价比的各类大模型（如 DeepSeek V3、Qwen、GLM 等）进行字幕与歌词翻译，双语对照让看剧学外语、品味外语音频变得轻松精准。

---

## 4. 影响面分析 (Scope & Impact)

### 4.1 影响模块
- **UI / 本地化模块**：
  - `values/strings.xml`, `values-zh-rCN/strings.xml`
  - `AppearancePreferences.kt`, `AppearancePreferencesScreen.kt`
  - `App.kt`, `MainActivity.kt` (Locale 设置与初始化)
- **网络流与浏览器模块**：
  - `NetworkBrowserViewModel.kt`, `NetworkBrowserScreen.kt`
  - `NetworkVideoCard.kt` (升级为支持音频属性的媒体卡片)
  - `NetworkStreamingProxy.kt` (音频 MIME 类型与 Range 响应健全)
  - `SubtitleOps.kt` (网络音频同目录歌词与字幕自动加载)
- **播放器核心与交互模块**：
  - `PlayerActivity.kt`, `PlayerViewModel.kt`
  - `PlayerControlsPortrait.kt`, `PlayerControlsLandscape.kt`
  - 新增 `AudioPlayerOverlay.kt`, `LyricsOverlay.kt`
- **AI 引擎与配置模块**：
  - `AiPreferences.kt`, `AiIntegrationScreen.kt`
  - `AiService.kt`, `AiClient.kt`, `OpenAiClient.kt`, `AiPrompts.kt`

### 4.2 兼容性与非影响面
- **Android 版本兼容**：兼容项目设定的 `minSdk = 26`（Android 8.0）至 `targetSdk = 36`，针对 Android 13+ 的应用级语言设置机制无缝降级兼容。
- **现有视频功能完全不受影响**：原有本地视频扫描、网络视频串流、HDR、Anime4K 着色器、视频硬解等核心能力保持独立，绝不发生回归破坏。
- **数据库 Schema**：不涉及破坏性 Room 数据库变更，新增偏好项采用现有的 `PreferenceStore`（SharedPreferences / MMKV 适配封装）持久化。
