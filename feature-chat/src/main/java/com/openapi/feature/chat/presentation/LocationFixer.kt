package com.openapi.feature.chat.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 取一次「够用的」坐标就收手。抽成接口的原因与 [VideoMetaSource] 一样：
 * `LocationManager` 在 JVM 单测里不存在，而「拿不到定位要提示什么、拿到怎么写进草稿」
 * 这段流程得能在测试里跑。
 */
internal interface LocationFixer {
    /** 单次定位结果。[accuracyMeters] 拿不到时为 null——宁可少标也不编一个精度。 */
    data class Fix(val latitude: Double, val longitude: Double, val accuracyMeters: Int?)

    /**
     * 取一次位置。[timeoutMillis] 内拿不到就返回 null（调用方提示重试）。
     * 必须在有 `ACCESS_COARSE_LOCATION` 授权后调用；本实现不申请权限。
     */
    suspend fun once(timeoutMillis: Long): Fix?
}

/**
 * 真机实现：`LocationManager` 的**网络定位**单次回调（P2-13）。
 *
 * 为什么不用 FusedLocationProviderClient：`play-services-location` 不在这个工程里，
 * 而拉一个新依赖只为拿一个经纬度，代价（包体、GMS 依赖、构建配置）远大于收益。
 * 网络定位对「城市街区级」够用，与只申请 COARSE 的设计一致。
 *
 * 顺序：先尝鲜 `getLastKnownLocation`（上一次别的 App 或本 App 定位过就有现成的，秒回），
 * 没有再请求一次单次更新。收尾（removeUpdates）放在 finally：超时、成功、取消三条路
 * 都必须注销，否则回调注册一直挂在系统里。
 */
internal class LocationManagerFixer(private val context: Context) : LocationFixer {

    @SuppressLint("MissingPermission") // 权限由调用方在 PermissionGateway 之后保证；这里重复检查只会掩盖调用顺序错误
    override suspend fun once(timeoutMillis: Long): LocationFixer.Fix? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val enabled = manager.allProviders.filter {
            runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
        }
        if (enabled.isEmpty()) return null
        // 上一次定位留下的结果：取精度最好的一条，精度标注照实带（界面会显示「±N m」）
        val cached = enabled.asSequence()
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .minByOrNull { it.accuracy }
        if (cached != null) return cached.toFix()

        // 网络优先（室内、粗精度够用），其次 GPS；两个都注册没有意义，一个结果就收手
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { it in enabled } ?: enabled.first()

        val result = CompletableDeferred<LocationFixer.Fix?>()
        val handedOff = AtomicBoolean(false)
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                if (handedOff.compareAndSet(false, true)) result.complete(location.toFix())
            }

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("API 28 及以下仍会回调旧签名")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        }
        val registered = runCatching {
            // 主线程 Looper：回调在主线程派发，removeUpdates 也在主线程侧，无线程竞态
            manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        }.isSuccess
        if (!registered) return null
        try {
            return withTimeoutOrNull(timeoutMillis) { result.await() }
        } finally {
            runCatching { manager.removeUpdates(listener) }
        }
    }

    private fun Location.toFix() = LocationFixer.Fix(
        latitude = latitude,
        longitude = longitude,
        // 精度为负表示定位方没给（部分实现），当作未知
        accuracyMeters = if (accuracy.isNaN() || accuracy < 0f) null else accuracy.toInt(),
    )
}
