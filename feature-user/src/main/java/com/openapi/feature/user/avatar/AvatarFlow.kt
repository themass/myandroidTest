package com.openapi.feature.user.avatar

import java.io.File

/**
 * 二次元头像流程的**纯状态机**（JVM 单测钉死，无 Android 依赖）。
 *
 * 闭环：选图/拍照（已暂存为本地文件）→ 上传（复用 `/v1/uploads`）→ 生成（`/v1/avatars/generate`，
 * 服务端 provider 可切换，端侧不感知）→ 预览确认 → 应用（`/v1/avatars/apply`）→ 成功。
 *
 * 两条钉死的纪律（用户拍板）：
 * 1. **失败不覆盖旧头像**：状态机里没有任何「写头像指针」的动作——指针只在
 *    [UserAvatarStore.install] 成功落盘后换，而 install 只发生在 ApplyDone 之后。
 *    任何 Failure / Retake / Reset 都动不到旧头像。
 * 2. **失败可重试且不重复上传**：Failure 记住已完成的阶段产物
 *    （stagedPath / sourceUploadId / avatarUploadId），Retry 从最近的断点续跑。
 *
 * 预览图字节不进状态（ByteArray 没有值语义，状态相等判断会退化成引用比较）：
 * UI 侧自己持有 preview bytes，状态机只管 id 与阶段。
 */

/** 失败发生在哪个阶段——决定 Retry 从哪里续跑。 */
enum class AvatarFailureStage { STAGE, UPLOAD, GENERATE, APPLY }

/** 失败的人话分类（文案在 strings.xml；**绝不把服务端原始 body 透给用户**，同 photosolve 纪律）。 */
enum class AvatarUserReason { NOT_LOGGED_IN, BAD_IMAGE, QUOTA, SERVICE_UNAVAILABLE, NETWORK, UNKNOWN }

sealed interface AvatarUiState {
    /** 还没选图。 */
    data object Idle : AvatarUiState

    /** 已压缩暂存到本地文件（[stagedPath]），等用户点「生成」。 */
    data class Staged(val stagedPath: String) : AvatarUiState

    data class Uploading(val stagedPath: String) : AvatarUiState

    /** 源图已在服务端，正在生成二次元风格。 */
    data class Generating(val sourceUploadId: String) : AvatarUiState

    /** 生成完成，等用户「使用 / 换一张」。（预览字节由 UI 侧另行持有。） */
    data class Previewing(val avatarUploadId: String) : AvatarUiState

    data class Applying(val avatarUploadId: String) : AvatarUiState

    /** 已应用。旧头像指针此刻才真正换过。 */
    data object Saved : AvatarUiState

    /** 失败态：带断点信息，Retry 据此续跑。 */
    data class Failed(
        val stage: AvatarFailureStage,
        val reason: AvatarUserReason,
        val stagedPath: String?,
        val sourceUploadId: String?,
        val avatarUploadId: String?,
    ) : AvatarUiState
}

sealed interface AvatarEvent {
    /** 选图/拍照结果已压缩落盘。 */
    data class ImageStaged(val path: String) : AvatarEvent

    data object UploadStarted : AvatarEvent
    data class UploadDone(val uploadId: String) : AvatarEvent
    data class GenerateDone(val avatarUploadId: String) : AvatarEvent
    data object ApplyStarted : AvatarEvent
    data object ApplyDone : AvatarEvent

    data class Failure(val stage: AvatarFailureStage, val reason: AvatarUserReason) : AvatarEvent

    /** 从失败断点续跑（不重复已完成的阶段）。 */
    data object Retry : AvatarEvent

    /** 换一张照片：丢掉所有服务端中间产物，回到选图。 */
    data object Retake : AvatarEvent
}

/**
 * 状态机本体。单线程使用（与 UI 协程同一线程），无锁。
 *
 * 守卫是刻意的：非法转移（比如没预览就 ApplyStarted）**直接忽略**而不是抛——
 * 连点/竞态在 UI 层防不胜防，状态机兜底比崩溃好。合法路径表见 [AvatarFlowTest]。
 */
class AvatarFlow {

    var state: AvatarUiState = AvatarUiState.Idle
        private set

    // 断点检查点（Retry 的依据）：每完成一个阶段就记一次，跨阶段累积。
    // 为什么状态里不带全链：Applying 只需要 avatarUploadId 就能干活，但失败时要能把
    // 「源图也还在」一并报出来——只从当前状态捞会丢上游 id（Apply 阶段失败重试却重新上传，
    // 就是这条测试钉住的 bug）。
    private var stagedPath: String? = null
    private var sourceUploadId: String? = null
    private var avatarUploadId: String? = null

    fun on(event: AvatarEvent): AvatarUiState {
        state = when (event) {
            is AvatarEvent.ImageStaged -> {
                // 任何状态下拿到新的暂存文件都进入 Staged（换一张的自然覆盖）。
                // 换图 = 旧照片的服务端中间产物全部作废：不清掉的话，Retry 会去续跑旧图的生成。
                stagedPath = event.path
                sourceUploadId = null
                avatarUploadId = null
                AvatarUiState.Staged(event.path)
            }

            AvatarEvent.UploadStarted -> when (val s = state) {
                is AvatarUiState.Staged -> AvatarUiState.Uploading(s.stagedPath)
                else -> s
            }

            is AvatarEvent.UploadDone -> when (state) {
                is AvatarUiState.Uploading -> {
                    sourceUploadId = event.uploadId
                    AvatarUiState.Generating(event.uploadId)
                }
                else -> state
            }

            is AvatarEvent.GenerateDone -> when (state) {
                is AvatarUiState.Generating -> {
                    avatarUploadId = event.avatarUploadId
                    AvatarUiState.Previewing(event.avatarUploadId)
                }
                else -> state
            }

            AvatarEvent.ApplyStarted -> when (val s = state) {
                is AvatarUiState.Previewing -> AvatarUiState.Applying(s.avatarUploadId)
                else -> s
            }

            AvatarEvent.ApplyDone -> when (state) {
                is AvatarUiState.Applying -> AvatarUiState.Saved
                else -> state
            }

            is AvatarEvent.Failure -> AvatarUiState.Failed(
                stage = event.stage,
                reason = event.reason,
                // 断点取累积检查点而不是当前状态：Apply 阶段失败时状态里只剩 avatarUploadId，
                // 「源图也还在」必须一并报出来（否则 Retry 会重新上传——AvatarFlowTest 钉住的 bug）。
                stagedPath = stagedPath,
                sourceUploadId = sourceUploadId,
                avatarUploadId = avatarUploadId,
            )

            AvatarEvent.Retry -> {
                val f = state as? AvatarUiState.Failed ?: return state
                when {
                    // 断点就近原则：有 avatarUploadId 就不重新生成，有 sourceUploadId 就不重新上传。
                    f.avatarUploadId != null -> AvatarUiState.Applying(f.avatarUploadId)
                    f.sourceUploadId != null -> AvatarUiState.Generating(f.sourceUploadId)
                    f.stagedPath != null && File(f.stagedPath).exists() -> AvatarUiState.Uploading(f.stagedPath)
                    else -> AvatarUiState.Idle // 暂存文件丢了才回到选图
                }
            }

            AvatarEvent.Retake -> {
                // 换照片 = 服务端中间产物全部作废，检查点一并清掉（防 Retry 复活旧图的 id）。
                stagedPath = null
                sourceUploadId = null
                avatarUploadId = null
                AvatarUiState.Idle
            }
        }
        return state
    }

    /** 「使用这张」只在 Previewing 合法；连点第二次会被忽略。 */
    fun canApply(): Boolean = state is AvatarUiState.Previewing
}
