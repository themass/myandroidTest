package com.openapi.feature.video.screening

/**
 * 图集：和频道/播放页共用同一套样片素材，但按图集组织。
 *
 * 工程里已有的图集页是 `:feature-content` 的 `ImgChannelImgListFragment` / `ImgItemFragment`，
 * 数据来自远端推荐接口；这里不接网络，只用随包的样图把原生图集的结构做出来：
 * 封面双列瀑布流 → 图集内规则网格 → 全屏阅览（滑动、缩放、计数、返回原位置）。
 */
internal data class Album(
    val id: String,
    val title: String,
    val place: String,
    val description: String,
    val cover: String,
    val coverRatio: Float,
    val shots: List<Shot>,
)

internal data class Shot(val id: String, val asset: String, val caption: String, val ratio: Float)

/**
 * 样图只有八个场景，所以用「同一张图 + 不同裁切比例」拼出多张照片的真实感：
 * 每张图的宽高比不同，网格才会像真的图集，而不是一排等宽方块。
 */
internal val albums = listOf(
    Album(
        "ridge", "云层之上", "野外放映 · 第 12 卷",
        "海拔两千米的那段路，云在脚下流。",
        "ridge", .86f,
        listOf(
            Shot("r1", "ridge", "天亮前十分钟", 1.5f),
            Shot("r2", "snow", "雪线上的风", .75f),
            Shot("r3", "sky", "山的背面", 1f),
            Shot("r4", "forest", "林间小屋", 1.33f),
            Shot("r5", "road", "下山的路", .67f),
            Shot("r6", "night", "最后一盏灯", 1f),
            Shot("r7", "sea", "远处的海", 1.5f),
            Shot("r8", "city", "回城的车窗", .8f),
            Shot("r9", "ridge", "再来一张", 1f),
        ),
    ),
    Album(
        "night", "午夜不打烊", "夜行电台 · 深夜特辑",
        "把夜色留给还不想睡的人。",
        "night", 1.32f,
        listOf(
            Shot("n1", "night", "零点之后", .75f),
            Shot("n2", "city", "街道被拉成线", 1.5f),
            Shot("n3", "sky", "蓝调时刻", 1f),
            Shot("n4", "road", "还开着的便利店", .67f),
        ),
    ),
    Album(
        "sea", "退潮之前", "海边来信 · 夏季合辑",
        "给自己留一小段看海的时间。",
        "sea", 1.25f,
        listOf(
            Shot("s1", "sea", "退潮线", 1.5f),
            Shot("s2", "sky", "云在海平面以上", 1f),
            Shot("s3", "road", "沿海公路", 1.33f),
            Shot("s4", "ridge", "礁石", .75f),
        ),
    ),
    Album(
        "city", "城市侧写", "城市侧写 · 黑白练习",
        "霓虹、玻璃、和刚好经过的人。",
        "city", .82f,
        listOf(
            Shot("c1", "city", "霓虹招牌", .8f),
            Shot("c2", "night", "加班的人", 1f),
            Shot("c3", "road", "高架桥下", 1.5f),
            Shot("c4", "sky", "楼缝里的天", .67f),
            Shot("c5", "ridge", "离开城市那天", 1f),
        ),
    ),
    Album(
        "forest", "森林来客", "森林来客 · 慢速行走",
        "沿着没有路标的绿意往前走。",
        "forest", .96f,
        listOf(
            Shot("f1", "forest", "晨雾", .75f),
            Shot("f2", "ridge", "雾散之后", 1.5f),
            Shot("f3", "snow", "高处的雪", 1f),
        ),
    ),
    Album(
        "snow", "白色地平线", "野外放映 · 冬",
        "风过雪线，世界一下安静了。",
        "snow", 1.22f,
        listOf(
            Shot("w1", "snow", "雪线", 1.5f),
            Shot("w2", "ridge", "远山", .75f),
            Shot("w3", "sky", "白噪", 1f),
        ),
    ),
).associateBy(Album::id)

internal val albumOrder = listOf("ridge", "night", "sea", "city", "forest", "snow")
