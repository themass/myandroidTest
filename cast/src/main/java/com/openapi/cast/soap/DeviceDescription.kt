package com.openapi.cast.soap

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 设备描述文档解析。
 *
 * SSDP 只告诉我们"设备在这个 URL 有描述"，真正的控制地址藏在描述文档的
 * `<serviceList>` 里：要找 `AVTransport` 服务，取它的 `<controlURL>`。
 * 这一步不能省——不同品牌的 controlURL 路径完全不一样，没有任何规律可循。
 */
object DeviceDescription {

    private const val TAG = "DeviceDescription"

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    data class Parsed(
        val friendlyName: String,
        val modelName: String?,
        val manufacturer: String?,
        val controlUrl: String,
    )

    /**
     * @param location SSDP 响应里的 LOCATION 头
     * @param baseUrl 取 LOCATION 的 scheme+host+port，用于把相对 controlURL 拼成绝对地址
     */
    fun fetch(location: String): Parsed? {
        val xml = runCatching {
            client.newCall(Request.Builder().url(location).build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string().orEmpty()
            }
        }.onFailure { Log.w(TAG, "取描述失败 $location", it) }.getOrNull() ?: return null

        return runCatching { parse(xml, location) }
            .onFailure { Log.w(TAG, "解析描述失败 $location", it) }
            .getOrNull()
    }

    private fun parse(xml: String, location: String): Parsed? {
        // 关掉外部实体解析：描述文档来自局域网里的任意设备，属于不可信输入
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val doc = factory.newDocumentBuilder()
            .parse(xml.byteInputStream(Charsets.UTF_8))
        doc.documentElement.normalize()

        val base = java.net.URL(location)

        fun text(tag: String): String? =
            doc.getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

        if (doc.getElementsByTagName("device").length == 0) return null
        val services = doc.getElementsByTagName("service")
        var controlUrl: String? = null
        for (i in 0 until services.length) {
            val service = services.item(i)
            val type = service.childNodes.let { nodes ->
                (0 until nodes.length)
                    .map { nodes.item(it) }
                    .firstOrNull { it.nodeName == "serviceType" }
                    ?.textContent
            } ?: continue
            if (!type.contains("AVTransport", ignoreCase = true)) continue
            controlUrl = service.childNodes.let { nodes ->
                (0 until nodes.length)
                    .map { nodes.item(it) }
                    .firstOrNull { it.nodeName == "controlURL" }
                    ?.textContent
                    ?.trim()
            }
            if (!controlUrl.isNullOrEmpty()) break
        }

        val resolvedControl = controlUrl?.let { resolve(base, it) } ?: return null

        return Parsed(
            friendlyName = text("friendlyName").orEmpty(),
            modelName = text("modelName"),
            manufacturer = text("manufacturer"),
            controlUrl = resolvedControl,
        )
    }

    /** controlURL 可能是相对路径（`/upnp/control/...`）也可能是绝对 URL。 */
    private fun resolve(base: java.net.URL, controlUrl: String): String = runCatching {
        java.net.URL(base, controlUrl).toString()
    }.getOrDefault(controlUrl)
}
