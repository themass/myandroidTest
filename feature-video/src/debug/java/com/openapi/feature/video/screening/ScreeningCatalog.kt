package com.openapi.feature.video.screening

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class Reel(val id: String, val title: String, val description: String, val ratio: Float)
internal data class Channel(val title: String, val category: String, val cover: String, val ratio: Float, val reels: List<String>)

internal val reels = listOf(
    Reel("ridge", "云层之上", "去海拔更高的地方，听一会儿风。", .84f),
    Reel("forest", "树影慢一点", "沿着没有路标的绿意往前走。", 1.18f),
    Reel("snow", "白山回声", "风过雪线，世界一下安静了。", .95f),
    Reel("road", "天亮就出发", "把地图折起来，跟着地平线走。", 1.12f),
    Reel("night", "午夜不打烊", "把夜色留给还不想睡的人。", .84f),
    Reel("city", "最后一站", "车窗外的灯，把街道拉成一条线。", 1.2f),
    Reel("sea", "退潮之前", "给自己留一小段看海的时间。", 1.18f),
    Reel("sky", "蓝调之间", "日落之后、天黑之前。", .86f),
).associateBy(Reel::id)

// 写死的「样例频道」已删：没有任何生产者会造出 sample://channel/ 条目，
// 真频道全部走服务端（channel.json / items.json）。随包 assets 只剩本地样片库一种用途。
internal val samples = Channel("本地样片", "离线样片库", "road", 1f, reels.keys.toList())
internal const val SAMPLE_MS = 12_000L

@Composable
internal fun coverBitmap(context: Context, id: String): ImageBitmap? =
    produceState<ImageBitmap?>(null, id) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("$id.jpg").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }.value

internal fun time(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
