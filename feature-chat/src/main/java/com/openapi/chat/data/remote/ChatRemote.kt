package com.openapi.chat.data.remote

import com.openapi.network.http.BaseService
import com.openapi.network.awaitPost
import com.openapi.network.awaitPostOk

/** 对话相关日志和请求的标签。 */
internal const val CHAT_TAG = "chat"

/**
 * 对话的远程调用包装。失败统一返回 null（或 false），不抛异常：
 * 调用方只关心"成功还是不成功"，错误详情已经在 ResponseErrorListener 里打了日志。
 */
internal class ChatRemote(private val baseService: BaseService) {

    suspend fun <T> postForResult(url: String, param: Any, clazz: Class<T>): T? =
        baseService.awaitPost(url, param, clazz, CHAT_TAG)

    /** 只关心是否成功：服务端接口只返回 errno，没有业务数据时用它。 */
    suspend fun postForOk(url: String, param: Any): Boolean =
        baseService.awaitPostOk(url, param, CHAT_TAG)
}
