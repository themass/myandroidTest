package com.openapi.common.media

import java.io.File

/**
 * 附件暂存目录的孤儿文件清扫（cleanup 项 5）。
 *
 * **一次实现，两处调用**：「对话」页（`:feature-chat` 的 `chat_attachments` + 拍照的 `camera`）
 * 与陪伴页（`:feature-companion` 的 `companion_attachments`）都在 `filesDir` 下写一次性暂存文件，
 * feature 之间不许互相依赖（`:app` 的 ModuleGraphTest 第 1 条），所以这份逻辑只能放在第 0 层。
 *
 * 孤儿从哪来：
 * - 选择器一次给多张，超出额度那张被闸挡下 → 那个文件从没进过任何托盘，谁也不引用它；
 * - 用户选完就退出、或编辑半途取消 → 同上；
 * - 拍照那条路先写文件再拉起相机，取消拍摄也留一个空/半张文件。
 * 这些文件以前**没有任何回收路径**：目录只增不减（Task 4 报告 §9 登记的就是这个缺口）。
 *
 * 为什么按**修改时间**而不是「进程启动时刻」：同一台机器上的暂存文件被两条页面共用，
 * 判「比本次启动更早」会误删另一次会话还在用的文件（陪伴页正在逐张上传时尤其明显）。
 * [MAX_AGE_MILLIS] 取 7 天：远大于任何一次「失败后过一会儿再按一次发送」的窗口，
 * 又不至于让目录无限增长。代价写清楚：超过 7 天且**只在本地**才有字节的旧图片消息，
 * 下次联网刷新历史时会走服务端签名 URL（那条路本来就是权威），离线时那一张退化成加载失败。
 *
 * 只删**文件**，不递归删子目录：这几个目录本来就没有子目录，保守一点不会误伤别的东西。
 * 删除失败一律忽略：GC 永远不该把一次选图变成错误。
 */
object StagingGc {

    /** 暂存文件的最长寿命：超过这个年龄、又不被任何托盘引用的文件按垃圾处理。 */
    const val MAX_AGE_MILLIS: Long = 7L * 24 * 60 * 60 * 1000

    /**
     * 删掉 [dir] 下年龄超过 [MAX_AGE_MILLIS] 的文件，回删掉几个（测试用；调用方不看返回值）。
     * 目录不存在或读不到 → 0，不抛。
     */
    fun sweep(dir: File, now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - MAX_AGE_MILLIS
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return 0
        var deleted = 0
        for (child in children) {
            if (!child.isFile) continue
            if (child.lastModified() >= cutoff) continue
            if (runCatching { child.delete() }.getOrDefault(false)) deleted++
        }
        return deleted
    }
}
