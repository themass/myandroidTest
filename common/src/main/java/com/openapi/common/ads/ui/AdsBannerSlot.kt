package com.openapi.common.ads.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdView
import com.openapi.common.ads.base.AdsContext
import com.openapi.common.ads.base.AdsManager

/**
 * Compose 列表里的内嵌 banner 槽（台账 docs/design/20261125-admob-ads-ledger.md §4/§7）。
 *
 * 旧 View 体系把 banner 挂在行布局的 `rv_ads` 槽上（如
 * `feature-content/.../text/TextChannelListItemsViewAdapter.java:77-91`），迁移到
 * Compose 的页面没有这个槽，广告就随宿主一起没了。这里把同一件事做成一个可复用
 * 的槽：`AndroidView` 包一个空 `FrameLayout`，交给现存的
 * [AdsManager.showBannerAds]（AdMob adaptive banner，实现没换）。
 *
 * debug 闸关闭时 `showBannerAds` 直接返回，容器保持空、高度 0，不占版面。
 * 位置/类别规则不在这里——调用方用 [com.openapi.common.ads.base.AdsShowPolicy]
 * 的纯函数判定后才放置本槽（有 JVM 单测）。
 *
 * LazyColumn 回收：行滚出组合时 `onRelease` 销毁槽内 AdView（旧 View 路只
 * removeView 不 destroy，每次 onBind 泄漏一个 AdView——这里补上）。
 */
@Composable
fun AdsBannerSlot(
    categrey: AdsContext.Categrey,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current ?: return
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            FrameLayout(ctx).also { container ->
                AdsManager.getInstans().showBannerAds(activity, container, categrey)
            }
        },
        onRelease = { container -> destroyAdViewsIn(container) },
    )
}

private fun destroyAdViewsIn(group: ViewGroup) {
    for (i in 0 until group.childCount) {
        when (val child = group.getChildAt(i)) {
            is AdView -> child.destroy()
            is ViewGroup -> destroyAdViewsIn(child)
        }
    }
    group.removeAllViews()
}
