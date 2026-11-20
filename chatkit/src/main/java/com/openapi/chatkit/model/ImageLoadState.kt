package com.openapi.chatkit.model

/**
 * 一张图片在气泡里的加载/重试状态（整改清单 P1-10）。
 *
 * - [Loading]：首次加载中（占位底色）
 * - [Success]：已显示
 * - [Failed]：加载失败，显示失败占位（图标 + 文案），整块可点击重试
 * - [Retrying]：用户点过重试、正在重新加载；失败占位保留，图标换成进度样式
 */
enum class ImageVisualStatus { Loading, Success, Failed, Retrying }

/**
 * 单张图片的加载/重试状态机（整改清单 P1-10）。
 *
 * UI 观察 Coil 的成功 / 失败回调，把事件喂进这里的转移函数，由它决定现在该显示什么、
 * 这次重试点击该不该被接受。抽成纯数据类是因为「失败 → 点击 → 重试中 → 成功 / 又失败」
 * 的转移就是这段整改的全部行为，不该以 if 的形式散落在 Compose 里，而且要能脱离
 * Compose 单测（`ImageLoadStateTest`）。
 *
 * 转移表（其余组合一律原样返回，用来吃掉换源之后迟到的旧回调）：
 * - Loading  --onLoaded--> Success；Loading  --onFailed--> Failed
 * - Retrying --onLoaded--> Success；Retrying --onFailed--> Failed
 * - Failed   --onRetryClick--> Retrying（[attempt] + 1）
 * - Loading / Success / Retrying 下的 onRetryClick 被忽略：没有失败可重试，或已在途
 *
 * [attempt] 是第几次手动重试（0 = 首次自然加载）。UI 把它拼进 Coil 的缓存 key
 * （mediaview 的 `rememberStatefulImageModel`）：换 key 必然缓存未命中，本机文件被强制
 * 重新解码、远程地址被强制重新请求——否则内存缓存里的失败结果会被直接回放。
 */
data class ImageLoadState(
    val status: ImageVisualStatus = ImageVisualStatus.Loading,
    val attempt: Int = 0,
) {
    fun onLoaded(): ImageLoadState =
        if (status == ImageVisualStatus.Loading || status == ImageVisualStatus.Retrying) {
            copy(status = ImageVisualStatus.Success)
        } else {
            this
        }

    fun onFailed(): ImageLoadState =
        if (status == ImageVisualStatus.Loading || status == ImageVisualStatus.Retrying) {
            copy(status = ImageVisualStatus.Failed)
        } else {
            this
        }

    fun onRetryClick(): ImageLoadState =
        if (status == ImageVisualStatus.Failed) {
            copy(status = ImageVisualStatus.Retrying, attempt = attempt + 1)
        } else {
            this
        }
}
