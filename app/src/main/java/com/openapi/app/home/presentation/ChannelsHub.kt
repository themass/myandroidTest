package com.openapi.app.home.presentation

import androidx.compose.runtime.Composable
import com.openapi.feature.video.screening.ScreeningRoute

/**
 * 首页「频道」标签：放映场整屏内嵌（正式入口）。
 *
 * 旧实现在这里挂 feature-content 的 ChannelsScreen + 「本地视频」tab——旧卡片页点进去
 * 会路由到旧列表/旧详情，就是 kimi 报的「首页新二级旧」。放映场已迁 main 源集，
 * 标签直接内嵌它（同一套 UI：频道/图集两个看法、协议分发全在页内、取不到就空态），
 * 不复制页面也不开第二个 Activity。「本地视频」不在放映场里有正式口径（随包样片已下线），
 * 旧 LocalVideo 页面原样保留在 :feature-video，入口在侧边栏「内容」组的「本地视频」一项。
 * （旧 ChannelsScreen/ChannelsViewModel 整链已作为死代码删除。）
 */
@Composable
fun ChannelsHub() {
    ScreeningRoute()
}
