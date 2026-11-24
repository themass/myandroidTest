package com.openapi.mediaview.edit

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openapi.mediaview.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** 缩略图底图长边。再小就看不出滤镜的色偏了；六格这个尺寸的位图内存可以忽略。 */
private const val FILTER_THUMB_LONG_EDGE = 128

/** 点一格预设默认套多满（要淡再拖滑杆）。宿主选预设时用同一个值，两边不能各写一份。 */
internal const val FILTER_PRESET_INTENSITY = 100

/** 一格缩略图的边长。 */
private const val FILTER_CELL_DP = 56

/**
 * 滤镜面板：6 格预设 + 强度滑杆 + 「按住看原图」。
 *
 * 面板只管「选了哪一格、强度多少」，**不碰历史**：滑杆沿用 [AdjustPanel] 那套
 * withOpsUnrecorded / 松手一次提交，预设、移除、对比怎么记一步都归宿主（见 PhotoEditor 的调用点）。
 *
 * 缩略图回答的是「这张表把色调改成什么样」，**不承诺复现当前画幅**（旋转/裁剪不反映进去）：
 * 为此单跑一条裁剪管线不值，而且用户在滤镜这一步要挑的也不是构图。
 *
 * **取不到表必须看得见**（实机抱怨「滤镜点了没反应，全是黑框」）：原先 provider 返回 null
 * 就直接跳过那一格，格子上只剩一层深灰 —— 六格全取不到时，六格深灰压在黑色面板上就是「一片黑框」，
 * 而且没有任何地方说明为什么。现在：缺表的格子画成明确的占位（斜杠图标），面板顶部给一行
 * 「滤镜资源不可用」提示，缺几张就说几张。
 *
 * @param filter 当前滤镜；null = 无滤镜（此时滑杆置灰，因为没有作用对象）
 * @param source 生成缩略图用的底图（预览源，不是导出尺寸）
 * @param lutProvider 取表。首次进面板会在这里读 6 个 `.cube`，所以调用点必须传后台安全的 provider
 *   （[LutAssets] 按 id 记忆化，之后重进面板不再读盘）
 */
@Composable
internal fun FilterPanel(
    filter: EditOp.Filter?,
    source: Bitmap,
    lutProvider: (String) -> Lut3D?,
    onPickPreset: (String) -> Unit,
    onIntensityChange: (Int) -> Unit,
    onIntensityDone: () -> Unit,
    onCompare: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedId = filter?.id

    // 缩略图在后台生成：每张都要「拷一份 128px 底图 + 逐像素套表」，摊到组合期就会拖住首帧。
    val thumbs by produceState<FilterThumbs>(FilterThumbs.EMPTY, source, lutProvider) {
        value = buildFilterThumbs(source, lutProvider)
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF111111))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.mediaview_edit_filter), color = ACCENT, modifier = Modifier.weight(1f))
            CompareButton(onPressed = { onCompare(true) }, onReleased = { onCompare(false) })
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.mediaview_edit_filter_done), color = Color.White)
            }
        }

        // 缺表提示：只在真的有取不到的表时占一行，不静默留空。
        if (thumbs.missing.isNotEmpty()) {
            Text(
                filterUnavailableNotice(thumbs.missing.size),
                color = ACCENT,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        // 两行三格。用 chunked 而不是 LazyGrid：总共 6 格，滚动与复用在这里全是负担。
        FilterPresets.ids.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { id ->
                    FilterCell(
                        id = id,
                        thumb = thumbs.map[id],
                        // 取不到表 ≠ 还没生成完：只有确认取不到的格子才画「不可用」占位
                        unavailable = id in thumbs.missing,
                        selected = id == selectedId,
                        onClick = { onPickPreset(id) },
                        // weight 只在 RowScope 里有：所以等宽由调用方传进来，不是格子自己声明
                        modifier = Modifier.weight(1f),
                    )
                }
                // 最后一行不满三格时补位，保证每格等宽
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.mediaview_edit_intensity), color = Color.White, modifier = Modifier.width(48.dp))
            Slider(
                value = (filter?.intensity ?: FILTER_PRESET_INTENSITY).toFloat(),
                onValueChange = { onIntensityChange(it.roundToInt()) },
                onValueChangeFinished = onIntensityDone,
                valueRange = 0f..100f,
                enabled = filter != null,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${filter?.intensity ?: FILTER_PRESET_INTENSITY}",
                color = Color.White,
                modifier = Modifier.width(36.dp),
            )
        }
        Text(
            stringResource(R.string.mediaview_edit_hint_filter),
            color = Color.Gray,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private val ACCENT = Color(0xFFFFCC00)

/** 缺表时在面板顶部的一行提示（res 在我负责范围外，所以文案落在这里）。 */
internal fun filterUnavailableNotice(missingCount: Int): String =
    "滤镜资源不可用（$missingCount/${FilterPresets.ids.size} 张表取不到），请重装应用"

/** 面板缩略图的结果：成功的那些 + **确认取不到**的那些（缺表计数是可见提示的数据源）。 */
internal class FilterThumbs(val map: Map<String, ImageBitmap>, val missing: List<String>) {
    companion object {
        val EMPTY = FilterThumbs(emptyMap(), emptyList())
    }
}

/**
 * 一格预设。选中态除了描边还把名字染黄：描边给眼睛、颜色给状态，两个都留一点。
 *
 * 名字直接显示滤镜 id：它同时是 `.cube` 的文件名，也是设计换表时要对齐的键。
 * 要中文显示名就在这里加一层 id→资源映射（只有 6 个，先不造这层）。
 *
 * [unavailable] = provider 确认取不到这张表：格子里画一个可辨的占位（禁止图标），
 * **不是**只留一层深灰——深灰压在黑色面板上就是用户看到的「黑框」。
 */
@Composable
private fun FilterCell(
    id: String,
    thumb: ImageBitmap?,
    unavailable: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(FILTER_CELL_DP.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (unavailable) Color(0xFF424242) else Color(0xFF2A2A2A))
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) ACCENT else Color(0xFF555555),
                    RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                thumb != null -> Image(
                    bitmap = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                unavailable -> Icon(
                    Icons.Filled.Block,
                    contentDescription = null,
                    tint = Color(0xFFFFB0A0),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            id,
            color = when {
                unavailable -> Color(0xFFFFB0A0)
                selected -> ACCENT
                else -> Color.White
            },
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * 「按住看原图」：按下期间宿主把滤镜从底图里摘掉（不写历史，见调用点），松手还原。
 *
 * 用 [awaitEachGesture] 而不是 `clickable`：语义是「按住」，需要按下与抬起两个时刻。
 * `finally` 保证抬手、手势被抢、面板销毁都会还原——否则画面会卡在「无滤镜」上回不来。
 */
@Composable
private fun CompareButton(onPressed: () -> Unit, onReleased: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF333333))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPressed()
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        onReleased()
                    }
                }
            },
    ) {
        Text(stringResource(R.string.mediaview_edit_compare), color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * 六格缩略图：每张表在 128px 底图上跑一遍。
 *
 * 挪到 [Dispatchers.Default]：读 `.cube`（约 1MB 文本）+ 逐像素插值，都不该占主线程。
 * 表经 [LutAssets] 记忆化，所以只有第一次进面板真的读盘，之后每次重进只是重跑插值。
 *
 * 取不到表的预设**记进 [FilterThumbs.missing]**（旧实现只是 `continue`）：面板据此画占位格 +
 * 顶部提示行。一张坏表绝不能让整张表「静默空掉」——用户看到的就是「全是黑框、点了没反应」。
 *
 * 缩略图不 `recycle()`：API 26+ 像素在 Java 堆上，面板销毁后交给 GC 就够
 * （手动回收反而会伤到还在显示这一帧的 Image）。
 */
internal suspend fun buildFilterThumbs(source: Bitmap, provider: (String) -> Lut3D?): FilterThumbs =
    withContext(Dispatchers.Default) {
        val thumbBase = scaleToLongEdge(source, FILTER_THUMB_LONG_EDGE)
        val out = LinkedHashMap<String, ImageBitmap>()
        val missing = mutableListOf<String>()
        for (id in FilterPresets.ids) {
            coroutineContext.ensureActive()
            val lut = provider(id)
            if (lut == null) {
                missing += id
                continue
            }
            // LutApply 是**就地且不可叠加**的：每格都必须从干净副本起
            val painted = thumbBase.copy(Bitmap.Config.ARGB_8888, true)
            LutApply.apply(painted, lut, FILTER_PRESET_INTENSITY)
            out[id] = painted.asImageBitmap()
        }
        FilterThumbs(out, missing)
    }