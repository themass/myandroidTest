package com.openapi.core.cast.soap

import android.util.Log
import com.openapi.core.cast.DlnaDevice
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * AVTransport 控制（UPnP 的播放控制动作）。
 *
 * 只实现投屏真正需要的几个：设源、播放、暂停、停止、定位、查进度。
 * 每个动作就是一条 SOAP POST，body 结构固定，没有需要抽象的地方——
 * 因此这里直接拼 XML，而不是引 SOAP 库。
 */
class AvTransport(private val device: DlnaDevice) {

    private companion object {
        const val TAG = "AvTransport"
        const val SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1"
        const val MAX_RETRIES = 2
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val xmlType = "text/xml; charset=\"utf-8\"".toMediaType()

    /** 让电视去拉 [url]（本地文件时是手机上的 HTTP 服务地址）。 */
    fun setUri(url: String, title: String? = null): Result<Unit> = call(
        "SetAVTransportURI",
        """<InstanceID>0</InstanceID>
           <CurrentURI>${escape(url)}</CurrentURI>
           <CurrentURIMetaData>${escape(metadata(url, title))}</CurrentURIMetaData>""",
    )

    fun play(): Result<Unit> = call("Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")

    fun pause(): Result<Unit> = call("Pause", "<InstanceID>0</InstanceID>")

    fun stop(): Result<Unit> = call("Stop", "<InstanceID>0</InstanceID>")

    fun seekTo(seconds: Int): Result<Unit> = call(
        "Seek",
        "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>${formatTime(seconds)}</Target>",
    )

    /** @return (已播放秒数, 总时长秒数)；取不到时返回 null。 */
    fun position(): Pair<Int, Int>? {
        val body = callRaw(
            "GetPositionInfo",
            "<InstanceID>0</InstanceID>",
        ) ?: return null
        val position = extract(body, "RelTime")?.let(::parseTime) ?: return null
        val duration = extract(body, "TrackDuration")?.let(::parseTime) ?: 0
        return position to duration
    }

    private fun call(action: String, argsXml: String): Result<Unit> =
        if (callRaw(action, argsXml) != null) Result.success(Unit)
        else Result.failure(IllegalStateException("$action 失败：${device.friendlyName}"))

    private fun callRaw(action: String, argsXml: String): String? {
        val body = soapEnvelope(action, argsXml)
        var lastError: Exception? = null
        repeat(MAX_RETRIES + 1) { attempt ->
            runCatching {
                val request = Request.Builder()
                    .url(device.controlUrl)
                    .header("SOAPACTION", "\"$SERVICE_TYPE#$action\"")
                    .header("Content-Type", "text/xml; charset=\"utf-8\"")
                    .post(body.toRequestBody(xmlType))
                    .build()
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) return text
                    lastError = IllegalStateException("HTTP ${resp.code}")
                }
            }.onFailure { lastError = it as? Exception }
            if (attempt < MAX_RETRIES) Thread.sleep(200L * (attempt + 1))
        }
        Log.w(TAG, "$action 重试后仍失败", lastError)
        return null
    }

    private fun soapEnvelope(action: String, argsXml: String): String =
        """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:$action xmlns:u="$SERVICE_TYPE">
      $argsXml
    </u:$action>
  </s:Body>
</s:Envelope>"""

    private fun metadata(url: String, title: String?): String = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
  <item id="0" parentID="-1" restricted="1">
    <dc:title>${escape(title ?: "video")}</dc:title>
    <upnp:class>object.item.videoItem</upnp:class>
    <res protocolInfo="http-get:*:video/*:*">${escape(url)}</res>
  </item>
</DIDL-Lite>"""

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun extract(xml: String, tag: String): String? =
        Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.getOrNull(1)?.trim()

    private fun parseTime(time: String): Int? {
        val parts = time.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return null
        return parts[0] * 3600 + parts[1] * 60 + parts[2]
    }

    private fun formatTime(seconds: Int): String = "%02d:%02d:%02d".format(
        seconds / 3600,
        (seconds % 3600) / 60,
        seconds % 60,
    )
}
