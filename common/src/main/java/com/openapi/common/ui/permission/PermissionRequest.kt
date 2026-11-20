package com.openapi.common.ui.permission

/**
 * 一次权限请求：要哪些权限 + 为什么要。`rationale` 会出现在解释对话框和「去设置」对话框里，
 * 所以写成用户能看懂的一句话（「发送语音消息需要使用麦克风」），不要写技术描述。
 *
 * 用 [Permissions] 里的预设构造，不要到处手写 `Manifest.permission.XXX`：
 * ```kotlin
 * val request = PermissionRequest(Permissions.CAMERA, "拍照给 AI 看需要相机权限")
 * gateway.request(request)
 * ```
 * 预设在不同 Android 版本上返回不同的权限串（例如通知在 Android 13 之前不需要申请）。
 * 返回空列表表示当前系统上不需要任何权限，请求会立即成功。
 */
class PermissionRequest(
    val permissions: List<String>,
    val rationale: String,
) {
    constructor(permissions: Array<out String>, rationale: String) : this(permissions.toList(), rationale)

    /** 同一组权限视为同一个请求，与理由文字无关：用于合并重复点击。 */
    internal val key: String = permissions.sorted().joinToString(",")

    override fun equals(other: Any?): Boolean =
        other is PermissionRequest && permissions == other.permissions && rationale == other.rationale

    override fun hashCode(): Int = 31 * permissions.hashCode() + rationale.hashCode()

    override fun toString(): String = "PermissionRequest($permissions)"
}

/** 一次请求的最终结果。三种情况对应界面上三种不同的处理。 */
sealed interface PermissionOutcome {

    /** 全部已授权，可以直接做需要权限的事。 */
    data object Granted : PermissionOutcome

    /** 用户这次拒绝了，但还能再问：下次触发功能时可以重新请求（会先弹解释）。 */
    data class Denied(val denied: List<String>) : PermissionOutcome

    /**
     * 系统授权框不会再出现（用户选了「不再询问」，或系统策略如此），只能去应用设置页手动开启。
     *
     * 不要把它当普通拒绝处理：此时再调 [PermissionGatewayHandle.request] 不会弹系统框，
     * 界面应当明确告诉用户去哪里开，或直接提供一个跳转设置的按钮。已经跳设置、用户回来仍没开时也返回这个结果。
     */
    data class PermanentlyDenied(val denied: List<String>) : PermissionOutcome
}
