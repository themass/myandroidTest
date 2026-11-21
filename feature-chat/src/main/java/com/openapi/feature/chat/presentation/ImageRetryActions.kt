package com.openapi.feature.chat.presentation

import com.openapi.feature.chat.domain.model.ImageSource
import com.openapi.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 点击加载失败的图片气泡 → 重试（整改清单 P1-10）。
 *
 * 分工要分清：本机的文件重读（强制重新解码）由 chatkit 组件自己完成——它换个 Coil 缓存
 * key 重新加载就够了，不需要网络。这里只负责「远程图」的换源：用和历史整页换 URL 同一条
 * `GET /uploads/{id}` 通道重新签一个 300 秒的地址（服务端不需要新接口）。换到就更新该下标
 * 图片的 path——来源一变，组件会把这一张重建回 Loading 并按新地址加载。
 *
 * 换不到（旧链路、对象已删、网络失败）时什么都不改：界面上的失败占位还在，用户能再点一次。
 *
 * 在途去重：同一张图的重试没结束前，再点不产生第二次请求——急躁的连点不该在网络上排队。
 */
internal class ImageRetryActions(
    private val repository: ChatRepository,
    private val state: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
) {
    /** 正在重新取地址的「消息 id:图片下标」。只在单一协程调度器（viewModelScope）上读写。 */
    private val inFlight = mutableSetOf<String>()

    fun retry(messageId: String, index: Int) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        val image = message.images.getOrNull(index) ?: return
        // 本机文件没有可换的地址：组件的强制重载已经是全部能做的事
        if (image.source != ImageSource.REMOTE) return
        val uploadId = image.uploadId ?: return // 没上传记录，无从查起
        val ticket = "$messageId:$index"
        if (!inFlight.add(ticket)) return
        scope.launch {
            val url = runCatching { repository.refreshImageUrl(uploadId) }.getOrNull()
            inFlight.remove(ticket)
            if (url.isNullOrEmpty()) return@launch
            state.update { current ->
                current.copy(messages = current.messages.map { m ->
                    if (m.id != messageId) m
                    else m.copy(images = m.images.mapIndexed { i, img -> if (i == index) img.copy(path = url) else img })
                })
            }
        }
    }
}
