package com.openapi.feature.video.screening

import android.app.Activity
import androidx.compose.runtime.Composable
import com.openapi.common.ads.base.AdsContext
import com.openapi.common.ads.base.AdsManager
import com.openapi.common.ads.base.AdsShowPolicy
import com.openapi.common.ads.base.InterstitialCadence
import com.openapi.common.ads.ui.AdsBannerSlot
import com.openapi.core.config.Constants

/**
 * 放映场的广告挂载点（AD2/AD3 恢复，台账
 * docs/design/20261125-admob-ads-ledger.md §3/§4）。
 *
 * 旧 View 列表把 banner 塞进行布局的 `rv_ads` 槽（
 * `feature-content/.../text/TextChannelListItemsViewAdapter.java:77-91`），
 * 位置表是 `Constants.BANNER_ADS_POS`，类别按 `position % 3` 选
 * VPN3/VPN2/VPN1。这里保持**同一套规则**，只是宿主换成 Compose 槽
 * （[AdsBannerSlot]）；规则本体是 [AdsShowPolicy] 的纯函数，JVM 单测在
 * `common/src/test/.../AdsShowPolicyTest.kt`。debug 闸关闭时槽保持 0 高，不占版面。
 */

/** 单列列表：在第 [index] 行之后按旧位置表放一条 banner（不满足就不画任何东西）。 */
@Composable
internal fun FeedRowAdSlot(index: Int) {
    if (!AdsShowPolicy.bannerSlotAt(index, Constants.BANNER_ADS_POS)) return
    AdsBannerSlot(categrey = AdsShowPolicy.bannerCategreyAt(index))
}

/** 三列网格：以「行内最后一张条目」的下标套用同一位置表，banner 画在整行下方。 */
@Composable
internal fun FeedGridAdSlot(rowEndIndex: Int) = FeedRowAdSlot(rowEndIndex)

/**
 * 下钻一层时尝试插屏（对应旧频道页 `MovieChannleActivity.java:50` 等处的
 * `showRand(this, CATEGREY_VPN2)`）。主干从来没有时机闸门（台账 §3），所以必须过
 * [InterstitialCadence]：启动静默期 + 两次插屏最小间隔都过了才走概率，
 * 否则新放映场会变成「每切一页弹一屏全屏」——Play 按「广告干扰核心功能」处理的那种。
 */
internal fun tryInterstitialOnDrill(activity: Activity) {
    if (!InterstitialCadence.allow()) return
    if (!AdsContext.rateShow()) return
    InterstitialCadence.noteShown()
    AdsManager.getInstans()
        .showInterstitialAds(activity, AdsContext.Categrey.CATEGREY_VPN2, false, AdsContext.AdsFrom.ADMOB, 1)
}
