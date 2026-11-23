# Screening：text/sound 迁出 LEGACY

见兄弟会话笔记 protocol-dispatch-fix-20261123.md；本切片先收 text，sound 仍交回 SoundChannleActivity。

- text:// + channel= → FeedRoute.TEXT → ScreeningText（channel.json → items.json → item.json）
- sound:// → WEB/LEGACY（待 SOUND 路由）

## 更新（sound 切片）
- `sound://` + channel= → `FeedRoute.SOUND` → `ScreeningSound`（channel.json → items.json + Exo 底栏）
- `isLegacyOnlyAction` 恒 false；sound/text 均不再交 ConfigActionJump
