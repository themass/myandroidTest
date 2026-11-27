# AdMob 广告台账（20261125）

用户亲令：「我原来在很多地方增加了广告，现在都不见了。」本台账逐条给出
**原来在哪 → 怎么没的 → 恢复落点与最小方案**。基线 `origin/weifu-refactor`
tip（本分支 HEAD 起点 6da4aee9）。所有 file:line 均为该基线上的实际位置。

## 0. 总判断（先给结论）

广告**基础设施从来没被删**：依赖、app id、广告位 id、`AdsManager`/AdMob 实现、
启动页开屏调用、5 个频道页的插屏调用、文字列表的内嵌 banner，今天都还在仓库里
（见 §1）。用户「全都看不见」是三类原因叠加，按影响面排序：

1. **debug 总闸（影响四处，最直接）** — 2026-11-21 `28b2226e`
   `fix(app/common): 通话自动重连信号去 saveable + debug 构建关闭广告` 新增
   `common/src/main/java/com/openapi/common/ads/base/AdsGate.kt`：debug 构建下
   `AdsManager` 不初始化，且 `showBannerAds`/`showSplashAds`/`showInterstitialAds`/
   `showNative`/`reqVideo` 全部第一行 `if (!AdsGate.enabled()) return;`
   （`common/.../base/AdsManager.java:50,92,97,102,107,112,118,123,128,133,138`）。
   测试机/开发机装的签名都是 debug → 所有广告一律不加载、不展示。
   这条是 P2-13 真机验收踩坑后刻意加的（chat debug 包冷启动弹全屏 GMA AdActivity，
   误触跳 googleadservices 证书报警页），`840f16a8` 又补了 exit* 漏闸导致的 NPE。
2. **插屏概率器有「进程内永久关闭」缺陷（影响所有插屏，release 也中招）** —
   `common/.../base/AdsContext.java:139-144`
   ```java
   public static boolean rateShow() {
       if (showCount++ > 8) return false;      // showCount 全仓无复位点
       int i = Md5.getRandom(AdsIds.maxRate);  // nextInt(10)
       return i <= AdsIds.PROBABILITY;         // <=3 → 40%
   }
   ```
   `showCount` 是 `private static int`（:26），**没有任何复位**（全仓仅 :26 声明、
   :140 使用）。进程启动后每调一次 `showRand` 自增，第 9 次起**整个进程生命周期内
   所有插屏永久返回 false**。冷启动进几个页面后，插屏就再也不会出现——这条与
   debug 闸无关，release 同样发生。
3. **页面迁移到 Compose 时没有搬广告槽位（影响 AD2 首页、AD3 放映场/图集正文、AD4 chat）** —
   旧 View 体系把广告挂在基类（`BaseBannerAdsActivity`）和行布局的 `rv_ads` 槽上，
   新 Compose 页面既不继承基类也没有槽位，广告随宿主一起消失。

三类原因**互不覆盖**：只放开 debug 闸（1）修不了（2）（3）；修好（2）也看不到（3）。

## 1. 现存基础设施（恢复的地基，未改动）

| 项 | 位置 | 状态 |
|---|---|---|
| 依赖 | `common/build.gradle:49` `api 'com.google.android.gms:play-services-ads:24.9.0'` | 在 |
| app id | `app/src/main/AndroidManifest.xml:97-98` `com.google.android.gms.ads.APPLICATION_ID = ca-app-pub-6599725010915516~2513490769` | 在 |
| 广告位 id | `common/src/main/java/com/openapi/common/ads/AdsIds.java`：SPLASH `…/3521969031`、INTER `…/9330697473`、BANNER `…/4233766548`、BANNER2 `…/6148132379`、REWARD `…/5135784679`（另有 3 组 `ca-app-pub-7849865307083433/…` 与 Google 官方 test id） | 在 |
| SDK 初始化 | `app/src/main/java/com/openapi/app/MyApplication.java:145` `AdsManager.getInstans().init(this)`；闸写入 `:58-59` | 在（debug 被闸挡） |
| AdMob 实现 | `common/.../ads/admob/AdmobAdsManager.java`、`AdmobSplashAds.java`（AppOpenAd）、`AdmobInterstitialAds.java`、`AdmobBannerAds.java`（anchored adaptive） | 在，可用 |
| adview 实现 | `common/.../ads/adview/*` | **空壳**：方法体整段注释（如 `BannerAdviewAds.java:25-60`），仓内无 adview SDK 依赖 → ADVIEW 通路调用即无事发生 |

> 关键含义：`AdsContext.showRand` 一律走 `AdsFrom.ADMOB`（:168/:173/:178），所以
> adview 空壳**不是**广告消失的原因；但 `BaseBannerAdsActivity.getBannerAdsFrom()`
> 默认返回 ADVIEW（:134）且该值当前未被 `showAds()` 使用（`showAds()` 走 ADMOB，:129），
> 恢复 banner 时不要误接回 ADVIEW。

## 2. AD1 开屏广告（App Open Ad）

**原来在哪**
- 现行代码仍在：`app/src/main/java/com/openapi/app/LaunchActivity.kt:95-99`
  `showAdview()` → `AdsManager.showSplashAds(AdsFrom.ADMOB, this, rlSpread, skipView)`；
  退出清理 `:91`；ADVIEW 续投 `:111-115`（空壳，见 §1）。
  实现 `common/.../admob/AdmobSplashAds.java:44-97`（`AppOpenAd.load` + `show`）。
  容器 `app/src/main/res/layout/main_launch.xml`：`rl_spread`（全屏）、`skip_view`。
  入口链：manifest `activity-alias`（`app/src/main/AndroidManifest.xml:128-138`）→
  `LaunchActivity` → `HomeActivity`。
- 历史上它一直随启动页存在：`git log -S showSplashAds` 从 `29864cab`
  (2023-04-24 `admob&mobvista`) 起连续存在，Kotlin 化在 `632b3834`
  `Stage 8: LaunchActivity becomes Kotlin`，**没有删除提交**。

**怎么没的** → **条件闸不满足**（原因 1）：此前 debug 包 `showSplashAds` 直接 return，
`init` 也被挡。用户已于 2026-11-25 定夺总闸恒开（见 §6 D1），该条件闸已取消。
另有一条独立旁路：带 `script_case` extra 启动时 `LaunchActivity.kt:52-55`
主动跳过开屏（adb 验收用），此契约保留。

**恢复落点与最小方案**
1. 开屏代码原本仍在；总闸恒开后，debug 与 release 都走既有启动调用。
2. `script_case` 仍跳过开屏，以免打断自动化验收；其他环境不因构建类型关闭广告。
3. 附带修正：`AdmobSplashAds.launchAds` 载入失败走
   `postLaunchAdsNext`，但成功路径不通知倒计时；`LaunchActivity` 倒计时
   `max = Constants.STARTUP_SHOW_TIME_6000 + 1000` 与广告展示无耦合——广告弹出期间
   倒计时仍在跑，广告关闭后可能立即 `launch()`。恢复后若要「广告关闭即进主页」，
   在 `observeAdEvents()` 的 `splashDismissed()` 分支里 `launch()` 即可（不改 SDK 层）。

## 3. AD2 插屏广告（首页/主干入口）

**原来在哪**
- 旧首页：`app/src/main/java/com/openapi/ks/chatfree/ui/main/MainFragmentViewPage.java:133`
  （删除前快照，`git show eefb98a9^:…`）`setupView()` 里
  `AdsContext.showNext(MainFragmentViewPage.this);` —— 进主页即弹插屏。
- 主干承载点在基类：`core/.../legacyui/base/CommonFragmentActivity.java:53`
  读 `INTERSTITIAL_ADS_SHOW`，`:129-130` `setupView()` 里 `AdsContext.showRand(this)`。
  今天仍活着，并由这些调用方置 true：
  `core/.../config/Routes.java:61`（全局搜索）、
  `feature-content/.../video/VideoChannelListFragment.java:34`、
  `VideoChannelUserListFragment.java:34`、`VideoChannelUserItemsListFragment.java:32`、
  `image/ImgChannelImgListFragment.java:35`、`text/TextItemsFragment.java:87`、
  `text/TextItemsWebViewFragment.java:77`、`text/TextChannelListFragment.java:47`、
  `sound/SoundItemsMusicFragment.java:205`。
- 频道页自身插屏（非基类，仍在）：`feature-content/.../video/MovieChannleActivity.java:50`、
  `VideoChannleActivity.java:50`（`showRand(this, CATEGREY_VPN2)`）、
  `image/ImgChannleActivity.java`（`setupView` 内 `showRand(this)`）、
  `text/TextChannleActivity.java:50`、`sound/SoundChannleActivity.java:41`、
  `core/.../legacyui/base/WebViewActivity.java:72`（受服务端 `adsPopShow` 驱动）。
- 服务端驱动的开关链（仍在）：`core/.../model/RecommendVo.java:26-27`
  `adsShow`/`adsPopShow` → `feature-content/.../common/RecommendFragment.java:147-148`
  放进跳转 param → `feature-content/.../config/ConfigActionJump.java:93-98` 转成
  Intent extra；版本级总开关 `core/.../update/VersionUpdater.java:43`
  （`VersionVo.adsShow`）→ `CommonConstants.ADS_SHOW_CONFIG`。
  **注意**：`adsShow/adsPopShow` 只有走 `RecommendFragment` 的卡片才有值，走新
  Compose 正文（§4）的卡片不再携带 → 服务端闸门实际只剩旧路径生效。

**怎么没的**
- 首页那一路：**删了** @ `eefb98a9`（2026-11-06 `Update build config: version catalog,
  drop dead messagekit module and dependencies.gradle`）——同 commit 删掉整个
  `chatfree/ui/` 旧首页体系（`MainFragmentViewPage` 等），新
  `app/src/main/java/com/openapi/app/home/HomeActivity.kt` +
  `home/presentation/HomeScreen.kt` **无任何广告调用**（全文件 grep `ads` 为空）。
  属「迁移没搬」而非「有意关广告」。
- 其余插屏宿主仍在，但被 §0 的原因 1（debug 闸）与原因 2（`showCount` 永久关闭）压掉。
- 另：插屏**时机闸门** `AdGate.java`（VPN 操作后 20s 静默、两次插屏间隔 ≥45s）只存在于
  `4ff9d4e5`（`广告时机闸门 + 修正隐私政策/删号页地址`），该 commit **不在
  weifu-refactor 系谱**（`git merge-base --is-ancestor 4ff9d4e5 HEAD` → NO，仅在
  `origin/global-vpn`）。所以主干从来没有时机闸门；恢复插屏时**必须补**，
  否则会复现该 commit 描述里点名的拒审风险（插屏打断核心操作 / 连弹）。

**恢复落点与最小方案**
1. 首页补一处插屏：落点 `app/src/main/java/com/openapi/app/home/HomeActivity.kt`
   `onCreate`（或 `HomeScreen` 首帧后），调用集中在纯逻辑闸门后
   （`AdsContext.showRand` 保留）。要求：进程内最多 1 次、且不在开屏广告刚关闭时弹
   ——正好用 §7 的 `AdsShowPolicy` 钉。
2. 把 `4ff9d4e5` 的时机闸门以纯 Kotlin 形式移植进 `common/ads`
   （静默期 + 最小间隔），取代散在各页的裸调；`feature-content` 各页调用点不变。
3. 修 `rateShow` 永久关闭：见 §7。

## 4. AD3 电影 / 图集 / 文章页的插屏 + banner

分三条页种，**结论各不相同**：

### 4.1 电影（movie）
- **插屏在**：`feature-content/.../video/MovieChannleActivity.java:50`
  `showRand(this, CATEGREY_VPN2)`；由 `ConfigActionJump.java:57`
  `movie://` → `MovieChannleActivity` 注册可达。
- **banner 在（基类路）**：`MovieChannleActivity extends BaseFragmentActivity`
  → `core/.../legacyui/app/BaseBannerAdsActivity.java:113` `onResume` → `:127-131`
  `showAds()` → `AdsManager.showBannerAds(this, flBanner, getBannerCategrey())`（ADMOB），
  `:153` `onDestroy` → `exitBannerAds`；banner 容器 =
  `core/src/main/res/layout/base_fragment.xml:25` `fl_header`（`flBanner` 绑定点
  `BaseBannerAdsActivity.java:59`）。`MovieChannleActivity` 覆写 `needShow() = true`（:40）。
- **但用户实际走的新页没有广告（关键）**：旧频道页代码与槽位**全部健在却已不可达**——
  首页「频道」标签 2026-11-23 起内嵌 Compose 放映场（`db1da24f`
  `feat(screening): 放映场迁 main 源集`，`app/.../home/presentation/ChannelsHub.kt:18`
  → `ScreeningRoute()`）；旧频道首页桥 `LegacyChannelEntries`（它会
  postConfigAction → `ConfigActionJump` → `MovieChannleActivity`）在 `e5c5ce32`
  `chore(home): 旧频道页死代码整链删除` 删除；放映场又吸收了 `movie://`/`teleplay://`
  协议（`1b5d9f7e`，`ScreeningFeed.kt:103` `SUBCHANNEL_SCHEMES`）。
  `ConfigActionJump` 仍注册 `movie://`，只有未被吸收的 scheme 才会落到旧页。
  新放映场
  `feature-video/src/main/java/com/openapi/feature/video/screening/ScreeningActivity.kt:78`
  是 `ComponentActivity` + `setContent`（:86），列表在 `:424`/`:539`/`:792`/`:801` 的
  `LazyColumn`/`items`，**全文件无任何 ads 引用**（grep 确认）。
  电影分类卡改走 `videoList1://` → `VideoListShowActivity`（`ConfigActionJump.java:53-54`），
  后者 `extends BaseFragmentActivity`（`feature-video/.../page/VideoListShowActivity.java:27`）
  → 基类 banner 与 `INTERSTITIAL_ADS_SHOW` 可用，但**没有任何地方为它置 true**
  （`Routes.globalSearch` 是唯一的 true 调用方）。
  → 结论：**迁移漏了**（新放映场无槽位）+ **条件闸不满足**（VideoListShow 未置 extra）。

### 4.2 图集（image）
- **插屏在**：`feature-content/.../image/ImgChannleActivity.java`（`setupView` → `showRand`）。
- **正文 banner / native 没了**：列表正文已由 Compose 承接 ——
  `feature-content/.../image/ImgChannleBodyFragment.java extends ChannelBodyFragment`，
  而 `common/ChannelBodyFragment.kt` 用 `ComposeView` + `ChannelBodyScreen`
  （迁移 commit `05d38cc0` `Move WebView shell and channel body screens to Compose`），
  `ChannelBodyScreen.kt` 里**没有广告槽位**（grep `ads` 为空）。
  旧 Java 列表里的 native 广告调用已被注释：
  `image/ImgChannelImgListFragment.java:104` `// AdsManager…showNative(...)`（同类注释：
  `video/VideoChannelListFragment.java:106`、`GlobalVideoChannelListFragment.java:90`、
  `VideoChannelUserListFragment.java:65`、`VideoChannelUserItemsListFragment.java:67`）。
  → 结论：**迁移漏了**（正文换 Compose 后无槽）+ **注释停用的 native 通路**
  （且 native 只有 ADVIEW 实现，§1 已空壳 → 即便解注释也拉不到广告，必须换 ADMOB 或
  改成 banner 槽）。

### 4.3 文章（text）
- **插屏在**：`text/TextChannleActivity.java:50`；条目页
  `TextItemsFragment.java:87` / `TextItemsWebViewFragment.java:77` 置
  `INTERSTITIAL_ADS_SHOW` → `CommonFragmentActivity:129` 生效。
- **列表内嵌 banner 仍在且是活代码**：
  `text/TextChannelListItemsViewAdapter.java:77-91` ——
  `if (Constants.BANNER_ADS_POS.contains(position))` 打开 `rv_ads` 并按位置选
  `CATEGREY_VPN3/VPN2/VPN1`（:80/:84/:87），else 清空隐藏（:90-91）。
  位置表 `core/.../config/Constants.java:57`
  `BANNER_ADS_POS = [2,8,13,18,21,30,40,50]`；槽位布局
  `feature-content/src/main/res/layout/layout_channel_items_item.xml:51` `rv_ads`
  （`@dimen/banner_h`，默认 gone）。
  活路径确认：`TextChannleBodyFragment.java:47` → `TextChannelListFragment.startFragment`
  → `TextChannelListFragment.java:53` 用该 adapter。
  同族另一路仍在：`feature-user/.../setting/SettingCharacterAdapter.java:55-65`。
  → 结论：**没删**，被 §0 原因 1（debug 闸）压住；但新放映场的文字页
  `feature-video/.../screening/ScreeningText.kt`（对标 `TextChannelListFragment`）
  **无广告槽** → 新路径属迁移漏了。

## 5. AD4 chat 消息列表若干位置的 banner

（依 brief：本线**只写方案，不改** `feature-chat` presentation 与 `chatkit`——
这两处被协调会话三个 worker 占着。）

**原来在哪**
- 宿主是旧 MessageKit 体系（`messagekit` 模块的 `MessagesListAdapter`/`MessageHolders`
  + app 侧 `chat/ui/adapter/MyMessagesListAdapter`）。插入方式不是插 item，而是
  每条消息行布局内嵌 `RelativeLayout id=rv_ads` 槽，由 ViewHolder `onBind()` 开关。
  含 `showBannerAds` 的 chat 文件全历史仅 4 个 holder：
  `app/src/main/java/chat/ui/adapter/media/holders/`
  `MyInComingMassageHandler.java`、`MyOutComingMassageHandler.java`、
  `MyOutComingImageMessageViewHolder.java`、`MyOutComingFileViewHolder.java`
  （删除前快照 `git show f34e483f^:…`：如
  `MyInComingMassageHandler.java:46` 绑 `rv_ads`、`:134` 展示调用；
  `MyOutComingImageMessageViewHolder.java:30` `static AtomicInteger counter`、
  `:70-73` `rvAds != null && counter.get()<2` + `counter.addAndGet(1)`）。
- 规则：服务端 `message.showAds` 标记 + **每类 holder 最多 2 条**（`counter<2`），
  类别 `AdsContext.Categrey.CATEGREY_VPN2`。
  引入 `ca63d4aa`(2025-02-07 `1.修改广告`)，同日补丁 `b1937f39` 改成 counter<2 规则
  （引入时除出向图片外即已注释 → 长期是「休眠代码」）；文件消息路 `389003ae`
  (`1.支持文件&解题`)。
- 聊天页顶部另一路：`chat/ui/CustomChatMessagesActivity.java`（f34e483f^ 视图）
  `:353` `BannerHeaderFragment.getNewInstans(CATEGREY_VPN)`、`:347`/`:403`/`:613`
  插屏；顶部 banner 实现 `app/src/main/java/com/openapi/ks/myapp/ui/base/BannerHeaderFragment.java`
  （`flBanner` + `AdsManager.showBannerAds`）。
- 旧首页的 tab 顶 banner 同族：`chatfree/ui/maintab/TabChatMessagesFragment.java`
  （`getTabHeaderView() = BannerHeaderFragment.getNewInstans(CATEGREY_VPN3)`），
  基类 `TabBaseAdsFragment`。

**怎么没的**（HEAD 系谱上逐段确认，`git merge-base --is-ancestor`）
| commit | 日期 | 删掉的东西 |
|---|---|---|
| `f34e483f` | 2026-11-06 | `Convert login, settings, drawer menu and video cards to Compose; replace legacy chat` —— **AD4 主体死亡点**：删 `CustomChatMessagesActivity`、`MyMessagesListAdapter`、全部 `holders/My*.java` 与含 `rv_ads` 的 `item_custom_*_new.xml` 消息行布局 |
| `eefb98a9` | 2026-11-06 | 删整个 `messagekit/` 模块（其自身从无广告代码） |
| `b43e7f66` / `5c618622` | 2026-11-09 | `Remove three unreferenced legacy classes (BannerHeaderFragment, …)` —— 顶部 banner 碎片 |

叠加 §0 原因 1：即使旧代码在，debug 包也不展示。

**今天的渲染路径（无广告位）**
- 列表构建：`feature-chat/.../presentation/ui/ChatScreen.kt:129`
  （`ChatItemMapper.map(...)` → `List<ChatItem>`）→ `:213` `ChatMessageList(items …)`。
- LazyColumn：`chatkit/src/main/java/com/openapi/chatkit/message/ChatMessageList.kt:231`，
  行渲染 `:237` `itemsIndexed(items, key = { _, it -> it.key })` → `:252` `ChatMessageItem`。
- 模型 `chatkit/.../model/ChatItem.kt:73` 纯消息模型；`chatkit`+`feature-chat` 全目录
  grep `banner|ads` 为空（唯一命中 `chatkit_editing_banner` 是编辑提示条）。
- `HomeActivity` / 底部 tab（`HomeScreen.kt:99` `enum HomeTab`、`:207-234` 导航）无广告槽。

**恢复落点与最小方案（等协调线放行后再动手）**
1. 在 `ChatMessageList.kt:231-237` 的 `LazyColumn` 里插 banner item：
   `ChatItem` 增一个非消息的 Ad 变体（或 `MessagePart` 增 Ad 子类），在 `itemsIndexed`
   分支渲染 `AndroidView` 包一个 `ViewGroup` 喂给现存
   `AdsManager.showBannerAds(FragmentActivity, group, categrey)`（它是 View 体系、要
   FragmentActivity），或复用现成 `itemOverlay` 插槽（`ChatMessageList.kt:114`）。
2. 插入位置规则由 `feature-chat/.../ui/ChatItemMapper.kt:25` 计算，对齐旧规则
   （`showAds` 标记 + 每类 ≤2）或仓内幸存的同族规则
   （`Constants.BANNER_ADS_POS` / `position % N`，见 `TextChannelListItemsViewAdapter.java:77-88`、
   `SettingCharacterAdapter.java:55-62`）——规则本身写成 §7 的纯函数，可先测后接。
3. 约束：LazyColumn 有 `key` 唯一性要求（banner item 需稳定唯一 key），且 key 集合变化
   会触发贴底逻辑（`ChatMessageList.kt:188-228`）；滚动中途插入 banner 要避开贴底抖动。
4. 复用 `CATEGREY_VPN2`（旧 chat 用的就是它），不新增 id。

## 6. 需要用户/协调线定夺的事（不猜）

- **D1 debug 闸要不要放开**：**已定（用户 20261125 当面亲令「一直开，忽略debug」）**
  —— 采纳方案 (b)：`AdsGate.enabled()` 恒为 `true`，debug 与 release 一律加载并
  展示广告。`28b2226e`（P2-13：debug 冷启动弹全屏开屏/插屏、误触跳
  googleadservices 证书报警页）带来的验收干扰由用户明确接受。
  该决定由 `AdsGateTest` 钉住（任何人把闸改回按构建类型关会红），`debugBuild`
  标记保留给日志/诊断但闸门不再读它。
  **剧本房验收的干扰改在 `script_case` 这一层挡**：启动页原本就跳过开屏倒计时，
  新增 `InterstitialCadence.scriptQuiet`（`HomeActivity.onCreate` 见
  `script_case` 就置位）静音整个进程的插屏——放映场下钻与首页插屏都查它。
- **D2 广告位 id**：仓库里 id 齐全（§1），**无需**新 id；`adviewToAdmobBanner` 映射
  （`AdsIds.java:39-44`）已把三个 adview key 映射到两个 ADMOB banner id，
  恢复时直接可用。
- **D3 服务端闸门**：`RecommendVo.adsShow/adsPopShow` 与 `VersionVo.adsShow`
  是否仍下发 true 需要在服务端核对（本线只看到客户端消费侧）。若服务端已置 false，
  `WebViewActivity` 那条路即使放开客户端闸也不弹。

## 7. 本线已实施的第二步（门控纯逻辑 + 单测 + 恢复接线）

**门控纯逻辑（`common/.../ads/base/`，JVM 单测 `common/src/test/.../AdsShowPolicyTest.kt`）**
- `AdsShowPolicy.kt`：纯函数集合——概率口径（`hitProbability`，保持旧 4/10）、
  插屏时机（`allowInterstitial`：启动静默期 5s + 两次插屏最小间隔 45s，口径移植自
  仅存在于 `origin/global-vpn` 的 `4ff9d4e5`，主干从来没有）、banner 位置表命中
  （`bannerSlotAt`，沿用 `Constants.BANNER_ADS_POS`）、类别选择（`bannerCategreyAt`，
  抄 `TextChannelListItemsViewAdapter.java:80-88` 的 `position % 3` 口径）、
  chat 列表「每 N 条 + 上限」规则（`bannerPositionsEvery`，AD4 预留，未接线）。
- `RateBudget.kt`：窗口化限流，取代 `AdsContext.showCount`（修掉 §0.2 的
  「第 9 次起永久 false」；`AdsContext.rateShow()` 改为委托）。
- `InterstitialCadence.kt`：进程级时间戳持有者（noteStartup/noteShown/allow），
  时钟可注入。锚点在 `MyApplication.onCreate`。
- `AdsClock.kt`：可注入时钟接口。
- `AdsGate.kt`：**恒开**（§6 D1 用户亲令定案）——debug 与 release 一律加载展示；
  `debugBuild` 标记保留给诊断，闸门不读。`AdsManager` 保持「所有 map 访问前先过闸」
  的形状（`AdsManagerGateTest` 钉住），`init` 加 `inited` 幂等。
- 单测：`AdsGateTest` 钉总闸恒开；`AdsShowPolicyTest.kt` 钉剧本静音、预算窗口过期
  重开（旧实现永久 false）、概率边界、静默期/间隔、位置表与类别规则；既有
  `AdsManagerGateTest` 保持 map 访问前有闸的源码结构。

**恢复接线**
- **AD1 开屏**：代码零改动即恢复（§2：链路一直在，此前只是被闸压住）；闸恒开后
  debug 装机直接可见。剧本房（`script_case`）仍跳过开屏倒计时——那是既有
  adb 验收契约，不是广告开关。
- **AD2**：`HomeActivity.showHomeInterstitialOnce()` —— 进程内最多一次、过
  `InterstitialCadence` + `rateShow`、`script_case` 剧本房不弹、开屏关闭后 1.5s
  才尝试（不接力全屏广告）。旧「进主页必弹」刻意不恢复。
- **AD3**：`common/.../ads/ui/AdsBannerSlot.kt` —— Compose 内嵌 banner 槽
  （`AndroidView` + 现存 `AdsManager.showBannerAds`；
  LazyColumn 回收时 `onRelease` 销毁 AdView，补旧路泄漏）。
  `feature-video/.../screening/ScreeningAds.kt` 把纯函数规则接到槽上：
  - 片单列表（`ScreeningActivity` Library 海报网格 + 紧凑行）：`FeedGridAdSlot`/`FeedRowAdSlot`；
  - 文章目录（`ScreeningText.TextArticleList`）：`FeedRowAdSlot`（对标旧 `rv_ads` 槽）；
  - 图集正文（`ScreeningGallery.AlbumContents`）：`FeedGridAdSlot`；
  - 下钻插屏：`openChannel`/`openGallery`/`openText`/`openSound` →
    `tryInterstitialOnDrill`（对标旧频道页 `showRand`，必须过节律闸门）。
- **AD4**：按红线未动 `feature-chat`/`chatkit`；规则纯函数（`bannerPositionsEvery`）
  已备好并带单测，等协调线放行后接 `ChatMessageList`。
