# 实施任务清单：WebDAV音频播放与自定义AI字幕翻译支持 (Task Checklist)

## Phase 1: 默认中文界面与国际化资源体系 (Localization & Chinese Default)

- [x] **1.1 应用首启默认语言初始化逻辑**
  - 在 `App.kt` 或 `MainActivity.kt` 启动流程中，读取 `AppearancePreferences.appLanguage`。
  - 若用户未显式设置过语言（首次启动），默认设置为简体中文 (`zh-CN`)，并调用 `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"))`。
- [x] **1.2 外观设置中增加应用语言切换**
  - 在 `AppearancePreferences.kt` 中增加 `appLanguage` 偏好项，预设选项包括：`zh-CN` (简体中文 - 默认)、`system` (跟随系统)、`en` (English)。
  - 在 `AppearancePreferencesScreen.kt` 中呈现语言下拉选择器，变更时即刻更新 ApplicationLocales。
- [x] **1.3 补齐音频、网络与 AI 相关词条的本地化资源**
  - 更新 `app/src/main/res/values/strings.xml`（英文基准）。
  - 全面更新并补充 `app/src/main/res/values-zh-rCN/strings.xml`（简体中文），覆盖 WebDAV 音频浏览、专辑封面、歌词面板、自定义 AI 提供商（Base URL, API Key, Model）及双语字幕翻译选项。

---

## Phase 2: WebDAV 音频格式解析与网络流代理打通 (WebDAV Audio Browsing & Streaming)

- [x] **2.1 扩展媒体格式判定与类型工具**
  - 检查并利用 `FileTypeUtils.AUDIO_EXTENSIONS`。
  - 在 `NetworkFile` 上扩展 `isAudioFile()` 与 `isPlayableMediaFile()` 方法，支持识别 `.mp3`, `.m4a`, `.flac`, `.wav`, `.ogg`, `.opus`, `.aac`, `.mka`, `.ape`。
- [x] **2.2 改造网络文件浏览器数据模型与列表视图**
  - 重构 `NetworkBrowserViewModel.kt` 的文件过滤逻辑，用 `isPlayableMediaFile()` 替换单纯的 `isPlayableVideoFile()`。
  - 允许播放列表集合中纳入同目录的全部可播媒体（视频 + 音频）。
- [x] **2.3 升级媒体卡片以适配音频属性与图标**
  - 改造 `NetworkVideoCard.kt`，当文件为音频类型且无视频缩略图时，显示专用的音频图标（如 `Icons.RoundedFilled.Audiotrack`）。
  - 在卡片上展示音频格式标签（如 FLAC、MP3、M4A）与文件大小。
- [x] **2.4 优化网络流代理服务 (NetworkStreamingProxy)**
  - 在 `NetworkStreamingProxy.registerStream` 中根据文件扩展名动态分配合规的 MIME 类型（如 `audio/mpeg`, `audio/flac`, `audio/mp4`）。
  - 确保音频 HTTP 206 Partial Content Range 请求的平稳响应，支撑无损音频的大步长 Seek。
- [x] **2.5 打通网络音频播放启动 Intent 与参数分发**
  - 在 `NetworkBrowserViewModel.playVideoInternal` 中：若所点选文件为音频，向 Intent 附带 `putExtra("is_audio", true)` 并设置精准的 `setDataAndType(uri, "audio/*")`。
  - 携带同目录音频播放列表索引与路径，保证下一曲/上一曲的连续性。

---

## Phase 3: 音频播放专属交互与封面展示 (Audio Playback UX & Cover Display)

- [x] **3.1 封面提取与网络同目录封面探测**
  - 通过 `mpvlib` 专辑封面检测（`track.isAlbumArtwork`）或 `MediaMetadataRetriever` 解析内嵌 ID3/FLAC 封面。
  - 扩展 `SubtitleOps` 类似机制，在播放 WebDAV 音频时探测同目录下存在的 `cover.jpg`, `cover.png`, `folder.jpg`，并加载为封面。
- [x] **3.2 打造音频播放专用主视图组件 (AudioPlayerOverlay)**
  - 创建 `AudioPlayerOverlay.kt`，当 `isAudioOnly` 为真时呈现。
  - 布局包含：居中圆角卡片专辑封面、动态微光环绕（Ambient Glow）、曲目标题（支持走马灯长文本）、艺术家与专辑信息。
  - 无封面时平滑降级为律动波形（BlobOverlay）或精致音乐唱片占位。
- [x] **3.3 适配精简版音频控制条**
  - 在 `PlayerControlsPortrait.kt` 和 `PlayerControlsLandscape.kt` 中添加针对 `isAudioOnly` 的渲染分支。
  - 自动隐藏视频专属控件（画面比例、视频缩放、着色器、硬件解码、视频帧导航等）。
  - 突出呈现：上一曲、下一曲、播放/暂停、循环模式、随机播放、播放速度调节与歌词面板快捷开关。
- [x] **3.4 播放列表导航与后台播放强化**
  - 确保网络音频在屏幕关闭或切至后台时通过 `MediaPlaybackService` 保持平稳串流与通知栏控制器联动。

---

## Phase 4: 实时歌词/字幕联动系统 (Real-time Synced Lyrics)

- [x] **4.1 自动扫描与挂载 WebDAV 同目录音频同名歌词文件**
  - 在 `SubtitleOps.autoloadSubtitles` 中，确保音频播放时也能扫描同目录同名的 `.lrc`, `.krc`, `.srt`, `.vtt` 歌词文件并通过 Proxy 自动执行 `sub-add`。
- [x] **4.2 实现轻量级歌词解析与模型结构**
  - 建立 `LyricLine` 与 `LyricsDocument` 数据结构。
  - 编写健壮的 LRC 时间戳解析器（支持 `[mm:ss.xx]` 及单行多时间戳、双语内联）。
- [x] **4.3 打造时间轴同步高亮与可拖曳滚动的歌词面板 (LyricsOverlay)**
  - 实现沉浸式歌词面板，监听 `MPVLib.propInt["time-pos"]`，实时计算并高亮当前唱到的行。
  - 支持垂直滑动查看上下文，并能在停止触摸数秒后平滑自动回弹居中至当前播放行。
- [x] **4.4 点击歌词跳转播放进度 (Tap-to-Seek)**
  - 支持用户点击歌词文本中的任意一行，触发 `viewModel.seekTo(...)` 精准跳转至对应时间点播放。

---

## Phase 5: 自定义 AI 提供商 (Custom Base URL + API Key + Model)

- [x] **5.1 扩展 AI 偏好设置数据结构**
  - 在 `AiPreferences.kt` 中向 `AiProvider` 枚举增加 `CUSTOM`。
  - 增加 `customBaseUrl`, `customApiKey`, `customModel`, `customAvailableModels`, `bilingualSubtitleTranslation` 等偏好存取属性。
- [x] **5.2 实现通用 OpenAI 兼容客户端适配器 (CustomAiClient)**
  - 基于 OkHttp 实现 `CustomAiClient.kt`，满足 `AiClient` 接口规范。
  - 实现动态 URL 请求：模型列表获取 `GET {baseUrl}/models`、生成接口 `POST {baseUrl}/chat/completions`。
  - 实现连通性与 API Key 测试探针。
- [x] **5.3 改造 AI 设置界面 (AiIntegrationScreen.kt)**
  - 在提供商选择下拉菜单中增加“自定义 (OpenAI 兼容)”选项。
  - 选中后展开：Base URL 输入框（预填或默认支持各类中转网关/DeepSeek/Ollama）、API Key 输入框、Model 名称（支持“获取模型列表”下拉选择或手动自定义输入）。
  - 提供“测试连接”功能，即时展示连通性状态。
- [x] **5.4 在 AiService 中接入 CustomAiClient**
  - 在 `AiService` 的 clients 映射中注册 `CUSTOM` 客户端。
  - 确保模型校验、标题优化、重命名和字幕翻译均可无缝路由至自定义端点。

---

## Phase 6: 字幕/歌词双语 AI 翻译引擎 (Bilingual AI Translation)

- [x] **6.1 扩展翻译 Prompt 体系支持双语输出格式**
  - 在 `AiPrompts.kt` 中设计双语字幕/歌词专用 Prompt，针对 SRT、VTT、LRC 分别规定原语在先、译文在后的结构化输出规范。
- [x] **6.2 改造 AiService 字幕翻译执行逻辑**
  - 在 `AiService.translateSubtitle` 中引入双语模式分支。
  - 优化分块切片算法与断点恢复逻辑，确保长字幕/多行歌词分块在多语言混排时不破坏时间戳格式。
- [x] **6.3 播放器字幕翻译流程与落盘规则适配**
  - 在 `PlayerViewModel.translateSubtitle` 中识别双语配置，生成新文件如 `<name>.<lang>.Bilingual.AI.<ext>`。
  - 保存后自动执行 `sub-add` 选定新翻译的双语字幕轨。
- [x] **6.4 歌词面板呈现双语对照**
  - `LyricsOverlay` 检测到双语字幕/歌词时，在每行原文下方优雅排版呈现译文字体，实现双语联动同步高亮。

---

## Phase 7: 质量门禁与端到端集成验证 (Verification & Quality Gate)

- [x] **7.1 单元测试与数据解析验证**
  - 编写 LRC 歌词解析测试用例（标准 LRC、内联双语 LRC、特殊格式）。
  - 编写网络音频文件识别与 MIME 类型匹配测试。
- [x] **7.2 WebDAV 音频流播集成测试**
  - 验证 WebDAV 挂载下 MP3/FLAC/M4A 音频流播放、Range 拖拽 Seek、同名歌词自动挂载链路。
- [x] **7.3 自定义 AI 接口与双语翻译契约测试**
  - 验证自定义 Base URL + API Key + Model 的 `/models` 获取与 chat completions 生成。
  - 验证双语 SRT 及双语 LRC 翻译输出的时间轴对齐准确性。
- [x] **7.4 界面语言与交互自检**
  - 验证全新安装下直接进入简体中文界面。
  - 验证音频播放时控制条适配无多余视频按钮、封面与歌词实时联动正常。
