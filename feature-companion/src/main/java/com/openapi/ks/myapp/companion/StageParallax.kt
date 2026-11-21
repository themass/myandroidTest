package com.openapi.ks.myapp.companion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlin.math.asin
import kotlin.math.abs

// ---- 视差契约常量（测试钉住） ----

/** 死区（弧度）：约 0.8°。小于它当作手抖，输出 0——光晕不许跟着微抖走。 */
const val PARALLAX_DEAD_ZONE_RAD = 0.014f

/** 满倾（弧度）：45°（π/4）。到这里归一化打满 1.0，再多只收敛不再位移。 */
const val PARALLAX_FULL_TILT_RAD = 0.7853982f

/** 低通系数：每帧只吃 alpha 的差距，姿态噪声被压平，大动作 ~0.3s 内跟上。 */
const val PARALLAX_SMOOTH_ALPHA = 0.08f

/** 满倾时背景光晕的最大偏移（dp）。超过 24dp 就成「背景在跑」而不是视差。 */
fun parallaxMaxOffsetDp(reducedMotion: Boolean): Float = if (reducedMotion) 0f else 14f

/**
 * 单轴：死区内归零，死区外线性到满倾归一化 ±1，越界收敛。
 * 返回方向 = 手机往哪倾、视差往哪走（正=向右/向下倾，光晕同向微移）。
 */
fun parallaxAxis(tiltRad: Float): Float {
    val sign = if (tiltRad >= 0f) 1f else -1f
    val mag = abs(tiltRad)
    if (mag <= PARALLAX_DEAD_ZONE_RAD) return 0f
    return ((mag - PARALLAX_DEAD_ZONE_RAD) / (PARALLAX_FULL_TILT_RAD - PARALLAX_DEAD_ZONE_RAD))
        .coerceAtMost(1f) * sign
}

/**
 * TYPE_(GAME_)ROTATION_VECTOR 分量 → 屏幕坐标下的小倾角 (pitch, roll)，弧度。
 *
 * 旋转矢量是「当前相对开机姿态」的轴角，不是欧拉角；但通话场景里手机几乎永远
 * 接近竖直握持，小角度下 x/y 分量的反正切就是可用的俯仰/横滚（±90° 内单调）。
 * 打平面表（绕屏幕法线转）时 x/y≈0，两个角都≈0——不该产生视差，也确实不产生。
 */
fun tiltFromRotationVector(x: Float, y: Float, z: Float): Pair<Float, Float> {
    val pitch = asin(x.coerceIn(-1f, 1f))
    val roll = asin(y.coerceIn(-1f, 1f))
    return pitch to roll
}

/** 一步低通：out += (target - out) * alpha。 */
fun smoothParallax(current: Float, target: Float, alpha: Float): Float =
    current + (target - current) * alpha

/**
 * 监听手机姿态，输出平滑后的视差归一化偏移 (x, y) ∈ -1..1，只在 draw 阶段读。
 *
 * - 优先 GAME_ROTATION_VECTOR（相对持握基准、不漂），退到 ROTATION_VECTOR；
 *   两者都没有的设备返回静止 (0,0)，画面与没有这个特性时完全一致。
 * - 设备坐标系的 y 轴与屏幕向下相反，方向在这里修正成「手机往哪倾、光晕往哪走」。
 * - SENSOR_DELAY_GAME ≈ 50ms 档：够顺，也不像 FASTEST 那样白耗电。
 */
@Composable
fun rememberStageParallax(): State<Offset> {
    val context = LocalContext.current
    val offsetX = remember { mutableFloatStateOf(0f) }
    val offsetY = remember { mutableFloatStateOf(0f) }
    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sm == null || sensor == null) return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (pitch, roll) = tiltFromRotationVector(
                    event.values.getOrElse(0) { 0f },
                    event.values.getOrElse(1) { 0f },
                    event.values.getOrElse(2) { 0f },
                )
                offsetX.floatValue = smoothParallax(
                    offsetX.floatValue, parallaxAxis(-roll), PARALLAX_SMOOTH_ALPHA,
                )
                offsetY.floatValue = smoothParallax(
                    offsetY.floatValue, parallaxAxis(pitch), PARALLAX_SMOOTH_ALPHA,
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    return remember { derivedStateOf { Offset(offsetX.floatValue, offsetY.floatValue) } }
}
