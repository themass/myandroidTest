package com.openapi.feature.chat.presentation

/**
 * JVM 单测没有资源环境：注入标记串，断言「取了哪条文案、带什么数字」。
 * 真实措辞（中英文）由 :app 的 ResourceChatStringsTest（Robolectric）钉住。
 */
internal class FakeChatStrings : ChatStrings {
    override fun historyLoadOlderFailed() = "S:historyLoadOlderFailed"
    override fun stoppedNoReply() = "S:stoppedNoReply"
    override fun emptyReply() = "S:emptyReply"
    override fun replyFailed() = "S:replyFailed"
    override fun replyTruncated() = "S:replyTruncated"
    override fun deleteFailed() = "S:deleteFailed"
    override fun readMessagesFailed() = "S:readMessagesFailed"
    override fun deleteManyPartFailed(failed: Int) = "S:deleteManyPartFailed:$failed"
    override fun deleteManyPartUnsynced(unsynced: Int) = "S:deleteManyPartUnsynced:$unsynced"
    override fun errorPartSeparator() = "，"
    override fun clearFailed(failed: Int) = "S:clearFailed:$failed"
    override fun sessionCreateFailed() = "S:sessionCreateFailed"
    override fun sessionRenameFailed() = "S:sessionRenameFailed"
    override fun attachmentReadFailed() = "S:attachmentReadFailed"
    override fun imageReadFailed() = "S:imageReadFailed"
    override fun imageEditFallback() = "S:imageEditFallback"
    override fun speakFailed() = "S:speakFailed"
    override fun roleSwitchFailed() = "S:roleSwitchFailed"
    override fun roleCreateFailed() = "S:roleCreateFailed"
    override fun roleUpdateFailed() = "S:roleUpdateFailed"
    override fun roleDeleteFailed() = "S:roleDeleteFailed"
    override fun videoTooLarge() = "S:videoTooLarge"
    override fun videoNotMp4() = "S:videoNotMp4"
    override fun locationFailed() = "S:locationFailed"
}
