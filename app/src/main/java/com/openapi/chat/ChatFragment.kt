package com.openapi.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.openapi.chat.presentation.ui.ChatRoute

/**
 * 以 Fragment 形式承载对话界面，供主页的对话 Tab 使用（[ChatActivity] 是 Activity 形式）。
 *
 * 两者共用同一个 [ChatRoute]，所以从 Tab 和从抽屉/启动页进来看到的是同一套实现——
 * 老版本这里是 `ChatMessagesListFragment` 与 `CustomChatMessages*Activity` 两套平行代码。
 */
class ChatFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent { ChatRoute() }
    }
}
