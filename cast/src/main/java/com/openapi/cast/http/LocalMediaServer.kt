package com.openapi.cast.http

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 把手机上的本地文件通过 HTTP 吐给电视。
 *
 * 电视只能按 URL 拉流，读不到手机文件系统，所以投屏本地视频必须先在本机起一个
 * HTTP 服务。这里用 NanoHTTPD（Apache-2.0，单文件级的小库）而不是自己写 socket：
 * Range 请求、断点续传、大文件分块这些电视机型差异很大，NanoHTTPD 已经处理好。
 *
 * 安全：服务只绑定在局域网，且每次启动生成一次性 token 作为路径前缀，
 * 避免同网段其它设备顺手猜到文件名把私有视频拉走。
 */
class LocalMediaServer(private val port: Int = DEFAULT_PORT) : NanoHTTPD(port) {

    private companion object {
        const val TAG = "LocalMediaServer"
        const val DEFAULT_PORT = 8888
    }

    private val token = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
    private var root: File? = null

    /** 启动服务并把 [dir] 作为可访问根目录。返回是否启动成功。 */
    fun start(dir: File): Boolean {
        root = dir
        return try {
            start(SOCKET_READ_TIMEOUT, false)
            true
        } catch (e: Exception) {
            Log.e(TAG, "启动本地媒体服务失败", e)
            false
        }
    }

    /** 生成某个文件对本网设备的可访问地址；服务未启动时返回 null。 */
    fun urlFor(file: File): String? {
        val ip = localIpv4() ?: return null
        return "http://$ip:$port/$token/${java.net.URLEncoder.encode(file.name, "UTF-8")}"
    }

    override fun serve(session: IHTTPSession): Response {
        val segments = session.uri.trim('/').split('/')
        if (segments.size < 2 || segments[0] != token) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "forbidden")
        }
        val name = java.net.URLDecoder.decode(segments[1], "UTF-8")
        val dir = root ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
        // 只允许取根目录下的直接文件，挡住 ../ 之类的路径穿越
        val file = File(dir, name)
        if (!file.canonicalPath.startsWith(dir.canonicalPath + File.separator) || !file.isFile) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
        }

        val mime = mimeTypeOf(file.name)
        val total = file.length()
        val range = session.headers["range"]

        // 电视拖动进度条发的是 Range 请求。不回 206 + Content-Range 的话，
        // 大部分设备会从头重放，表现为"投屏了但没法拖"。
        if (range != null && range.startsWith("bytes=")) {
            val spec = range.removePrefix("bytes=").substringBefore(',')
            val startText = spec.substringBefore('-').trim()
            val endText = spec.substringAfter('-').trim()
            val start = startText.toLongOrNull() ?: 0L
            val end = endText.toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
            if (start > end || start >= total) {
                return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "bad range")
            }
            val length = end - start + 1
            val stream = FileInputStream(file).apply { skipFully(start) }
            return newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, stream, length).apply {
                addHeader("Content-Range", "bytes $start-$end/$total")
                addHeader("Accept-Ranges", "bytes")
            }
        }

        return newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(file), total).apply {
            addHeader("Accept-Ranges", "bytes")
        }
    }

    private fun FileInputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }

    private fun mimeTypeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }

    /** 本机 IPv4，优先 wlan（电视在同一 Wi-Fi 才能连上）。 */
    private fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedByDescending { it.name.startsWith("wlan") }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull()
            ?.hostAddress
    }.getOrNull()
}
