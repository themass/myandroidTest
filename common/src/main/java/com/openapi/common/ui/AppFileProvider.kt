package com.openapi.common.ui

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 宿主 app 那个 `androidx.core.content.FileProvider` 的 authority（清单里
 * `app/src/main/AndroidManifest.xml` 的 `<provider android:authorities=…>`）。
 *
 * 为什么收在 `:common`（第 0 层）：这个串以前是**两份字面量**——`:feature-chat` 的
 * `ChatPlatform.FILE_PROVIDER` 与 `:feature-companion` 的 `CompanionScreen` 内联串
 * （`:app` 的 `AgentChatScreen` 是第三份）。三份都对不上清单时不会有任何编译错误，
 * 而表现是「点拍摄/点打开，什么也没发生」（FileProvider 抛 IllegalArgumentException
 * 被 catch 成一句 Toast）。feature 之间不许互相依赖（ModuleGraphTest 第 1 条），
 * 所以两边唯一能共用的只有这一层。
 *
 * **改这里必须同时改清单**——`:app` 的 `FileProviderAuthorityTest` 会读清单文本把两者钉在一起。
 */
object AppFileProvider {

    const val AUTHORITY = "com.openapi.ks.chat.fileprovider"

    /**
     * 把应用私有目录里的文件换成交给其它应用的 `content://` 地址。
     *
     * 只包一层 [androidx.core.content.FileProvider.getUriForFile]：调用方要的是「别写第三遍
     * authority」，不是要一个新的抽象——所以不吞异常，异常语义原样交给调用方（各处处理不同：
     * 有的是 Toast，有的是 catch 后走另一条路）。
     */
    fun uriFor(context: Context, file: File): Uri =
        androidx.core.content.FileProvider.getUriForFile(context, AUTHORITY, file)
}
