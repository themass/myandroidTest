package com.openapi.ks.dh

import java.io.File
import java.io.RandomAccessFile

/**
 * moc3 版本预闸（真机崩溃取证 2026-11-24 23:44 / 23:49，ADY-AL00）。
 *
 * 设备端 Cubism Core 是 4.2.1，`csmReviveMocInPlace` 只支持到 moc3 revision 4——
 * 设备日志逐字打出 `The Core unsupport later than moc3 ver:[4]. This moc3 ver is [5]`。
 * 加载失败后 native 的模型对象为空，但它**照常进入逐帧渲染**，`nativeOnDrawFrame`
 * 空指针 SIGSEGV（backtrace 全在 `libchatwaifu-live2d.so`），连 Java 熔断前兆都没有，
 * 只有 DhEngineWatchdog 事后把整个 LIVE2D 引擎隔离——「Live2D 都出不来」就是这么来的。
 *
 * native 拦不住，唯一能在加载前拦住的手段就是在这里读 model3.json 引用的那个 `.moc3`
 * 的版本字节（header: "MOC3" + revision）。任何一项读不出来都算拒绝：闸的目的是
 * 「宁可不出画面，也不能崩」。
 */
object Live2dModelGate {

    /** vendored Cubism Core 4.2.1 的 moc3 revision 上限（设备日志自证）。 */
    const val CORE_MAX_MOC3_REVISION = 4

    private val MOC_KEY = Regex("\"Moc\"\\s*:\\s*\"([^\"]+)\"")

    /** null = 放行；非 null = 拒绝原因（直接可展示）。 */
    fun rejectReason(
        cacheRoot: File?,
        modelDirName: String,
        modelJsonName: String = "$modelDirName.model3.json",
    ): String? {
        if (cacheRoot == null) return "模型目录不可用"
        return rejectReasonInDir(File(cacheRoot, modelDirName), modelJsonName)
    }

    /** 直接对模型目录本身设闸（install 暂存目录、形象轮盘共用）。 */
    fun rejectReasonInDir(dir: File, modelJsonName: String = "${dir.name}.model3.json"): String? {
        val json = File(dir, modelJsonName)
        if (!json.isFile) return "缺少 $modelJsonName"
        val mocName = runCatching { MOC_KEY.find(json.readText())?.groupValues?.get(1) }
            .getOrNull()
            ?: return "$modelJsonName 里读不到 Moc 引用"
        val moc = File(dir, mocName)
        if (!moc.isFile) return "缺少数模文件 $mocName"
        val header = ByteArray(5)
        val read = runCatching {
            RandomAccessFile(moc, "r").use { raf -> raf.read(header) }
        }.getOrNull() ?: return "读不了 $mocName"
        if (read < 5 || String(header, 0, 4, Charsets.US_ASCII) != "MOC3") {
            return "数模文件损坏：$mocName"
        }
        val revision = header[4].toInt() and 0xFF
        if (revision > CORE_MAX_MOC3_REVISION) {
            return "模型版本过新：moc3 r$revision > 端侧 Cubism Core r$CORE_MAX_MOC3_REVISION"
        }
        return null
    }
}
