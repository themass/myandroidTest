package com.openapi.feature.video.screening

import com.openapi.core.config.Constants

/**
 * 放映场的频道分类（对应两个真接口）。
 *
 * 随包样片清单已随「本地视频」标签下线：频道/片单/图集一律真接口，取不到就报空或报错，
 * 不再拿 assets 里的演示素材填屏。
 * 分类口径沿用旧频道页（其实现已随正式化删除）：只有电影和夜色，「自定义」分类已下线。
 */
internal enum class FeedCategory(
    val label: String,
    /** 接口路径（还没拼 base url）。 */
    val api: String,
) {
    MOVIE("电影", Constants.API_RECOMMEND_MOVIE_URL),
    NIGHT("夜色", Constants.API_RECOMMEND_NIGHT_URL),
}
