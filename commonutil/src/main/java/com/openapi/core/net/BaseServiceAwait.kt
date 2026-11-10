package com.openapi.core.net

import com.openapi.core.net.request.CommonResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/*
 * [BaseService] 的挂起包装，替代各模块里手写的 suspendCancellableCoroutine。
 *
 * 约定：在 Dispatchers.IO 上发起请求；失败（网络错误、服务端拒绝）返回 null（或 false），不抛异常。
 * 错误详情已由 CommonResponse.ResponseErrorListener 打日志，调用方只关心成功与否。
 */

/** POST 表单（可带一个对象参数），成功返回解析后的数据，失败返回 null。 */
suspend fun <T> BaseService.awaitPost(url: String, param: Any?, clazz: Class<T>, tag: String): T? =
    withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            postData(
                url,
                param,
                object : CommonResponse.ResponseOkListener<T>() {
                    override fun onResponse(response: T) {
                        if (cont.isActive) cont.resume(response)
                    }
                },
                object : CommonResponse.ResponseErrorListener() {
                    override fun onError() {
                        if (cont.isActive) cont.resume(null)
                    }
                },
                tag,
                clazz,
            )
        }
    }

/** POST 表单，只关心是否成功：接口只返回 errno、没有业务数据时用（删除类接口成功时响应体为空）。 */
suspend fun BaseService.awaitPostOk(url: String, param: Any?, tag: String): Boolean =
    withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            postData(
                url,
                param,
                object : CommonResponse.ResponseOkListener<Any>() {
                    // 响应体可能为 null，参数必须可空：否则 Kotlin 的非空检查抛出 NPE，协程永远挂起
                    override fun onResponse(response: Any?) {
                        if (cont.isActive) cont.resume(true)
                    }
                },
                object : CommonResponse.ResponseErrorListener() {
                    override fun onError() {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                tag,
                Any::class.java,
            )
        }
    }

/** 带文件的 multipart 上传，[partName] 是服务端读取文件的表单名。成功返回解析后的数据，失败返回 null。 */
suspend fun <T> BaseService.awaitUpload(
    url: String,
    param: Any?,
    files: List<File>,
    partName: String,
    clazz: Class<T>,
    tag: String,
): T? =
    withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            postData(
                url,
                param,
                files,
                object : CommonResponse.ResponseOkListener<T>() {
                    override fun onResponse(response: T) {
                        if (cont.isActive) cont.resume(response)
                    }
                },
                object : CommonResponse.ResponseErrorListener() {
                    override fun onError() {
                        if (cont.isActive) cont.resume(null)
                    }
                },
                partName,
                tag,
                clazz,
            )
        }
    }
