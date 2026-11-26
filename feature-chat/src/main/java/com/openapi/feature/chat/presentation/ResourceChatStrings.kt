package com.openapi.feature.chat.presentation

import android.content.Context
import com.openapi.feature.chat.R

/**
 * [ChatStrings] 的资源实现（整改清单 P2-14b）：文字在 feature-chat 资源里
 * （values/ 英文、values-zh/ 中文），这里只按当前系统语言取值。
 *
 * app 在 Application.onCreate 里注入（ChatDependencies.installStrings）。
 * 只应传应用级 Context——它会一直被持有，Activity 传进来就是泄漏。
 */
class ResourceChatStrings(private val context: Context) : ChatStrings {
    override fun historyLoadOlderFailed() = context.getString(R.string.chat_load_older_failed)
    override fun emptyReply() = context.getString(R.string.chat_empty_reply)
    override fun replyFailed() = context.getString(R.string.chat_reply_failed)
    override fun replyTruncated() = context.getString(R.string.chat_reply_truncated)
    override fun deleteFailed() = context.getString(R.string.chat_delete_failed)
    override fun readMessagesFailed() = context.getString(R.string.chat_read_messages_failed)
    override fun deleteManyPartFailed(failed: Int) = context.getString(R.string.chat_delete_part_failed, failed)
    override fun deleteManyPartUnsynced(unsynced: Int) = context.getString(R.string.chat_delete_part_unsynced, unsynced)
    override fun errorPartSeparator() = context.getString(R.string.chat_error_separator)
    override fun clearFailed(failed: Int) = context.getString(R.string.chat_clear_failed, failed)
    override fun sessionCreateFailed() = context.getString(R.string.chat_session_create_failed)
    override fun sessionRenameFailed() = context.getString(R.string.chat_session_rename_failed)
    override fun attachmentReadFailed() = context.getString(R.string.chat_attachment_read_failed)
    override fun imageReadFailed() = context.getString(R.string.chat_image_read_failed)
    override fun imageEditFallback() = context.getString(R.string.chat_image_edit_fallback)
    override fun speakFailed() = context.getString(R.string.chat_speak_failed)
    override fun roleSwitchFailed() = context.getString(R.string.chat_role_switch_failed)
    override fun roleCreateFailed() = context.getString(R.string.chat_role_create_failed)
    override fun roleUpdateFailed() = context.getString(R.string.chat_role_update_failed)
    override fun roleDeleteFailed() = context.getString(R.string.chat_role_delete_failed)
    override fun videoTooLarge() = context.getString(R.string.chat_video_too_large)
    override fun videoNotMp4() = context.getString(R.string.chat_video_not_mp4)
    override fun locationFailed() = context.getString(R.string.chat_location_failed)
    override fun attachmentLimitReached(max: Int) = context.getString(R.string.chat_attachment_limit_reached, max)
    override fun turnTimeout() = context.getString(R.string.chat_turn_timeout)
    override fun previousTurnRunning() = context.getString(R.string.chat_previous_turn_running)
    override fun steerSendFailed() = context.getString(R.string.chat_steer_send_failed)
}
