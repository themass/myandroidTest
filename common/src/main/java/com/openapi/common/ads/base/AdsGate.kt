package com.openapi.common.ads.base

/**
 * 广告总开关：**恒开**——用户 20261125 亲令「一直开，忽略debug」（台账
 * docs/design/20261125-admob-ads-ledger.md §6 D1，定为方案 b）。
 *
 * 历史：`28b2226e`（2026-11-21，P2-13）曾因 debug 包冷启动弹全屏开屏/插屏
 * （GMA SDK 的 AdActivity，误触跳 googleadservices 证书报警页）把 debug 构建
 * 整体关掉。用户明确接受该干扰，要求 debug 与 release 一律展示广告。
 *
 * 验收剧本的干扰不在这里挡，在 `script_case`：启动页跳过开屏倒计时，
 * [InterstitialCadence.scriptQuiet] 静音插屏。
 *
 * `AdsManager` 保持「所有 map 访问前先过闸」的形状（`AdsManagerGateTest` 钉住）；
 * 将来要临时全关广告，改 [enabled] 这一处即可。
 */
object AdsGate {
    /** 构建期 debug 标记，由 MyApplication 在 onCreate 最早处写入；闸门不再读它。 */
    @JvmStatic
    @Volatile
    var debugBuild: Boolean = false

    /** true = 允许加载与展示广告。恒为 true（用户决定），见类注释。 */
    @JvmStatic
    fun enabled(): Boolean = true
}
