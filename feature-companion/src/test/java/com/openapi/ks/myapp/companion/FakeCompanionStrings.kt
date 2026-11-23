package com.openapi.ks.myapp.companion

/**
 * JVM 单测没有资源环境：注入标记串，断言「取了哪条文案、带什么数字」。
 * 真实措辞（中英文）由 :app 的 CompanionStringsResourceTest（Robolectric）钉住。
 */
class FakeCompanionStrings : CompanionStrings {
    override fun uploadPartialFailed() = "S:uploadPartialFailed"
    override fun uploadUnsupported() = "S:uploadUnsupported"
    override fun attachmentDeliveryFailed() = "S:attachmentDeliveryFailed"
    override fun uploadInProgress() = "S:uploadInProgress"
    override fun imageLimitReached(max: Int) = "S:imageLimitReached:$max"
}
