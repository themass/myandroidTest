package com.openapi.cast.ssdp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/**
 * SSDP 发现（UPnP 设备发现的传输层）。
 *
 * 协议很小，没必要为它引一个通用 UPnP 栈：向 239.255.255.250:1900 发一条
 * `M-SEARCH`，把一段时间内收到的 `HTTP/1.1 200 OK` 响应收集起来即可。
 *
 * 三条 ST 都要发：`MediaRenderer:1` 是标准的媒体渲染器类型，但不少电视只响应
 * `ssdp:all`；少数只认 `AVTransport:1`。漏掉任一都会出现"某些电视搜不到"。
 */
object SsdpClient {

    private const val TAG = "SsdpClient"
    private const val SSDP_ADDR = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val MX_SECONDS = 3

    private val searchTargets = listOf(
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        "urn:schemas-upnp-org:service:AVTransport:1",
        "ssdp:all",
    )

    /**
     * 搜索局域网内的设备。
     *
     * @param timeoutMs 总共监听多久；至少要有 MX_SECONDS 秒，否则设备还没来得及响应就收工了
     * @return location → 响应头（含 `ST`/`USN`），由上层再 GET 描述文档拿控制地址
     */
    suspend fun search(timeoutMs: Int = 4000): Map<String, Map<String, String>> =
        withContext(Dispatchers.IO) {
            val found = ConcurrentHashMap<String, Map<String, String>>()
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 300

                val group = InetAddress.getByName(SSDP_ADDR)
                val request = buildSearchRequest()
                val payload = request.toByteArray(Charsets.UTF_8)
                searchTargets.forEach {
                    runCatching {
                        socket.send(DatagramPacket(payload, payload.size, InetSocketAddress(group, SSDP_PORT)))
                    }
                }

                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(4096)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        continue
                    } catch (e: Exception) {
                        Log.w(TAG, "接收失败", e)
                        break
                    }
                    val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val headers = parseHeaders(response)
                    val location = headers["location"] ?: continue
                    found[location] = headers
                }
            }
            found
        }

    private fun buildSearchRequest(): String = buildString {
        append("M-SEARCH * HTTP/1.1\r\n")
        append("HOST: $SSDP_ADDR:$SSDP_PORT\r\n")
        append("MAN: \"ssdp:discover\"\r\n")
        append("MX: $MX_SECONDS\r\n")
        append("ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n")
        append("\r\n")
    }

    /** 解析响应头，键统一小写（不同设备的头大小写不一致）。 */
    fun parseHeaders(response: String): Map<String, String> =
        response.lineSequence()
            .drop(1) // 状态行
            .filter { it.contains(':') }
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                val key = line.substring(0, idx).trim().lowercase()
                val value = line.substring(idx + 1).trim()
                if (key.isEmpty()) null else key to value
            }
            .toMap()
}
