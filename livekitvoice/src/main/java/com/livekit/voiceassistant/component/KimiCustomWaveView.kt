package com.livekit.voiceassistant.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.livekit.voiceassistant.R
import kotlinx.coroutines.delay
import kotlin.math.*

/**
 * 自定义波纹类型
 */
enum class KimiCustomWaveType {
    CIRCULAR,    // 圆形波纹
    LINEAR,      // 线性波纹
    PARTICLE     // 粒子波纹
}

/**
 * Kimi自定义波纹组件 - 使用原始AXWaveAnimation库
 * 恢复原始的UI效果，保持与之前版本的一致性
 */
@Composable
fun KimiCustomWaveView(
    isActive: Boolean = false,
    amplitude: Float = 0f, // 0-1之间的振幅值
    waveType: KimiCustomWaveType = KimiCustomWaveType.CIRCULAR,
    waveColor: Color = colorResource(id = R.color.kimi_primary),
    rippleColor: Color = colorResource(id = R.color.kimi_secondary),
    modifier: Modifier = Modifier
) {
    // 使用原始的MyWaveView组件
    AndroidView(
        factory = { context ->
            com.livekit.voiceassistant.wave.MyWaveView(context)
        },
        update = { view ->
            // 根据waveType设置不同的显示模式
            when (waveType) {
                KimiCustomWaveType.CIRCULAR -> {
                    // 显示圆形波纹
                    view.setWaveType(true, false)
                }
                KimiCustomWaveType.LINEAR -> {
                    // 显示线性波纹
                    view.setWaveType(false, true)
                }
                KimiCustomWaveType.PARTICLE -> {
                    // 显示粒子波纹（使用圆形波纹作为基础）
                    view.setWaveType(true, false)
                }
            }
            
            // 更新振幅
            view.setAmplitude(amplitude)
            
            // 更新活跃状态
            if (isActive) {
                view.startWaveAnimation()
            } else {
                view.stopWaveAnimation()
            }
        },
        modifier = modifier
    )
}

/**
 * 圆形波纹视图
 */
@Composable
private fun CircularWaveView(
    isActive: Boolean = false,
    amplitude: Float = 0f,
    waveColor: Color,
    rippleColor: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    // 波纹状态
    var waves by remember { mutableStateOf(List(3) { WaveData(phase = it * 0.3f, radius = 0f) }) }
    var ripples by remember { mutableStateOf<List<RippleData>>(emptyList()) }
    
    // 动画状态
    val infiniteTransition = rememberInfiniteTransition(label = "circular_wave")
    
    val waveAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_animation"
    )
    
    val rippleAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_animation"
    )
    
    // 更新波纹状态
    LaunchedEffect(isActive, amplitude, waveAnimation, rippleAnimation) {
        if (isActive) {
            val maxRadius = with(density) { 200.dp.toPx() }
            
            // 更新波纹
            waves = waves.mapIndexed { index, wave ->
                val waveProgress = (waveAnimation + wave.phase) % 1f
                val radius = waveProgress * maxRadius * (1f + amplitude * 0.5f)
                wave.copy(radius = radius)
            }
            
            // 更新涟漪
            ripples = ripples.map { ripple ->
                val newRadius = ripple.radius + 2f * (0.5f + amplitude * 1.5f)
                val alpha = (1f - (newRadius / 200f)).coerceAtLeast(0f)
                ripple.copy(radius = newRadius, alpha = alpha)
            }.filter { it.alpha > 0 && it.radius <= 200f }
            
            // 根据振幅创建新涟漪
            if (amplitude > 0.3f && ripples.size < 5) {
                val newRipple = RippleData(
                    x = (Math.random() - 0.5).toFloat() * 100f,
                    y = (Math.random() - 0.5).toFloat() * 100f,
                    radius = 0f,
                    alpha = 1f
                )
                ripples = ripples + newRipple
            }
            
            delay(16) // 60fps
        } else {
            // 停止时清空波纹
            waves = waves.map { it.copy(radius = 0f) }
            ripples = emptyList()
        }
    }
    
    Canvas(
        modifier = modifier
            .size(400.dp)
            .background(Color.Transparent)
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        
        // 绘制波纹
        waves.forEach { wave ->
            if (wave.radius > 0) {
                val alpha = (1f - (wave.radius / (size.minDimension / 2f))).coerceIn(0f, 1f)
                drawCircle(
                    color = waveColor.copy(alpha = alpha),
                    radius = wave.radius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        
        // 绘制涟漪
        ripples.forEach { ripple ->
            if (ripple.radius > 0 && ripple.alpha > 0) {
                drawCircle(
                    color = rippleColor.copy(alpha = ripple.alpha),
                    radius = ripple.radius,
                    center = Offset(centerX + ripple.x, centerY + ripple.y)
                )
            }
        }
    }
}

/**
 * 线性波纹视图
 */
@Composable
private fun LinearWaveView(
    isActive: Boolean = false,
    amplitude: Float = 0f,
    waveColor: Color,
    rippleColor: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    // 线性波纹状态
    var waveOffset by remember { mutableStateOf(0f) }
    var waveAmplitude by remember { mutableStateOf(0f) }
    
    // 动画状态
    val infiniteTransition = rememberInfiniteTransition(label = "linear_wave")
    
    val waveAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "linear_wave_animation"
    )
    
    // 更新线性波纹状态
    LaunchedEffect(isActive, amplitude, waveAnimation) {
        if (isActive) {
            waveOffset = waveAnimation * 2f * PI.toFloat()
            waveAmplitude = 20f + amplitude * 60f
        } else {
            waveOffset = 0f
            waveAmplitude = 0f
        }
    }
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(Color.Transparent)
    ) {
        val centerY = size.height / 2f
        val path = Path()
        
        // 绘制正弦波
        for (x in 0 until size.width.toInt() step 2) {
            val y = centerY + sin((x * 0.02f) + waveOffset) * waveAmplitude
            if (x == 0) {
                path.moveTo(x.toFloat(), y)
            } else {
                path.lineTo(x.toFloat(), y)
            }
        }
        
        drawPath(
            path = path,
            color = waveColor,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

/**
 * 粒子波纹视图
 */
@Composable
private fun ParticleWaveView(
    isActive: Boolean = false,
    amplitude: Float = 0f,
    waveColor: Color,
    rippleColor: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    // 粒子状态
    var particles by remember { mutableStateOf<List<ParticleData>>(emptyList()) }
    
    // 动画状态
    val infiniteTransition = rememberInfiniteTransition(label = "particle_wave")
    
    val timeAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "time_animation"
    )
    
    val secondaryAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "secondary_animation"
    )
    
    // 更新粒子状态
    LaunchedEffect(isActive, amplitude, timeAnimation, secondaryAnimation) {
        if (isActive) {
            particles = List(100) { index ->
                val gridX = index % 10 - 5
                val gridY = index / 10 - 5
                val baseX = gridX * 0.15f
                val baseY = gridY * 0.15f
                
                // 多层波浪扰动
                val wave1 = sin(baseX * 3f + timeAnimation * PI.toFloat() * 2f) * 0.2f
                val wave2 = cos(baseY * 2.5f + secondaryAnimation * PI.toFloat() * 1.2f) * 0.15f
                
                val distanceFromCenter = sqrt(baseX * baseX + baseY * baseY)
                val amplitudeResponse = if (amplitude > 0.01f) {
                    sin(distanceFromCenter * 4f + timeAnimation * PI.toFloat() * 3f) * amplitude * 0.3f
                } else 0f
                
                val finalX = baseX + wave1
                val finalY = baseY + wave2 + amplitudeResponse
                
                val centerInfluence = (1f - (distanceFromCenter / 2f).coerceIn(0f, 1f))
                val alpha = (0.3f + amplitude * 0.7f) * centerInfluence
                val size = (2f + amplitude * 3f) * centerInfluence
                
                ParticleData(
                    x = finalX,
                    y = finalY,
                    size = size,
                    alpha = alpha
                )
            }
        } else {
            particles = emptyList()
        }
    }
    
    Canvas(
        modifier = modifier
            .size(300.dp)
            .background(Color.Transparent)
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        
        // 绘制粒子
        particles.forEach { particle ->
            val x = centerX + particle.x * 100f
            val y = centerY + particle.y * 100f
            
            drawCircle(
                color = waveColor.copy(alpha = particle.alpha),
                radius = particle.size,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * 波纹数据类
 */
private data class WaveData(
    val phase: Float,
    val radius: Float
)

/**
 * 涟漪数据类
 */
private data class RippleData(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float
)

/**
 * 粒子数据类
 */
private data class ParticleData(
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float
)

// ============ Preview Functions ============

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E)
@Composable
fun KimiCustomWaveViewPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A0B2E))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Text(
            text = "自定义波纹组件",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        // 圆形波纹预览
        KimiCustomWaveView(
            isActive = true,
            amplitude = 0.7f,
            waveType = KimiCustomWaveType.CIRCULAR,
            modifier = Modifier.size(200.dp)
        )
        
        // 线性波纹预览
        KimiCustomWaveView(
            isActive = true,
            amplitude = 0.5f,
            waveType = KimiCustomWaveType.LINEAR,
            modifier = Modifier.height(100.dp)
        )
        
        // 粒子波纹预览
        KimiCustomWaveView(
            isActive = true,
            amplitude = 0.8f,
            waveType = KimiCustomWaveType.PARTICLE,
            modifier = Modifier.size(200.dp)
        )
    }
}
