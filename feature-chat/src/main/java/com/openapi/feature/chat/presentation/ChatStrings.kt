package com.openapi.feature.chat.presentation

/**
 * 给用户看的错误文案的端口（整改清单 P2-14b）。
 *
 * 以前这些文案硬编码在各 Action 类里，有两个后果：非中文系统的用户看到的仍是中文；
 * JVM 单测只能断言中文字面量，等于测试绑死了某一种语言。现在本模块只声明「需要哪条文案」，
 * 文字本体放 feature-chat 资源（values/ 英文、values-zh/ 中文），由 app 在 Application.onCreate
 * 注入实现（[com.openapi.feature.chat.ChatDependencies.installStrings]）。
 *
 * 单测注入 FakeChatStrings 标记串，只验证「取哪条文案、带什么数字」；
 * 真实措辞由 :app 的 Robolectric 测试钉住。
 */
interface ChatStrings {
    fun historyLoadOlderFailed(): String
    fun stoppedNoReply(): String
    fun emptyReply(): String
    fun replyFailed(): String
    fun replyTruncated(): String
    /** 单条删除与会话删除共用这条（中文措辞相同，整改清单点名的键位合一）。 */
    fun deleteFailed(): String
    fun readMessagesFailed(): String
    fun deleteManyPartFailed(failed: Int): String
    fun deleteManyPartUnsynced(unsynced: Int): String
    /** 多条失败原因拼一行时的分隔符（中文「，」，英文 ", "——标点也是本地化文案）。 */
    fun errorPartSeparator(): String
    fun clearFailed(failed: Int): String
    fun sessionCreateFailed(): String
    fun sessionRenameFailed(): String
    fun attachmentReadFailed(): String
    fun imageReadFailed(): String
    fun imageEditFallback(): String
    fun speakFailed(): String
    fun roleSwitchFailed(): String
    fun roleCreateFailed(): String
    fun roleUpdateFailed(): String
    fun roleDeleteFailed(): String
    /** 视频超过 25 MiB（P2-13；上限在执行前就拦，文案里不重复报数字——数字与规则同源，见 VideoRules）。 */
    fun videoTooLarge(): String
    /** 选了非 MP4 的视频。 */
    fun videoNotMp4(): String
    /** 单次粗定位没拿到结果（超时、GPS 关、无服务）。 */
    fun locationFailed(): String
    /**
     * 草稿托盘里的**附件**（图片+视频）已经到 [max] 个，这一个进不去。
     *
     * 措辞刻意是「附件」而不是「图片」：图与视频在 wire 上是同一条 `SendBody.attachments`、
     * 共用同一个额度，被拦下的可能是第 10 个视频——说「图片」会让用户去删一张其实没超的图。
     *
     * 必须有：选择器的 `maxItems` 只是**建议**，厂商 ROM 的选择器可以不认，
     * 那时附件会一路加到草稿里、上传花完钱再被服务端的 422 打回来。
     */
    fun attachmentLimitReached(max: Int): String
    /**
     * 帧间看门狗判定「这一轮再等下去也不会有内容」时的那句话（任务：SSE 无帧挂死）。
     *
     * data 层（TurnEventCollector）只能给出协议级的兜底中文串，用户真正看到的这句话
     * 必须按系统语言取——所以看门狗在事件里带的是 code，文案在这里。
     */
    fun turnTimeout(): String
    /**
     * 点停止之后服务端那一轮还没结束、取消并重试仍然吃 409 时的那句话。
     * 用户需要知道「不是我没发出去，是上一轮还占着」，以及下一步该做什么。
     */
    fun previousTurnRunning(): String
    /**
     * A40「插话」提交失败：这条排队文字已放回输入框。
     * 用户需要知道两件事：没发出去、文字没丢（在输入框里可以改后再发）。
     */
    fun steerSendFailed(): String
}
