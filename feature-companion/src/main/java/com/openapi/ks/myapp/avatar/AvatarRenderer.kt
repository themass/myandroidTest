package com.openapi.ks.myapp.avatar

import androidx.annotation.DrawableRes

/**
 * 形象素材描述，只含渲染需要的东西，不含业务逻辑。
 *
 * 资源 id 由调用方解析成 Int：这一层不认识角色卡格式，也不需要 Context——换素材
 * 来源（内置、下载包、骨架文件）时只改调用方。缺失一律用 null 表示：0 不是合法
 * 的资源 id，传进 painterResource 会崩，所以「没有」必须是显式的。
 */
data class AvatarSpec(
    @DrawableRes val portraitRes: Int? = null,
    /** 情绪名 → 表情图，名字来自角色卡的 emotion_map。缺名字就是没这个表情。 */
    val expressions: Map<String, Int> = emptyMap(),
    @DrawableRes val mouthClosedRes: Int? = null,
    @DrawableRes val mouthOpenRes: Int? = null,
    @DrawableRes val eyeOpenRes: Int? = null,
    @DrawableRes val eyeClosedRes: Int? = null,
    /** 下载的形象包素材。有它就优先用文件，没有就用上面的内置资源 id。 */
    val files: AvatarFiles? = null,
)

/** 触摸结果。v1 一律 [Ignored]；接入拖动渲染器后才会出现 [Handled]。 */
sealed interface TouchResult {
    data object Handled : TouchResult
    data object Ignored : TouchResult
}

enum class AvatarTouchKind { DOWN, MOVE, UP, TAP }

/**
 * 手指事件。坐标为相对形象区域的归一化值，具体含义由渲染器解释——v1 不读它，
 * 拖动渲染器才需要知道手指在形象内的相对位置。
 */
data class AvatarTouch(
    val x: Float,
    val y: Float,
    val kind: AvatarTouchKind,
)

/**
 * 渲染器接口。生命周期：[bind] → 若干 [dispatch] / [onTouch] → [release]。
 *
 * [release] 之后不再接收输入，渲染器要停掉自己的定时任务（眨眼等）——否则
 * 页面销毁后协程仍在跑，会一直持有已经没人看的 StateFlow。
 */
interface AvatarRenderer {
    val capabilities: Set<AvatarCapability>

    fun bind(spec: AvatarSpec)

    fun dispatch(command: AvatarCommand)

    fun onTouch(event: AvatarTouch): TouchResult = TouchResult.Ignored

    fun release()
}
