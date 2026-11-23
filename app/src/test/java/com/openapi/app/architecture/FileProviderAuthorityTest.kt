package com.openapi.app.architecture

import com.openapi.common.ui.AppFileProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * FileProvider 的 authority：代码里的常量必须与清单里的 `<provider>` 一致（cleanup 项 14）。
 *
 * 为什么要有这条：`com.openapi.ks.chat.fileprovider` 以前是**三份字面量**（对话页
 * `ChatPlatform`、陪伴页 `CompanionScreen`、:app 导出分享），对不上清单时没有任何编译错误，
 * 表现只是「点拍摄/点打开，什么也没发生」——FileProvider 抛 IllegalArgumentException，
 * 各处 catch 成一句 Toast。现在串收在 :common 的 [AppFileProvider]，这里把它与清单文本钉在一起：
 * 改了清单忘了改常量（或反过来）就红，而不是装机后才发现。
 *
 * 直接读清单文件而不是运行时查询：这条规则要钉的是**源码一致性**，跑在 JVM 上、不需要设备。
 */
class FileProviderAuthorityTest {

    private val root: File = File("..").canonicalFile

    @Test
    fun theConstantMatchesTheManifestProviderAuthorities() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        val provider = Regex("""<provider[^>]*android:name="androidx\.core\.content\.FileProvider"[^>]*>""", RegexOption.DOT_MATCHES_ALL)
            .find(manifest)
            ?: error("app 清单里找不到 androidx.core.content.FileProvider 的 <provider>")
        val authority = Regex("""android:authorities="([^"]+)"""").find(provider.value)?.groupValues?.get(1)
            ?: error("<provider> 上没有 android:authorities")

        assertEquals("清单与 :common 的常量必须逐字一致（改一处必须改另一处）", authority, AppFileProvider.AUTHORITY)
    }
}
