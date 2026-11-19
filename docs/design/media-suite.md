# Android 媒体体验重设计

静态视觉稿：[媒体套件](media-suite-preview.svg)。现状以 `weifu-refactor` `3696f7f3` 为基线。本文是实验设计，不代表 Android 功能已经上线。

## 设计目标

同一套深色媒体界面、薄荷绿强调色、中性占位图、统一筛选条和反馈状态。不是四个页面都铺同一种卡片：比例不规则的内容走瀑布流，强调信息扫读的内容走等高列表。宽屏可增加栏数；大字模式优先减少列数而不是截断重要操作。可点击控件最小 48dp，按 TalkBack 阅读顺序安排标题、状态和操作。Astryx 的 Library、Classic Gallery、Thumbnail States、Lightbox 是交互和状态参考；Android 实现继续用原生 Compose / View，不嵌 Web UI。

| 页面 | 默认布局 | 必须保留的行为 |
|---|---|---|
| 频道分类 | 顶部「频道 / 本地视频」导航，下面电影、夜色、自定义筛选；双列受控瀑布流 | 保留专区开关、分页、登录/VIP 路由以及分类独立滚动位置。缺封面用带标题的中性占位，不伪造图。初始加载、空数据和加载失败各自独立。 |
| 视频列表 | 等高 16:9 缩略图行：时长、标题、续播进度；排序与未看筛选。可选海报双列，但不让用户失去当前位置 | 只展示可信元数据；续播读现有进度；本地、远程、懒加载入口不改。首版不启用多路列表自动播放。 |
| 播放页 | 竖屏视频在上，进度、字幕、倍速、弹幕在下；横屏顶部导航、中部播放、底部时间轴，自动隐藏 | DLNA 与收藏保留在右下工具组；锁屏后停用手势；字幕、弹幕、操作条分层。画中画默认不画评论层；投到电视时不暗示弹幕也被投过去。 |
| 图集 | 图集封面用双列受控瀑布流；图集内用规则缩略图网格；全屏阅览支持滑动、缩放、位置计数 | 用稳定 ID 从第 N 张打开，返回原滚动位置；可预取相邻图，按需解码原图。下载/收藏沿用既有能力；图集图片不自动授予分享权限。 |

## 原生接入

- 频道：先替换 `ChannelsScreen` 的表现层，保留 `ChannelsViewModel`、`ChannelEntries` 和 `LegacyChannelEntries`；测试切换「是否显示专区」再回前台。
- 视频列表：`VideoListShowActivity` 当前挂载 `GSYAutoVideoListFragment`。先拆数据分页、播放导航和列表呈现，再替换呈现层，避免把 feed 自动播放和详情播放会话绑在一起。
- 图集：`ImgChannelImgListFragment` 仍继承 `RecommendFragment`，`ImgItemFragment` 承担图片详情交互。拆成 `AlbumGrid` / `AlbumContents` / `ImageViewer`，先通过适配器接现有数据，不要求服务端同步改版。共享卡片、占位、筛选和滚动状态，不把图片与视频硬塞进一个 ViewModel。
- 播放页：`DetailGSYVideoPlayer` 和两个 Activity 继续握住播放生命周期。建立只读 `PlaybackUiState`、动作入口和能力表：seek、换轨、字幕、投屏、PiP 等按当前源/内核决定是否可用。新控制层读这些接口，无需更换 Exo/IJK。保留 `MyFavoriteView` 已有的投屏回调边界；横屏重建只接同一会话，不重复创建播放器或弹幕请求。
- 字幕分三条来源：内嵌/外置字幕、既有实时字幕、实验性设备端转写。设备端先限定本地文件：音频提取与转写后台执行，模型可选下载，支持取消；产出 SRT/VTT 并按媒体版本缓存。现有实时翻译不是离线方案。先测中端设备的实时系数、内存和耗电，再决定是否推流式；不自动上传本地音频。

## 通用弹幕：播放时间轴服务，不是播放器插件

**三种「取消」是不同操作**：关闭显示立即清屏并停预取，不删内容；关闭输入放弃草稿，不发请求；撤回自己的已发弹幕会同步给其他客户端，报告别人的弹幕则走举报。发送时优先固定打开输入框的播放时间，显示 `18:42`，避免打字时视频在走导致评论贴错场景。

```text
TimedCommentSource ─→ CommentWindowStore ─→ TimelineScheduler ─→ Renderer
                                   ↑            ↑
                             收发/撤回       PlaybackClock
```

- `MediaTimelineKey = namespace + contentId + editionId`；同一内容的不同 URL 可以共用时间轴，不同剪辑默认不混。私有 `content://` 使用本地命名空间，MVP 不上传 URI/路径、不跨用户共享。
- `TimedComment(id, timelineKey, atMs, text, authorDisplay, style)`。来源接口提供 `loadWindow(startMs,endMs,cursor)`、`post(clientMessageId,atMs,text)`、`retract(id)`；本地样例源和服务端源共用渲染器。服务器推送不是 MVP 前提：先分窗拉取、分页和增量刷新；待引入推送时，仍以窗口重载兜底。
- 使用播放位置而不是墙钟。暂停/缓冲冻结；变速跟媒体时钟；跳转清空旧轨、取消过期请求，从新窗口重新排队；过期回包按会话和窗口代数丢弃。不逐条触发 Compose 布局；渲染器自己做密度上限、碰撞和字幕避让，App 在后台时停止绘制。
- 发送状态显示发送中/已发送/失败可重试；重试沿用幂等 ID。服务端校验登录、媒体权限、文本长度与频率；撤回权限只给作者或审核方，刷新收到撤回标记即移除。公测前应具备举报、屏蔽、审核和限流。MVP 先用纯文本滚动弹幕和开关，颜色、顶部固定、跨端实时同步后加。
- 建议验收：两台客户端同一时间轴看到一条弹幕；重试不重复；撤回后另一端刷新不再展示；seek/暂停/倍速正确；关闭显示不影响字幕与播放；横竖屏切换不重复请求；切到本地视频不串远端弹幕。

## 参考留档

- [NextPlayer](https://github.com/anilbeesetti/nextplayer) `85ae39e6`：本地源码有顶/中/底控制区，独立字幕、音轨、解码器、章节、倍速选择，以及手势与拖进度测试。是播放器布局与测试设计的首选参考。
- [mpvExtended-android](https://github.com/XIONGPEILIN/mpvExtended-android)：上游定位 NAS 播放器，适合再对照网络源和高级能力组织；本轮未核验其控件实现。[Nova / aos-AVP](https://github.com/nova-video-player/aos-AVP) `5c5d4ec` 作为媒体库、海报浏览和元数据呈现参考。
- [PixelPlayer](https://github.com/PixelPlayerHQ/PixelPlayer) `515c7d35`：本地检出为音乐播放器，有播放控制和叠层组件；未找到用户所指的离线视频字幕模块。[whisper.cpp](https://github.com/ggml-org/whisper.cpp) 可作为独立转写实验候选，当前未集成。
- 图集对照 [Aves](https://github.com/deckerst/aves) 的媒体管理方向，以及 [Fossify Gallery](https://github.com/FossifyOrg/Gallery) 的相册和图片浏览方向。本稿没有移植第三方源码或图片。

## 切入顺序与发布门槛

先用 `频道 → 视频列表 → 新播放控件` 在一个实验入口跑通真机纵切，保留旧路径作为对照；图集复用已站稳的筛选、图片状态和返回定位。弹幕先跑假数据再接真实收发；离线字幕单列性能实验。验证本地 content URI、远程/懒加载、手势与返回横竖屏、DLNA、字幕和断网恢复，再决定把新入口设为默认。当前交付仅静态设计稿与实施规格，未构建 APK。
