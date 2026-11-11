package com.openapi.app.data.chat

import com.openapi.common.net.http.BaseService
import com.openapi.feature.chat.data.remote.ChatRepositoryImpl
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 真机回归：对话里发图片/文件一律「发送失败」，日志是 BaseRequest 构造时 context 为 null 的 NPE。
 *
 * 原因是 `BaseService().apply { setup(context) }`：在 apply 里 `this` 是 BaseService，
 * 它恰好有一个公开字段 `context`，于是 `context` 解析成了 BaseService 自己那个还是 null 的字段，
 * 而不是 ChatRepositoryImpl 的构造参数——等于 `setup(null)`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatRepositoryWiringTest {

    @Before
    fun setUp() {
        com.openapi.core.app.AppContext.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun networkServiceReceivesTheRealContext() {
        val app = RuntimeEnvironment.getApplication()
        val repo = ChatRepositoryImpl(app)

        val field = ChatRepositoryImpl::class.java.getDeclaredField("baseService").apply { isAccessible = true }
        val service = field.get(repo) as BaseService

        assertSame("BaseService 拿到的必须是传进来的 Context，否则所有上传在构造请求时 NPE", app, service.context)
    }
}
