package com.openapi.ks.myapp.companion

/**
 * 陪伴页错误提示的文案端口（对齐「对话」页的 [com.openapi.feature.chat.presentation.ChatStrings]）。
 *
 * 为什么要有它：这些句子以前是 ViewModel 里的硬编码中文字面量，于是非中文系统的用户看到的
 * 仍是中文，而 JVM 单测只能断言中文字面量（等于测试绑死一种语言）。同一个提交里输入区的
 * 7 条文案已经走资源（`values/ks_copied.xml` + `values-zh/`），这三条是漏下的。
 *
 * 文字本体在 feature-companion 资源（values/ 英文、values-zh/ 中文），由 app 在
 * Application.onCreate 注入 [ResourceCompanionStrings]；JVM 单测注入
 * `FakeCompanionStrings` 标记串，只验证「取哪条文案、带什么数字」，
 * 真实措辞由 :app 的 Robolectric 测试钉住。
 */
interface CompanionStrings {
    /** 部分图片上传失败：重点是把「图还在、再按一次就行」说给用户。 */
    fun uploadPartialFailed(): String

    /** 房间不支持图片（协商变了）：图传不了，也不该只把文本单独发出去。 */
    fun uploadUnsupported(): String

    /** 图已经传好但 attachments 没落进房间：刻意不复用 [uploadPartialFailed]——「上传失败」是句谎话。 */
    fun attachmentDeliveryFailed(): String

    /** 图片还在传，动作文案不能插队。 */
    fun uploadInProgress(): String

    /** 第二次打开选择器又选了图、但条数已经到顶。不说清楚就像图被吞了。 */
    fun imageLimitReached(max: Int): String
}

/**
 * 依赖接入点（与 `ChatDependencies` 同款）：`:feature-companion` 不认识 `:app`，
 * 由 app 在启动时注入资源实现。未注入时 [require] 抛一条指名道姓的错误，
 * 而不是在界面层空指针。
 */
object CompanionDependencies {

    @Volatile
    private var strings: CompanionStrings? = null

    fun installStrings(strings: CompanionStrings) {
        this.strings = strings
    }

    fun strings(): CompanionStrings = checkNotNull(strings) {
        "CompanionStrings 未注入：请在 Application.onCreate 中调用 CompanionDependencies.installStrings(...)"
    }
}
