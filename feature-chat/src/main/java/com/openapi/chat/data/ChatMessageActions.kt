package com.openapi.chat.data

import android.content.Context
import com.openapi.core.db.FavoriteVo
import com.openapi.network.http.BaseService
import com.openapi.core.util.FavoriteUtil
import com.openapi.chat.data.remote.FeedbackContentForm
import com.openapi.core.model.NullReturnVo
import com.openapi.core.config.Constants

/**
 * 对话消息的点赞、点踩，与旧版 MyIncomingAudioFooterView / FeedbackDialog 保持一致。
 */
object ChatMessageActions {

    private const val TAG = "chat_message_action"

    /**
     * 点赞：记到本地收藏（类型 CHAT_OK，以消息 id 为键）。
     * FavoriteUtil 的写入是切换语义，再点一次即取消，所以调用方每次点击只调用一次。
     */
    fun toggleLike(context: Context, messageId: String) {
        val vo = FavoriteVo()
        vo.setType(Constants.FavoriteType.CHAT_OK)
        vo.setItemUrl(messageId)
        vo.setName("chat")
        FavoriteUtil.modLocalFavoritesAsync(context, vo) { _ -> }
    }

    /** 点踩反馈：提交到服务端 CHAT_FEEDBACK_URL，字段与旧版相同（content 为反馈文字，id 为消息 id）。 */
    fun sendFeedback(context: Context, messageId: String, feedback: String) {
        val form = FeedbackContentForm()
        form.setId(messageId)
        form.setContent(feedback)
        form.setTag("[]")
        val service = BaseService()
        service.setup(context)
        service.postData(
            Constants.getUrl(Constants.CHAT_FEEDBACK_URL),
            form,
            null,
            null,
            TAG,
            NullReturnVo::class.java,
        )
    }
}
