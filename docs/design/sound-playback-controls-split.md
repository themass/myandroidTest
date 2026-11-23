# 拆分 SoundItemsMusicFragment 的播放控制（设计 + 测试方案）

状态：待实现（由 Cursor 实现，手机验证）
分支：`weifu-refactor`（当前 HEAD `3c35861f`，已推送）
相关：[refactor-plan.md](refactor-plan.md) 第 5.4 节；上一步 `3c35861f` 已拆出 `SoundPlaybackReceivers` 并合并了字幕绑定。

---

## 1. 目标

把 `feature-content/.../sound/SoundItemsMusicFragment.java`（564 行）里的**播放控制逻辑**拆到一个独立类 `SoundPlaybackControls`，让 Fragment 只剩：视图绑定、列表适配器、下拉加载、字幕/翻译接线、广播事件到界面的映射。

可量化的目标：

| 指标 | 现在 | 目标 |
|---|---|---|
| `SoundItemsMusicFragment.java` 行数 | 564 | ≤ 420（达不到时，在报告里写明实际值和原因，不改目标以外的代码） |
| 播放控制逻辑所在类 | Fragment 内 | `SoundPlaybackControls`（新建） |
| 播放控制逻辑能否在 JVM 单元测试中运行（不依赖 Android 视图和 Service） | 否 | 是 |
| 对外 public API（被适配器、其他类调用的方法） | — | **不变** |
| 运行时行为 | — | **不变**（见第 4 节不变量） |

**不做的事**（范围外，避免顺手改动）：
- 不改 `PlayService` / `MusicService`（`:retromusic` 不动）。
- 不改 `SoundDetailActivity` 的播放逻辑（它有自己的 PlayerReceiver，另一个任务）。
- 不修第 6 节列出的已知问题，只记录。
- 不改布局、字符串、图标资源。

---

## 2. 现状（需要搬走的部分）

以下方法在 `SoundItemsMusicFragment` 中（行号以 `3c35861f` 为准，实现前请重新核对）：

| 方法 | 作用 | 依赖 |
|---|---|---|
| `onClick(View)` 中的播放相关分支 | 播放模式、播放/暂停切换、上一曲、下一曲 | `playback.service()`、`current`、`adapter` |
| `resume()` | 继续播放，返回位置 | service、`ivPlay` |
| `pause()` | 暂停，返回位置 | service、`ivPlay` |
| `isPlaying()` | 是否在播放 | service |
| `next()` / `pre()` | 下一曲/上一曲 | service、`current`、`adapter.getItemCount()` |
| `play(int position)` | 播放指定曲目（钳位、选中、toast） | `adapter`、`ivPlay`、service |
| `cyclePlayMode()` | 切换播放模式 | `PlayModeController.cycle` |
| `applyPlayModeIcon()` | 刷新播放模式图标 | `ivPlayMode`、`PlayModeController.applyIcon` |
| `syncPlayButtonFromService()` | 按服务状态刷新播放键图标 | `ivPlay`、service |
| `onStopTrackingTouch(SeekBar)` 中的 `play(progress)` | 拖动进度条后跳转 | service、`ivPlay` |
| 状态字段 `current`（当前曲目下标） | 由广播 `UPDATE_ACTION` 更新 | 被 next/pre/translation 读取 |

**保留在 Fragment 的**：
- `bindViews` / `unbindViews`、`setupViews` 中的监听器注册。
- `MusicStateListener` 的 `isPlaying()`、`showMusicListSpectrum()`（转调 controls）。
- `SoundPlaybackReceivers.Host` 的实现（界面更新）。
- `checkCanPlay()`、`showUpdateDialog()`、`setSpeed()`、`getFavoriteDataUrl()`、`getBrowserDataUrl()`、列表加载。

> 实现前先执行：`grep -rn "SoundItemsMusicFragment" --include="*.java" --include="*.kt" .`，确认没有其他类直接调用 Fragment 的 `play/pause/resume/next/pre`。若有，Fragment 保留这些方法做转调。

---

## 3. 设计

### 3.1 两个接口（测试缝）

为了能在 JVM 上测试，控制类不直接依赖 `PlayService`（Kotlin 类，依赖 Android Service）和 `ImageView`。

```java
/** 播放服务的最小接口，只包含控制类用到的方法。 */
interface PlaybackPort {
    boolean isPlaying();
    int getPosition();                 // 毫秒，与 PlayService.getPosition() 一致
    void resume();
    void pause();
    void playPosi(int index);          // 与 PlayService.playPosi(int) 一致
    void seekTo(int positionMs);       // 与 PlayService.play(int) 一致
    int computeNextIndex(int current, int count);
    int computePreviousIndex(int current, int count);
}

/** 播放键图标的输出。 */
interface PlayIconSink {
    void showPlaying(boolean playing); // true=暂停图标，false=播放图标
}
```

- `PlaybackPort` 的生产实现是一个适配器，包装 `MusicServiceConnection.service()`，service 为 null 时各方法的行为见第 4 节。
- `PlayIconSink` 的生产实现包装 `ivPlay`，`ivPlay == null` 时什么也不做。

### 3.2 `SoundPlaybackControls`（新文件）

包：`com.openapi.ks.myapp.content.sound`，可见性：`final class`（包内可见）。

职责：播放状态机 + 当前曲目下标 + 播放键/模式键图标。

```java
final class SoundPlaybackControls {
    interface Host {
        PlaybackPort playback();          // 可能返回 null（服务未绑定）
        int itemCount();                  // adapter.getItemCount()
        void selectItem(int index);       // adapter.setSelected(index)
        void onPlaybackChanged();         // 通知列表刷新（fragment 里 adapter.notifyDataSetChanged）
        void showEmptyList();             // 显示空列表 toast
    }

    SoundPlaybackControls(Host host, PlayIconSink playIcon, PlayModeIconSink modeIcon, ...);

    int current();                        // 当前曲目下标，初始 -1
    void setCurrent(int index);           // 由广播 UPDATE_ACTION 调用

    boolean isPlaying();
    int resume();                         // 返回值与原 resume() 一致
    int pause();
    void play(int position);
    void next();
    void pre();
    void togglePlayPause();               // 原 onClick 中 iv_play 分支
    void seekTo(int positionMs);          // 原 onStopTrackingTouch 中的 play(progress)
    void cyclePlayMode(Context context);
    void applyPlayModeIcon(Context context);
    void syncPlayButtonFromService();
}
```

`PlayModeIconSink` 同理，包装 `ivPlayMode` + `PlayModeController`。模式切换的实际调用仍走 `PlayModeController`，控制类只决定“何时调用”。

### 3.3 Fragment 中的变化

- `onClick`：保留 id 分发（`R.id.*` 不能用于 switch，沿用现有 if/else），分支内改为调用 `controls.xxx()`；**末尾的 `adapter.notifyDataSetChanged()` 保留在 Fragment**（原行为：服务为 null 时 early return，不刷新）。
- `public` 的 `resume/pause/isPlaying/next/pre/play`：保留签名，内部转调 `controls`。
- `SoundPlaybackReceivers.Host` 的 `onTrackChanged(index)`：改为 `controls.setCurrent(index)` 后再做界面更新。
- 字段 `current` 删除，统一从 `controls.current()` 读取（translation 的 `currentIndex()` 同步改）。
- `playback`（`MusicServiceConnection`）仍在 Fragment 创建，通过 `Host.playback()` 交给控制类。

### 3.4 依赖方向

```
SoundItemsMusicFragment ──► SoundPlaybackControls ──► PlaybackPort (接口)
        │                                                  ▲
        └── SoundPlaybackReceivers.Host                    │ 生产实现包装 MusicServiceConnection
```

控制类不 import 任何 `android.widget.*`、`PlayService`、`Fragment`，只 import `android.content.Context`（仅为 `PlayModeController` 的签名）。如果连 Context 也想去掉，把 `PlayModeController` 调用包进 `PlayModeIconSink` 即可。

---

## 4. 行为不变量（实现和测试都必须满足）

下表是从现有代码逐行推出来的，**以现有代码为准**。实现时如果发现和表不一致，以现有代码为准并在报告里说明。

| 编号 | 方法 | 条件 | 期望行为 |
|---|---|---|---|
| I-1 | `resume` | service 为 null | 返回 -1，不改图标 |
| I-2 | `resume` | service 正在播放 | 返回 -1，不调用 `resume()`，不改图标 |
| I-3 | `resume` | service 暂停中 | 调用 `resume()`，图标设为暂停，返回 `getPosition()` |
| I-4 | `pause` | 未在播放 | 返回 -1，不调用 `pause()`，不改图标 |
| I-5 | `pause` | 正在播放 | 调用 `pause()`，图标设为播放，返回 `getPosition()` |
| I-6 | `isPlaying` | service 为 null | false |
| I-7 | `play(p)` | 列表为空 | 调用 `showEmptyList()`，不调用服务，不改图标 |
| I-8 | `play(p)` | p < 0 | 按 p=0 处理 |
| I-9 | `play(p)` | p ≥ count | 钳位到 count-1 |
| I-10 | `play(p)` | 钳位后 p == 0 | `selectItem(0)` |
| I-11 | `play(p)` | 其他情况 | 不调用 `selectItem` |
| I-12 | `play(p)` | 正常 | 图标设为暂停，调用 `playPosi(p)` |
| I-13 | `next` / `pre` | service 为 null 或 count ≤ 0 | 不做任何事 |
| I-14 | `next` | 正常 | `play(computeNextIndex(current, count))` |
| I-15 | `pre` | 正常 | `play(computePreviousIndex(current, count))` |
| I-16 | `togglePlayPause` | service 为 null | 不做任何事（Fragment 层 `onClick` 的 early return 保证 notify 也不执行） |
| I-17 | `togglePlayPause` | 正在播放 | `pause()` |
| I-18 | `togglePlayPause` | 未播放且 `current == -1` | `play(0)` |
| I-19 | `togglePlayPause` | 未播放且 `current != -1` | `resume()` |
| I-20 | `setCurrent(i)` | 任意 | 保存 i；之后 `current()` 返回 i |
| I-21 | `syncPlayButtonFromService` | service 为 null | 不改图标 |
| I-22 | `syncPlayButtonFromService` | 正常 | 图标 = service.isPlaying() ? 暂停 : 播放 |
| I-23 | `seekTo(p)` | 正常 | 调用 `seekTo(p)`，图标设为暂停 |
| I-24 | `applyPlayModeIcon` | 图标 sink 或 context 为 null | 不做任何事 |
| I-25 | `cyclePlayMode` | service 为 null | 不做任何事 |

> 注意 I-23：原代码 `onStopTrackingTouch` 在 service 为 null 时会 NPE（见第 6 节 K-2）。拆分后**保持 NPE 行为不变**，不要在这次改动里加判空。

---

## 5. 测试方案（先写测试，看它红，再实现）

### 5.0 前置：测试依赖

`feature-content/build.gradle` 目前没有 `testImplementation`。按 `app/build.gradle` 的同名依赖补上：

```groovy
testImplementation 'junit:junit:4.13.2'
```

本方案的 JVM 测试**不需要** Robolectric，因为控制类只依赖接口。Robolectric 只在下面 5.3 的可选场景里用。

### 5.1 JVM 单元测试（必做）

文件：`feature-content/src/test/java/com/openapi/ks/myapp/content/sound/SoundPlaybackControlsTest.java`

测试替身（写在测试文件里，不进生产代码）：
- `FakePlayback implements PlaybackPort`：记录调用次数和参数，可设置 `playing`、`position`、`nextIndex`、`prevIndex`。
- `FakePlayIcon implements PlayIconSink`：记录最后一次 `showPlaying` 的值和调用次数。
- `FakeHost implements SoundPlaybackControls.Host`：`playback` 可为 null，记录 `selectItem`、`onPlaybackChanged`、`showEmptyList` 的调用。

**先写这些测试，每个都必须先失败。** 失败原因应该是“类或方法不存在”（编译错误）或断言不成立，不能是测试写错。

| 测试名（建议） | 覆盖 | 对应不变量 |
|---|---|---|
| `resume_returnsMinusOne_whenServiceNull` | I-1 | I-1 |
| `resume_doesNothing_whenAlreadyPlaying` | I-2 | I-2 |
| `resume_resumesAndShowsPause_whenPaused` | I-3 | I-3 |
| `pause_doesNothing_whenNotPlaying` | I-4 | I-4 |
| `pause_pausesAndShowsPlay_whenPlaying` | I-5 | I-5 |
| `isPlaying_false_whenServiceNull` | I-6 | I-6 |
| `play_showsEmptyList_whenNoItems` | I-7 | I-7 |
| `play_clampsNegativeToZero` | I-8 | I-8 |
| `play_clampsPastEndToLast` | I-9 | I-9 |
| `play_selectsZero_onlyWhenResultIsZero` | I-10, I-11 | I-10, I-11 |
| `play_showsPauseAndCallsPlayPosi` | I-12 | I-12 |
| `next_doesNothing_whenServiceNullOrEmpty` | I-13 | I-13 |
| `next_playsComputedNextIndex` | I-14 | I-14 |
| `pre_playsComputedPreviousIndex` | I-15 | I-15 |
| `togglePlayPause_pausesWhenPlaying` | I-17 | I-17 |
| `togglePlayPause_playsFirstWhenNeverStarted` | I-18 | I-18 |
| `togglePlayPause_resumesWhenPausedAfterStart` | I-19 | I-19 |
| `setCurrent_isReadBack` | I-20 | I-20 |
| `syncButton_noop_whenServiceNull` | I-21 | I-21 |
| `syncButton_matchesServiceState` | I-22 | I-22 |
| `seekTo_callsServiceAndShowsPause` | I-23 | I-23 |
| `applyPlayModeIcon_noop_whenSinkNull` | I-24 | I-24 |
| `cyclePlayMode_noop_whenServiceNull` | I-25 | I-25 |

每个测试只断言一件事。名字里有 “and” 就拆开。

### 5.2 Fragment 层的 onClick 分发（可选，JVM 可做）

`onClick` 的 id 分发留在 Fragment，不建议为它单独建测试。若要测，抽一个 `static int actionFor(int viewId)` 返回枚举 `Action {PLAY_MODE, TOGGLE, NEXT, PREV, NONE}`，再测枚举映射。**只在实现者觉得有必要时做**，不作为验收条件。

### 5.3 Robolectric 场景（可选，不作为验收）

- 图标资源 id 是否正确（`ic_music_pause_filled` / `ic_music_play_filled`）。这个由手机验证覆盖，不必写 Robolectric。

### 5.4 编译与回归（必做）

```bash
./gradlew :feature-content:testDebugUnitTest --offline
./gradlew :feature-content:compileDebugJavaWithJavac --offline
./gradlew :app:testChatDebugUnitTest --offline --continue
./gradlew :app:assembleChatDebug --offline
```

验收标准：
- `:feature-content:testDebugUnitTest` 全部通过，新增 22 个用例以上。
- `:app:testChatDebugUnitTest` 只剩已知的两个失败（`AppDatabaseMigrationTest`、`ScreenSmokeTest`），不新增失败。
- `:app:assembleChatDebug` 通过。

### 5.5 手机验证清单（统一手机测试时执行）

在 Chat 变体的 debug 包上执行。每一条都要在拆分**前**的版本（`3c35861f`）上先跑一遍记录结果，拆分后再跑一遍，两次结果必须一致。

| # | 操作 | 期望 |
|---|---|---|
| P-1 | 进入声音页，点列表第 1 项 | 开始播放，底栏播放键显示“暂停” |
| P-2 | 点底栏播放键 | 暂停，图标变“播放” |
| P-3 | 再点播放键 | 从暂停处继续，图标变“暂停” |
| P-4 | 未播放过时，直接点播放键 | 从第 1 首开始播放 |
| P-5 | 点下一曲、上一曲 | 切歌，列表选中项、标题、字幕副标题同步变化 |
| P-6 | 最后一首点下一曲 | 与拆分前一致（循环或停在末尾，以拆分前为准） |
| P-7 | 点播放模式键，循环三种模式 | 图标逐次变化，与拆分前一致 |
| P-8 | 拖动进度条松手 | 跳到对应位置，图标为暂停 |
| P-9 | 播放中按 Home 退到后台，再回来 | 底栏播放键图标与实际状态一致 |
| P-10 | 播放中切到移动网络（未勾选“允许流量”） | 弹出提示，确认后继续播放；取消则暂停 |
| P-11 | 列表为空（频道无数据）时点播放 | 出现提示 toast，不崩溃 |
| P-12 | 倍速切换 | 播放速度变化，与拆分前一致 |
| P-13 | 播放中点列表项切换到另一首 | 新歌开始播放，旧歌停止 |
| P-14 | 翻译/字幕开关 | 字幕随当前曲目更新（`setCurrent` 生效） |
| P-15 | 旋转屏幕或重建页面 | 播放状态与图标一致，不重复播放 |

记录格式：每条写“拆分前结果 / 拆分后结果 / 是否一致”。不一致的必须写出现象和复现步骤。

---

## 6. 已知问题（发现但不在本次修改范围内）

这些是拆分前就存在的，本次拆分**保持原样**，单独记录，之后另开任务。

| 编号 | 位置 | 问题 | 建议 |
|---|---|---|---|
| K-1 | `play()` 空列表提示文字 `"该频道没有sex love"` | 文案看起来像误写 | 确认后改为正常文案（需要产品确认） |
| K-2 | `pause()` 和 `onStopTrackingTouch` | service 为 null 时会 NPE | 另开任务加判空，本次不改 |
| K-3 | `play()` 中 `playback.service().playPosi(position)` | 同上，service 为 null 时 NPE | 同上 |
| K-4 | `SoundDetailActivity` 自带一份 `PlayerReceiver` | 与列表页的接收器逻辑重复 | 放到后续任务，可复用 `SoundPlaybackReceivers` |

---

## 7. 实现步骤（给 Cursor）

1. 补 `testImplementation 'junit:junit:4.13.2'`。
2. 按 5.1 写 `SoundPlaybackControlsTest.java` 和两个 Fake，**不写生产代码**。运行，确认全部红（编译错误或断言失败），把红的输出贴到报告里。
3. 新建 `PlaybackPort.java`、`PlayIconSink.java`、`PlayModeIconSink.java`（可以合并进 `SoundPlaybackControls.java` 作为嵌套接口，二选一）。
4. 新建 `SoundPlaybackControls.java`，逐条实现 I-1 到 I-25，每实现一条跑一次对应测试，变绿后再下一条。
5. 新建生产适配器 `MusicServicePlaybackPort`（包装 `MusicServiceConnection`），以及 `ivPlay` / `ivPlayMode` 的图标包装。
6. 改 `SoundItemsMusicFragment`：按 3.3 替换调用；删除 `current` 字段；`onStopTrackingTouch` 改调 `controls.seekTo`。
7. 运行 5.4 的全部命令。
8. 查看行数：`wc -l` 记录结果。若 > 420，在报告里写出实际行数和剩余内容，**不要**为了凑行数继续改其他代码。
9. 在报告中附：新增/修改文件列表、测试结果、行数、与设计的任何偏离。

**停止条件**：如果某条不变量在现有代码里找不到对应行为（和本文第 4 节矛盾），停下来报告，不要自行选择。

---

## 8. 交付物

- 代码（分支 `weifu-refactor`，本地提交，**不推送**，推送由用户决定）。
- 测试结果：5.4 四条命令的输出摘要。
- 手机验证表（5.5）的记录，由用户执行。
- 本文档的更新：若实现与设计不同，在第 9 节追加说明。

## 9. 实现偏离记录

- **行数未达标**：`SoundItemsMusicFragment.java` 为 554 行（目标 ≤ 420）。播放控制已全部迁出；剩余行数是视图绑定、列表加载、字幕/翻译接线、广播 Host 实现和控制类的接线代码（匿名类）。未为凑行数改动其他代码。
- **I-10 表述修正**：`play()` 的实现先判断 `position == 0` 选中第 0 项，再钳位越界值。所以空列表以外，`count == 1` 时 `play(5)` 不会选中第 0 项（测试 `play_doesNotSelectZero_whenClampedFromPastEnd` 锁定此行为）。
- **`PlaybackPort` 命名**：实际为 `SoundPlaybackControls.Playback`，`PlayIconSink` 不变，新增 `PlayModeSink`（封装 `PlayModeController`，因为它依赖 `PlayService`），`PlaybackSource` 用于服务未绑定时返回 null。
- **测试**：`SoundPlaybackControlsTest`，25 个用例（设计写 22 个以上）。先以编译错误变红，再实现变绿。
- **未做**：5.5 手机验证（未执行）。K-1 至 K-4 保持原样。

