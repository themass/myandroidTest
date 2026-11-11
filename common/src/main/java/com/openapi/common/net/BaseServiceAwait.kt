package com.openapi.common.net

import com.openapi.common.net.http.BaseService
import com.openapi.common.net.http.HttpClient
import com.openapi.common.net.http.NetError
import com.openapi.common.net.http.Response

import com.openapi.common.net.request.CommonResponse
import com.openapi.common.net.request.GsonRequest
import com.openapi.common.net.request.InfoListVo
import com.openapi.common.util.log.LogUtil
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

/**
 * 带文件的 multipart 上传，**失败时抛出** [NetError]（网络、超时、服务端 errno 都在里面）。
 *
 * 和 [awaitUpload] 的区别：那个返回 null 并把 toast 弹在 `HttpClient.showVolleyError` 里，
 * 只适合「失败了也没什么可说的」的上传。需要按服务端 errno 分支处理的接口（如拍照解题的
 * 40002「不是题目」）必须用这个，否则所有业务错误都会被压成一句通用提示。
 *
 * 注意：失败时不会自动弹 toast，调用方负责提示文案。
 */
suspend fun <T> BaseService.awaitUploadOrThrow(
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
                    override fun onErrorResponse(error: NetError) {
                        LogUtil.e("upload failed: $url", error)
                        if (cont.isActive) cont.resumeWithException(error)
                    }
                },
                partName,
                tag,
                clazz,
            )
        }
    }

/*
 * GET 请求的挂起版本。失败时抛出 ExecutionException（原因是 NetError），与原来的同步方法一致，
 * 调用方原本的 try/catch 不用改。错误不弹 toast（原同步方法也不弹）。
 */

/** GET 分页列表（InfoListVo 结构）。 */
suspend fun <T> BaseService.awaitInfoList(url: String, clazz: Class<T>, tag: String): InfoListVo<T> =
    withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val request = GsonRequest.infoList(
                context,
                url,
                clazz,
                null,
                object : Response.Listener<InfoListVo<T>> {
                    override fun onResponse(response: InfoListVo<T>) {
                        if (cont.isActive) cont.resume(response)
                    }
                },
                object : Response.ErrorListener {
                    override fun onErrorResponse(error: NetError) {
                        if (cont.isActive) cont.resumeWithException(ExecutionException(error))
                    }
                },
            )
            request.setTag(tag)
            HttpClient.addRequest(request)
        }
    }

/** GET 单个对象。 */
suspend fun <T> BaseService.awaitGet(url: String, clazz: Class<T>, tag: String): T =
    withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val request = GsonRequest(
                context,
                url,
                clazz,
                null,
                object : Response.Listener<T> {
                    override fun onResponse(response: T) {
                        if (cont.isActive) cont.resume(response)
                    }
                },
                object : Response.ErrorListener {
                    override fun onErrorResponse(error: NetError) {
                        if (cont.isActive) cont.resumeWithException(ExecutionException(error))
                    }
                },
            )
            request.setTag(tag)
            HttpClient.addRequest(request)
        }
    }
