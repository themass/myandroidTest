# A38 全站字号/换行只读审计（2026-11-25）

只读审计，未改任何文件。范围：feature-chat / chatkit / mediaview / feature-video / app / feature-companion / feature-content / feature-user / core / common / feature-photosolve（后者仅记录不给修法，归 A37；mediaview 编辑器仅记录，归 A33-A35）。

基线：正文 14–16sp、次级 12sp、title ≤18–20sp。

## 0. 根因（两条，修这两条覆盖大半清单）

1. **`common/.../theme/AppMaterialTheme.kt:29` 没有 override Typography** → 全站 `MaterialTheme.typography.titleLarge` 实际渲染 M3 默认 **22sp**（titleMedium 16sp）。全部 titleLarge/title/headline 调用点都超 18–20 基线：
   - app/src/main/java/com/openapi/ks/myapp/agent/ui/AgentChatEntry.kt:136（"选择角色"）
   - feature-video/.../screening/ScreeningActivity.kt:468（"放映场"）
   - feature-companion/.../companion/CompanionManagementScreens.kt:543（ScreenHeader 公共标题）
   - （photosolve 三处 titleLarge + headlineMedium=28sp：仅记录）
   **建议**：在 AppMaterialTheme 里定义自定义 Typography（titleLarge=20sp、titleMedium=16sp、labelLarge=14sp），一次性收掉全部调用点。

2. **feature-video/screening（深色改版模块）完全脱离 token**，硬编码 17–37sp，全站 ≥17sp 的 51 处里这个模块占 25 处。建议给该模块立一套 ScreeningTypography token（页标 18、区块 16、卡标 22、hero 24–26），见下面逐条。

现有 token 参照（都合规）：`common/.../Tokens.kt:87` AppTypography（screenTitle 18 / sectionTitle 16 / body 15 / caption 12）；`chatkit/.../theme/ChatKitTheme.kt:64`（body 16 / secondary 14 / caption 12）。

## ① 明显偏大的字号（现状 → 建议）

### feature-video/screening（最大问题簇）
| file:line | 现状 | 内容 | 建议 |
|---|---|---|---|
| ScreeningGallery.kt:165 | **37sp/42 两行（硬编码\n）** | hero "一册一个地方，\n翻到最后一张为止。" | 26sp，去掉硬换行 |
| ScreeningPlayback.kt:856 | **35sp/40 无 maxLines** | 播放页 source.title | 22sp + maxLines=2+Ellipsis |
| ScreeningGallery.kt:229 | **34sp/40** maxLines2 | 图集精选卡 book.title | 22sp |
| ScreeningSound.kt:127 | 32sp/38（硬编码\n） | hero "戴上耳机，\n慢慢来。" | 24–26sp |
| ScreeningText.kt:152 | 32sp/38 | hero "一个故事换一屏。" | 24–26sp |
| ScreeningActivity.kt:570 | 22sp/26 | 热映卡 entry.title | 18sp |
| ScreeningActivity.kt:749 | 21sp/25 | 频道页 page.title | 18sp |
| ScreeningActivity.kt:848 | 19sp（Heading 公共组件，多屏共用） | 区块标题 | 16sp |
| ScreeningActivity.kt:770 | 19sp | "子频道/全部视频" | 16sp |
| ScreeningGallery.kt:160 | 22sp | "图集" 页标 | 18sp |
| ScreeningSound.kt:120 | 22sp | "声音馆" 页标 | 18sp |
| ScreeningText.kt:145 | 22sp | "文字馆" 页标 | 18sp |
| ScreeningPlayback.kt:484 | 20sp | 设置面板当前值 | 16sp |
| ScreeningPlayback.kt:762 | 20sp | 快进 toast "+5s" | 可保留（瞬时 overlay） |
| ScreeningSound.kt:146 / ScreeningText.kt:171 | 17sp maxLines2 | 列表项标题 | 临界，可保留 |
| ScreeningGallery.kt:197 | 17sp maxLines2 | 小卡标题 | 可保留 |
| ScreeningPlayback.kt:662 | 17sp maxLines1 | 半屏顶栏片名 | 可保留 |

### feature-companion
| file:line | 现状 | 内容 | 建议 |
|---|---|---|---|
| CompanionCallScreen.kt:776 | 22sp | 通话顶栏 agentName | 18sp |
| CompanionCallScreen.kt:902 | 20sp | 半屏 agentName | 16–18sp |
| CompanionCallScreen.kt:1164 | 20sp | 色相环 "240°" 读数 | 14–16sp |
| CompanionCallScreen.kt:2577/2582, 1633/1637; SimpleAvatar.kt:229 | 30–54sp | 头像占位字母/emoji | 装饰性 glyph，**不算违规** |
| HeroSideDrawer.kt:135 | 20sp "›" | 把手符号 | 保留 |

### feature-user / core / feature-content / app
| file:line | 现状 | 内容 | 建议 |
|---|---|---|---|
| feature-user/.../password/FindPassActivity.kt:283 | 20sp | "邮件已发送" 标题 | 18sp |
| core/.../legacyui/list/ListShellViews.kt:156 | 18sp | 空态正文 | 14sp |
| feature-user/.../setting/StorageScreen.kt:108 | 17sp | "共占用 x" | 16sp |
| feature-user/.../home/presentation/ProfileScreen.kt:134 | 17sp | 用户昵称 | 16sp |
| feature-user/.../setting/SettingActivity.kt:323 | 17sp | 存储弹层标题 | 临界可保留 |
| feature-content/.../sound/compose/SoundDetailParts.kt:81 | 17sp | "正在播放" 标题 | 16sp |
| feature-content/.../SoundDetailScreen.kt:105 | 18sp marquee | 播放器曲名 | 可保留 |
| app/.../agent/ui/AgentChatEntry.kt:136 | titleLarge=22sp | "选择角色" | 随根因①收到 20 |

### chatkit（输入框比气泡正文还大）
| file:line | 现状 | 内容 | 建议 |
|---|---|---|---|
| chatkit/.../input/ComposerParts.kt:129+134 | **17sp**（气泡 body=16） | 输入框+hint | 16sp，与 body 对齐 |
| chatkit/.../input/VoiceDraftEditor.kt:73 | 17sp | 语音草稿输入 | 16sp |
| chatkit/.../message/MessageSelection.kt:89+98 | 16sp | 选择态 取消/删除 按钮 | 14–15sp |
| chatkit/.../input/WeChatRecordingView.kt:130 | 17sp（固定 140dp 胶囊内） | 弧形按钮文案 | 15sp |

### feature-photosolve（A37 范围，仅记录不给修法）
PhotoSolveHomeScreen.kt:83 headlineMedium(28sp)；CropAssistComparisonScreen.kt:77 / CropAssistSettingsSheet.kt:38 titleLarge(22sp)；SolveHistoryDetailScreen.kt:182/224 MathText 20sp；PhotoResultScreen.kt:153/204 17/18sp。

## ② 按钮/控件文字会换行（无 maxLines / 宽度不够 / 文案过长）

| file:line | 现状 | 风险 | 建议 |
|---|---|---|---|
| feature-companion/.../CompanionCallScreen.kt:490+493 | TextButton "■ 停止用户试听"/"▶ 用户试听" 两个并排，无 maxLines | 小屏两个长文案按钮同排，必换行 | maxLines=1 + 文案收短（"停止试听"/"试听"） |
| app/.../agent/ui/AgentChatScreen.kt:89 | TextButton "${reminders.size} 条提醒待发送" | 动态长文案 | maxLines=1 或改"N 条提醒" |
| feature-chat/.../roles/presentation/RoleCatalogPane.kt:122 | FilterChip label="${division.label} ${division.count}" 无 maxLines | chip 内换行撑高 | maxLines=1 |
| feature-user/.../setting/SettingRows.kt:105 | 行标题 weight(1f) 15sp 无 maxLines | 长设置项名换行 | maxLines=2 |
| app/.../agent/ui/AgentChatEntry.kt:121 | role.name titleMedium weight(1f) 无 maxLines | **服务端角色名**，长名换行顶掉按钮 | maxLines=1+Ellipsis |
| feature-user/.../home/presentation/ProfileScreen.kt:134 (name 处约 128 行 Column) | info.name 17sp 无 maxLines，旁边还有等级徽标 | **用户昵称**长→挤压/换行 | maxLines=1+Ellipsis |
| chatkit/.../input/WeChatRecordingView.kt:147 | liveText 17sp，300–370dp 内无 maxLines | ASR 实时文本长句狂换行 | 保留换行（场景需要），字号 16 |
| app/.../agent/ui/AgentChatScreen.kt:305 | "${r.dueLocal}  ${r.content}" weight(1f) 无 maxLines | 提醒内容不定长 | maxLines=2 |

### mediaview 编辑器（A33–A35 范围，仅记录）
PhotoEditor.kt:948 sliderLabel 固定 width(48.dp) 无 maxLines（中文 3 字≈48dp 临界，字体放大即换行）；PhotoEditor.kt:1177/1258 width(56.dp) 同型风险。

## ③ title 长文无 Ellipsis

| file:line | 现状 | 建议 |
|---|---|---|
| feature-video/.../ScreeningPlayback.kt:856 | 35sp 无任何截断（全站最严重，长片名 3 行起步） | 22sp + maxLines=2 + Ellipsis |
| feature-video/.../ScreeningActivity.kt:848 Heading() | 公共组件无截断（各屏区块标题都走它） | maxLines=1 + Ellipsis（一处改覆盖多屏） |
| feature-content/.../SoundDetailParts.kt:81 | weight(1f) 无截断 | maxLines=1 |
| feature-video/.../local/LocalVideoActivity.kt:165 | 18sp weight(1f) 无截断（静态文案，低风险） | maxLines=1 |
| feature-user/.../SettingActivity.kt:323 | 静态文案，低风险 | maxLines=1 |

已合规对照（有 maxLines+Ellipsis，修复时照这个抄）：ScreeningActivity.kt:570/574/749、ScreeningPlayback.kt:662、PlayerControlsLayer.kt:367 区标题、CompanionCallScreen.kt:776/902、SoundDetailScreen.kt:105。

## 汇总数字

- 全站硬编码 fontSize 数值 ~320 处；≥17sp 共 51 处，其中 feature-video/screening 占 25 处。
- `typography.titleLarge`（默认 22sp）6 处调用全部超基线，且主题层未 pin。
- 修复优先级：①根因 AppMaterialTheme pin Typography → ②screening 模块 token 化（hero 37/34/35/32 → ≤26，页标 22→18）→ ③数据驱动的 title 补 maxLines+Ellipsis（昵称/角色名/片名）→ ④长文案按钮收短+maxLines。
