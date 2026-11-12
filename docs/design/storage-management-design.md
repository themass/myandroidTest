# 客户端存储管理设计：目录清单、可见性与清理

日期：2026-11-12
分支：`claude/happy-fermi-300550`（基线 `weifu-refactor`）
相关：`docs/design/photo-solve-design.md`、`docs/design/photo-editing-design.md`

---

## 1. 现状与问题

App 会把数据写到十几个目录，但**用户完全看不到**：

| 问题 | 后果 |
|---|---|
| 目录散落在各模块的 private 常量里 | 用户不知道 App 占了多少空间；「存储」里只显示一个总数 |
| 用户数据和缓存混在一起 | 想清空间只能去系统设置「清除数据」，**这会把解题历史一起删掉** |
| 缓存没有清理入口 | 图片编辑的 `photo_edit/`、分享临时文件会一直增长 |
| 目录名字没有任何说明 | 即使列出来，用户也看不懂 `photosolve_history` 是什么 |

## 2. 现有的目录清单（实测）

### 2.1 内部存储 `filesDir`（随 App 卸载删除，`adb backup` 可备份）

| 目录 | 归属 | 内容 | 性质 |
|---|---|---|---|
| `photosolve_history/` | `:feature-photosolve` | 解题历史索引 + 题目图（≤1280）+ 缩略图（≤240） | **用户数据** |
| `chat_attachments/` | `:feature-chat` | 对话附件暂存 | 缓存 |
| `camera/` | `:feature-chat` | 聊天里拍的原图 | 缓存 |
| `tts/` | `:feature-chat` | 语音合成的音频 | 缓存 |
| `avatars/` | `:feature-companion` | 2D 形象资源包（含 manifest 与图片） | 可重建 |
| `custom_artist_images/` | `:retromusic` | 本地音乐的自定义歌手封面 | **用户数据** |
| `profile.jpg` / `banner.jpg` | `:retromusic` | 用户头像与背景（**文件，不是目录**） | **用户数据** |
| `share/`（部分版本） | `:common` | 分享用的临时文件 | 缓存 |

### 2.2 内部缓存 `cacheDir`（系统可在低存储时自动清理）

| 目录 | 归属 | 内容 | 性质 |
|---|---|---|---|
| `photo_edit/` | `:mediaview` | 图片编辑导出的结果（≤2048, q92） | 缓存 |
| `share/` | `:common` | 分享临时文件 | 缓存 |
| `export/` | `:app` | 对话数据导出的 JSON | 缓存 |
| `attachments/` | `:feature-chat` | 附件缩略图 | 缓存 |
| `photosolve_*.jpg` | `:feature-photosolve` | 上传时的临时压缩图（用完即删） | 临时 |
| `paizhao_capture_*.jpg` | `:feature-photosolve` | 拍照原图（解码后即可删） | 临时 |
| `avatar-*.zip` | `:feature-companion` | 形象包下载的临时 zip（用完即删） | 临时 |

### 2.3 外部存储（**不在本期范围内**）

| 路径 | 用途 |
|---|---|
| `getExternalFilesDir(DOWNLOADS)/ADeepseek/` | Glide 磁盘缓存（100MB 上限），见 `AppGlideConfig` |
| `Environment.getExternalStorageDirectory()/tencent/vpn/freevpn.apk` | APK 下载（旧代码） |

> 外部存储涉及权限和用户可见的文件，改动风险高于收益，本期只做内部存储。

## 3. 方案：一张登记表 + 一个管理页

### 3.1 为什么不自动扫描

`filesDir` / `cacheDir` 下还有系统与第三方库的目录（Glide、WebView、OkHttp……）。自动扫描会把它们也列给用户，
**删掉可能弄坏别的功能**。所以只列出**明确登记过**的目录。

### 3.2 分层

```
:common  StorageDirs      目录名字符串清单（第 0 层，不能反向依赖各模块）
         StorageCatalog   登记 + 统计体积 + 清理（不认识任何业务）
              ↑
各模块    各自用自己的目录名（private 常量与 StorageDirs 保持一致）
              ↑
:app     StorageRegistry  把目录名翻译成「中文名 + 用途 + 能否删除」，启动时登记一次
              ↑
:feature-user  StorageScreen  设置页 →「存储管理」，只依赖 StorageCatalog，不认识具体模块
```

**为什么放在 :app 登记**：它是组合根，唯一允许同时知道所有模块的地方；`:common` 保持第 0 层纯净。
`:feature-user` 只依赖 `:common`，所以既能用登记表，又不必知道 `:feature-photosolve` 的存在。

### 3.3 数据结构

```kotlin
data class Entry(
    val dirName: String,          // filesDir/cacheDir 下的一级目录名
    val root: Root,               // FILES 或 CACHE
    val title: String,            // 用户看得懂的名字，例如「解题历史」
    val description: String,      // 里面是什么、删了会怎样
    val userData: Boolean,        // true = 删了不可恢复
    val onCleared: (() -> Unit)?  // 清理后需要刷新别处时用
)

data class Usage(entry, path, sizeBytes, fileCount, missing)
```

### 3.4 两类操作的交互差异

| 类型 | 按钮文案 | 确认 | 说明 |
|---|---|---|---|
| 缓存（`userData=false`） | 「清理」 | 不需要（只弹 Toast） | 丢了会自动重建 |
| 用户数据（`userData=true`） | 「删除」（红色） | **必须确认**，文案写明「删除后无法恢复」 | 解题历史、歌手封面等 |
| 全部缓存 | 「清理全部缓存」 | 需要确认，显示将释放多少 | 只清 `userData=false` 的目录 |

## 4. 界面

设置页 →「存储管理」（弹窗，高度 90%）：

```
┌─────────────────────────────────────┐
│ 存储管理                        关闭 │
├─────────────────────────────────────┤
│ 📁 共占用 43.2 MB                    │
│    缓存 12.8 MB · 用户数据 30.4 MB   │
│    下面只列出 App 自己产生的目录…    │
├─────────────────────────────────────┤
│ 清理全部缓存                  [清理] │
│ 只清理标记为缓存的目录…              │
├─────────────────────────────────────┤
│ 解题历史                [用户数据]   │
│ 30.4 MB · 128 个文件                │
│                          [ 删除 ]   │
│ 拍照解题的题目和解答记录。删除后…    │
│ /data/data/…/files/photosolve_history│
├─────────────────────────────────────┤
│ 图片编辑结果                        │
│ 8.1 MB · 24 个文件                  │
│                          [ 清理 ]   │
│ 编辑后待发送的图片…                 │
└─────────────────────────────────────┘
```

- 用户数据排在前面；每项显示：名字、类型标签、体积、文件数、用途说明、完整路径（小字，排查问题用）。
- 体积统计在后台线程做（递归 `walkTopDown`），完成后回主线程更新。
- 右上角「刷新」重新统计。

## 5. 已知不足与后续

| # | 现状 | 后续 |
|---|---|---|
| 1 | 外部存储（Glide 100MB 缓存、APK 下载）未纳入 | 单列一项「图片缓存」，用 Glide 自带的 `clearDiskCache()` |
| 2 | `profile.jpg` / `banner.jpg` 是**文件**不是目录，当前登记表只支持目录 | 给 `Entry` 加 `isFile` 或引入「虚拟条目」概念 |
| 3 | 历史记录图片占空间，只能整体删除 | 加「只保留文字、不留图」或「超过 N 条自动清理最早」 |
| 4 | 路径信息对普通用户意义不大 | 可藏在「开发者模式」下 |
| 5 | 没有「导出备份」 | 历史记录已经是 JSON，导出成本低 |

## 6. 验证

```bash
./gradlew --offline :common:testDebugUnitTest --tests "com.openapi.common.storage.*"  # 5 个
./gradlew --offline :app:compileChatDebugKotlin
```

已验证：上述命令本机通过；`StorageCatalogTest` 覆盖登记、去重、单位格式化、用户数据与缓存的区分。

**未验证**（需要真机）：
- 实际体积统计的正确性与大目录下的耗时（几百 MB 的目录递归可能上百毫秒）；
- 删除后 `FsSolveHistoryStore` 是否需要主动刷新（当前历史页每次进入都重读索引，天然自愈）；
- 弹窗在低分辨率设备上的滚动行为。
