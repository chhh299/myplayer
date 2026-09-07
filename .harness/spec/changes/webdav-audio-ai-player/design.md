# 详细系统设计：WebDAV音频播放与自定义AI字幕翻译支持

## 1. 架构总览 (System Architecture & Topology)

### 1.1 系统拓扑结构图

```
+-----------------------------------------------------------------------------------+
|                                 mpvRx 表现层 (UI)                                  |
|                                                                                   |
|  [NetworkBrowserScreen]      [PlayerActivity]           [AiIntegrationScreen]     |
|   +-- Folders Section         +-- MPVView (Native Surface) +-- Provider Dropdown  |
|   +-- Audios/Videos List      +-- AudioPlayerOverlay       +-- Custom Base URL    |
|   +-- NetworkMediaCard        +-- LyricsOverlay            +-- API Key & Model    |
|                               +-- Adapted Controls Bar     +-- Verify / Test Conn |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                              领域模型与业务逻辑层                                  |
|                                                                                   |
|  [NetworkBrowserViewModel]    [PlayerViewModel]          [AiService]              |
|   +-- isPlayableMediaFile()    +-- isAudioOnly (State)    +-- CustomAiClient      |
|   +-- createPlayableUri()      +-- currentLyrics (Flow)   +-- translateSubtitle() |
|                                +-- seekTo() / parseLrc()  +-- Bilingual Prompts   |
+-----------------------------------------------------------------------------------+
       |                                |                            |
       v                                v                            v
+------------------------+  +------------------------+  +---------------------------+
|    数据访问与网络代理    |  |       底层播放引擎      |  |       外部 AI 网关        |
|                        |  |                        |  |                           |
| [WebDavClient]         |  | [mpvlib (Native libmpv)|  | [OpenAI-Compatible APIs]  |
|  +-- PROPFIND / GET    |  |  +-- Audio Track / Demux|  |  +-- /v1/models          |
| [NetworkStreamingProxy]|  |  +-- sub-text / time-pos|  |  +-- /v1/chat/completions|
|  +-- NanoHTTPD (206)   |  |  +-- Embedded Artwork  |  |  (DeepSeek / Ollama /    |
| [SubtitleOps]          |  |  +-- Equalizer/Playback|  |   SiliconFlow / OneAPI)  |
+------------------------+  +------------------------+  +---------------------------+
```

### 1.2 核心数据流

1. **WebDAV 音频流转路径**：
   `WebDAV Server` $\xrightarrow{\text{PROPFIND}}$ `WebDavClient` $\xrightarrow{\text{NetworkFile (Audio)}}$ `NetworkBrowserViewModel` $\xrightarrow{\text{Register Stream}}$ `NetworkStreamingProxy (127.0.0.1:port/streamId)` $\xrightarrow{\text{Intent}}$ `PlayerActivity` $\xrightarrow{\text{loadfile}}$ `mpvlib`
2. **封面与歌词联动路径**：
   - 封面：`NetworkRepository.listFiles(同目录)` / `mpvlib albumart track` / `MediaMetadataRetriever` $\rightarrow$ `AudioPlayerOverlay` 居中展示
   - 歌词：`SubtitleOps.autoloadNetworkFileSubtitles` $\rightarrow$ `Proxy (lrc/srt)` $\rightarrow$ `mpv sub-add` & `PlayerViewModel` 解析时间轴 $\rightarrow$ 实时比对 `MPVLib.propInt["time-pos"]` $\rightarrow$ `LyricsOverlay` 高亮滚动与点击跳转
3. **AI 双语翻译路径**：
   原始字幕/歌词 $\rightarrow$ 分块切片（Chunking） $\rightarrow$ 携带双语结构化约束 Prompt $\rightarrow$ `CustomAiClient (OpenAI-Compatible)` $\rightarrow$ 译文解析与格式恢复 $\rightarrow$ 保存并重新挂载双语字幕轨。

---

## 2. 模块详细设计 (Component Technical Specifications)

### 2.1 默认中文与国际化引擎

#### 2.1.1 需求目标
1. 新安装用户首次启动应用，无论系统系统语言为何，默认直接采用中文界面（简体中文 `zh-CN`）。
2. 在外观偏好设置中增加“应用语言（App Language）”设置项，支持“简体中文 (默认)”、“跟随系统”、“English”等选项。
3. 补全新增音频、网络浏览和 AI 配置的全部中文词条，杜绝硬编码英文。

#### 2.1.2 实施方案
- **首启语言探测与初始化**：
  在 `App.onCreate` 中增加应用语言初始化逻辑。读取 `AppearancePreferences.appLanguage`。若偏好值为空（首次启动），默认写入 `"zh-CN"`，并调用 `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"))`。
- **外观设置项新增**：
  在 `AppearancePreferences.kt` 中添加：
  ```kotlin
  val appLanguage = preferenceStore.getString("app_language", "zh-CN")
  ```
  在 `AppearancePreferencesScreen.kt` 中提供下拉选择器：
  - `zh-CN`: 简体中文（默认）
  - `system`: 跟随系统
  - `en`: English
  切换时调用 `AppCompatDelegate.setApplicationLocales(...)`，实现即时无感热切换。

---

### 2.2 WebDAV 音频格式解析与媒体流代理打通

#### 2.2.1 需求目标
1. 解除网络浏览器（WebDAV/SMB/FTP）仅显示视频的限制，支持浏览 `.mp3`, `.m4a`, `.flac`, `.wav`, `.ogg`, `.opus`, `.aac`, `.mka`, `.ape` 等音频文件。
2. 针对点击音频文件，打通网络代理播放流，支持精准 Seek（Range: bytes 请求）。
3. 自动识别同目录下与音频同名的歌词文件（`.lrc`, `.krc`, `.srt`, `.vtt`）并随音频一同载入播放器。

#### 2.2.2 核心实现
- **文件过滤策略重构 (`NetworkBrowserViewModel.kt`)**：
  将 `isPlayableVideoFile` 升级为通用媒体判断 `isPlayableMediaFile`，并派生 `isAudioFile`：
  ```kotlin
  fun NetworkFile.isAudioFile(): Boolean {
    if (isDirectory || isM3uFile(this)) return false
    val mime = mimeType?.lowercase()
    if (mime?.startsWith("audio/") == true) return true
    val ext = name.substringBefore('?').substringBefore('#')
      .substringAfterLast('.', "").lowercase()
    return ext in FileTypeUtils.AUDIO_EXTENSIONS
  }

  fun NetworkFile.isPlayableMediaFile(): Boolean {
    return isPlayableVideoFile() || isAudioFile()
  }
  ```
- **网络流代理 MIME 类型完善 (`NetworkStreamingProxy.kt`)**：
  在 `registerStream` 时，通过文件扩展名利用 `FileTypeUtils.getMimeTypeFromExtension(ext)` 获取准确的 MIME 类型（如 `audio/mpeg`, `audio/flac`, `audio/mp4`），不再默认使用 `video/mp4`。
- **歌词/字幕自动挂载 (`SubtitleOps.kt`)**：
  当前 `autoloadNetworkFileSubtitles` 已包含 `lrc` 与 `krc` 判定。调用入口放宽对 `intent.type` 的限制，当播放音频文件时，同样触发 `SubtitleOps.autoloadSubtitles(...)`，从而将同名 `.lrc` 文件自动注册进 proxy 并通过 `MPVLib.command("sub-add", proxyUrl, "select")` 挂载。

---

### 2.3 音频播放体验与控制条适配

#### 2.3.1 需求目标
1. 当播放音频文件时（通过 `isAudioOnly` 或 `isKnownAudioLaunch` 识别），自动进入专属音频播放视图。
2. 封面展示：展示内嵌专辑封面或同目录提取的 cover 图片；无封面时展示富有质感的音乐唱片或脉冲波形（Audio Visualizer）。
3. 控制条适配：隐藏视频独有的画面比例、超分辨率着色器（Anime4K）、画面裁剪、视频硬解等按钮；突出展示上一首、下一首、播放/暂停、播放模式（循环/单曲/随机）、进度条、音轨与歌词开关。

#### 2.3.2 布局与组件设计
- **`AudioPlayerOverlay` 视觉设计**：
  - 位于视频层正上方，采用居中高保真材质设计。
  - 封面卡片尺寸：动态适配屏幕宽度（占屏幕宽度的 65%~75%，最大 360dp），圆角 16dp，外围带有基于 Material You 动态提取色调的轻量光晕（Ambient Glow）与阴影。
  - 封面下方展示：曲目标题（大字号粗体加跑马灯 `basicMarquee`）、艺术家/专辑名。
- **控制条适配逻辑 (`PlayerControlsPortrait.kt` / `PlayerControlsLandscape.kt`)**：
  - 增加 `isAudioOnly` 分支判断。
  - 在音频播放模式下：
    - 隐藏：`FRAME_NAVIGATION`, `VIDEO_ZOOM`, `ASPECT_RATIO`, `DECODER`, `CURRENT_CHAPTER`。
    - 保留并增强：`BACK_ARROW`, `MEDIA_TITLE`, `PLAY_PAUSE`, `PREV_TRACK`, `NEXT_TRACK`, `REPEAT_MODE`, `SHUFFLE`, `SEEK_BAR`, `LYRICS_TOGGLE`, `PLAYBACK_SPEED`, `SLEEP_TIMER`。

---

### 2.4 实时歌词/字幕联动系统 (Real-time Synced Lyrics)

#### 2.4.1 歌词同步原理
- **双通道歌词驱动**：
  1. **底层通道 (libmpv)**：通过 `sub-add` 挂载 `.lrc` 或 `.srt`，由 mpv 自身负责根据音频 PTS 同步计算当前激活的行。
  2. **表现层通道 (Compose Overlay)**：
     - 轻量模式：通过 `MPVLib.propString["sub-text"]` 获取 mpv 引擎当前抛出的字幕文本，在封面下方以优雅排版展示单行/双行实时字幕。
     - 沉浸式歌词面板（Lyrics Panel）：从 Proxy 读取歌词源文本，解析为时间戳行列表：
       ```kotlin
       data class LyricLine(
         val timeMs: Long,
         val text: String,
         val translationText: String? = null,
       )
       ```
     - 结合 `MPVLib.propInt["time-pos"]` 高亮显示当前唱到的歌词行，并使用 `LazyListState.animateScrollToItem()` 保持当前行处于屏幕视线居中位置。
     - 用户点击任意一行歌词，触发 `viewModel.seekTo((line.timeMs / 1000).toInt())` 实现精准点播。

---

### 2.5 自定义 AI 提供商 (Custom Base URL + API Key + Model)

#### 2.5.1 架构设计
- **枚举扩展 (`AiPreferences.kt`)**：
  在 `AiProvider` 中增加 `CUSTOM`：
  ```kotlin
  enum class AiProvider(val displayName: String) {
    OPENCODE("OpenCode"),
    GROQ("Groq"),
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    OPENROUTER("OpenRouter"),
    TOGETHER("Together"),
    LOCAL("Offline Model"),
    CUSTOM("Custom (OpenAI-Compatible)"),
  }
  ```
- **配置项扩展 (`AiPreferences.kt`)**：
  ```kotlin
  val customBaseUrl = preferenceStore.getString("ai_custom_base_url", "https://api.openai.com/v1")
  val customApiKey = preferenceStore.getString("ai_custom_api_key", "")
  val customModel = preferenceStore.getString("ai_selected_model_custom", "")
  val customAvailableModels = preferenceStore.getString("ai_available_models_custom", "[]")
  val bilingualSubtitleTranslation = preferenceStore.getBoolean("ai_bilingual_subtitle_translation", true)
  ```
- **通用 OpenAI-Compatible 客户端 (`CustomAiClient.kt`)**：
  复用 OkHttp 与 Json 序列化管道，根据 `customBaseUrl.get()` 动态构建请求 URL：
  - 模型列表：`GET ${baseUrl.trimEnd('/')}/models`
  - 聊天补全：`POST ${baseUrl.trimEnd('/')}/chat/completions`
  - 格式适配：支持标准 OpenAI 格式的入参（`messages`, `model`, `temperature`, `max_tokens` / `max_completion_tokens`）与错误返回解析。

---

### 2.6 双语字幕与歌词 AI 翻译处理流

#### 2.6.1 双语生成机制
针对 SRT、VTT、LRC 和 ASS 格式分别定制输出规范 Prompt：
- **SRT / VTT 双语结构**：
  ```
  1
  00:00:01,000 --> 00:00:04,000
  Original subtitle text
  目标语言中文翻译
  ```
- **LRC 歌词双语结构**：
  ```
  [00:12.34]Original lyric line
  [00:12.34]中文翻译歌词
  ```
  或在同一时间戳下内联展示：
  ```
  [00:12.34]Original lyric line / 中文翻译歌词
  ```

#### 2.6.2 翻译流程与缓存
1. 提取字幕文本，按固定 Chunk 大小切分（15~20 个字幕块）。
2. 在 `AiPrompts.kt` 中注入双语指令：
   `"You are a professional subtitle translator. For each subtitle entry, retain the original line and append the natural, accurate translation directly beneath it. Keep timecodes and indexes identical."`
3. 执行分块并发/流式翻译，利用本地磁盘持久化缓存中间块（断点续翻）。
4. 翻译完毕后保存为 `*.bilingual.AI.<ext>` 并自动调用 `MPVLib.command("sub-add", newPath, "select")`。

---

## 3. 接口与数据结构定义 (Interfaces & Contracts)

### 3.1 歌词与媒体数据模型

```kotlin
package app.gyrolet.mpvrx.domain.lyrics

@androidx.compose.runtime.Immutable
data class LyricLine(
  val timeMs: Long,
  val text: String,
  val translation: String? = null,
)

data class LyricsDocument(
  val lines: List<LyricLine>,
  val isBilingual: Boolean = false,
  val offsetMs: Long = 0L,
)
```

### 3.2 自定义 AI 客户端接口契约

```kotlin
package app.gyrolet.mpvrx.repository.ai

class CustomAiClient(
  private val client: OkHttpClient,
  private val json: Json,
  private val baseUrlProvider: () -> String,
) : AiClient {
  override suspend fun fetchModels(apiKey: String): Result<List<AiModelInfo>>
  override suspend fun verifyKey(apiKey: String): Result<String>
  override suspend fun generateContent(
    apiKey: String,
    model: String,
    instruction: String,
    userInput: String,
    options: AiGenerationOptions = AiGenerationOptions(),
  ): Result<AiGeneratedContent>
}
```

---

## 4. 架构决策记录 (## Decisions)

### D1: 默认语言策略与本地化切换机制

- **决策内容**：
  在 `App.onCreate` 中通过读取持久化偏好判断是否为首启，若未设置则主动将应用首选语言初始化为简体中文 (`zh-CN`)，并调用 `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"))`。同时在 `AppearancePreferences` 中暴露语言配置供用户随时切换回系统默认或英文。
- **备选方案（Alternatives）及否决理由**：
  - *备选方案 A*：完全依赖 Android 系统语言环境，仅提供 `values-zh-rCN/strings.xml` 资源包。
    - **否决理由**：无法达成“默认中文界面”的硬性验收要求。当用户设备系统语言为英文（如部分外版手机或开发者设备）时，应用会回退至 `values/strings.xml` 显示英文界面，违背母语首选的体验诉求。
  - *备选方案 B*：在 BaseActivity 中覆写 `attachBaseContext`，使用自定义 `ContextWrapper` 更改 Configuration 的 Locale。
    - **否决理由**：侵入性强，在 Android 13+ 引入 Per-App Language Preferences 之后容易引发 Context 包装层冲突、Activity 重建与 Compose 视图生命周期紊乱。

### D2: WebDAV 音频网络流代理与播放链路设计

- **决策内容**：
  全面复用既有成熟的 NanoHTTPD `NetworkStreamingProxy` 本地轻量级 HTTP 代理机制，依据媒体扩展名准确设置 `Content-Type`（如 `audio/mpeg`, `audio/flac`, `audio/mp4`），通过 206 Partial Content 支持大文件精准 Seek，并统一分发播放 Intent 给 `PlayerActivity`。
- **备选方案（Alternatives）及否决理由**：
  - *备选方案 A*：直接将带有认证账号密码的 `http://user:pass@host/path` 或 `dav://` URL 交给 mpv 播放。
    - **否决理由**：许多私有 WebDAV 服务器（如坚果云、部分群晖套件）在并发 HEAD/PROPFIND 请求与基本认证重定向处理上存在兼容性缺陷，直接交给底层 ffmpeg/mpv 串流容易发生 401 认证失败、拖拽 Seek 超时卡死以及网络重连中断。
  - *备选方案 B*：在播放前先完整下载整首音频文件到本地 App 缓存目录。
    - **否决理由**：高清无损音频（如 24bit/192kHz FLAC 或 DSD）单曲容量可达 100MB~300MB，全量下载将带来不可接受的首跳播放延迟（秒开变成数十秒），且极度消耗移动设备存储与网络流量。

### D3: 音频专属交互与封面/歌词渲染方案

- **决策内容**：
  在 Compose 层构建声明式的 `AudioPlayerOverlay` 和 `LyricsOverlay`。通过响应 `PlayerViewModel.isAudioOnly` 状态自适应重构界面；通过 `MPVLib.propString["sub-text"]` 与本地 LRC 解析器双轨驱动歌词滚动与点击拖拽跳转播放。
- **备选方案（Alternatives）及否决理由**：
  - *备选方案 A*：完全依赖 mpv 原生 libass 引擎在视频绘制表面（SurfaceView）渲染歌词和封面。
    - **否决理由**：mpv 的 OSD/libass 渲染专为影视字幕设计，对无视频轨的音频文件缺乏精美的专辑卡片阴影、动态毛玻璃模糊等现代化视觉表现，且完全不支持触控交互（例如用户无法在原生 OSD 字幕上滑动查看全歌词或点击跳转指定行）。
  - *备选方案 B*：针对音频播放引入完全独立的 AndroidX Media3 / ExoPlayer 播放器引擎，与 mpv 视频分立。
    - **否决理由**：形成“双内核”架构，导致音轨管理、全局均衡器、蓝牙线控、MediaSession 后台播放服务、最近播放历史数据库等逻辑全部分裂维护，架构冗余且测试复杂度急剧膨胀。

### D4: 自定义 AI 协议端点设计

- **决策内容**：
  采用工业标准的 OpenAI-Compatible REST 协议，在偏好体系中引入单一的 `CUSTOM` Provider，由用户自由配置 `Base URL`、`API Key` 与 `Model Name`，并在客户端封装通用适配器 `CustomAiClient`。
- **备选方案（Alternatives）及否决理由**：
  - *备选方案 A*：为 DeepSeek、Moonshot (Kimi)、SiliconFlow、Zhipu、Ollama 等各家平台分别硬编码独立的枚举和专属 API 客户端。
    - **否决理由**：当前主流大模型供应商及自建反向代理（OneAPI, NewAPI）均已 100% 兼容 OpenAI 的 `/v1/chat/completions` 接口规范。针对每个厂商增加硬编码枚举会导致界面配置臃肿、扩展性差，且无法支持用户自建内网模型。
  - *备选方案 B*：在移动端内嵌轻量级脚本引擎（如 Python 或 JavaScript）让用户自定义调用代码。
    - **否决理由**：引入解释执行引擎会显著增加 APK 体积（>20MB），并带来严重的冷启动开销与潜在的代码执行安全风险。

### D5: 双语字幕/歌词生成与对齐策略

- **决策内容**：
  针对 SRT、VTT、LRC、ASS 等不同格式定制强约束的结构化 Prompt，由大模型在单次调用中返回保留原文且追加译文的双语格式文本。本地对返回的 Chunk 执行格式校验与时间轴结构校验，并持久化生成独立的 `*.bilingual.AI.<ext>` 文件进行加载。
- **备选方案（Alternatives）及否决理由**：
  - *备选方案 A*：仅调用 AI 生成单语译文，随后在本地通过算法拆分句子进行行对齐拼接。
    - **否决理由**：自然语言翻译中经常出现“一句话拆分为两句”或“两句话合并为一句”的语意重构现象。在无语境的前提下由本地纯算法按行或词对齐极易出现字幕时间戳错位与内容串行。
  - *备选方案 B*：生成独立的译文字幕文件，使用 mpv 的次字幕（`secondary-sub`）功能双轨同时渲染。
    - **否决理由**：移动端手机屏幕垂直空间极为有限，双字幕独立渲染易出现字体重叠、位置抢占与样式不一致问题；更重要的是次字幕功能完全无法被 LRC 歌词解析和沉浸式歌词面板消费。

---

## 5. 错误处理与容错机制 (Error Handling)

1. **网络音频断流与重试**：
   当 WebDAV 音频流由于网络抖动中断时，`NetworkStreamingProxy` 捕获 SocketTimeoutException 并自动尝试重新建立 upstream stream，向 mpv 保证不断流。
2. **AI 自定义接口连通性容错**：
   在 `AiIntegrationScreen` 中提供“测试连接”功能。当用户输入错误的 Base URL 或 API Key 时，捕获 HTTP 401/404/ECONNREFUSED 并给出友好清晰的中文错误排查指引，避免在正式播放时翻译崩溃。
3. **缺失歌词与封面降级**：
   若音频文件没有内嵌封面且同目录无图片，优雅降级为音频波形律动或精致的唱片占位图标；若无歌词，则隐藏歌词入口，不影响基础音频播放。
