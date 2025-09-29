package com.livekit.voiceassistant.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.livekit.voiceassistant.R
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.math.abs

/**
 * 音频可视化波形类型
 */
enum class KimiWaveType {
    BAR,        // 传统条形波形
    PARTICLE,   // 粒子效果波形
    CUSTOM      // 自定义波纹效果
}

/**
 * Kimi音频可视化组件 - 支持三种波形类型切换
 * 1. BAR: 传统条形波形
 * 2. PARTICLE: 粒子效果波形（参考图片设计）
 * 3. CUSTOM: 自定义波纹效果（基于MyWaveView）
 */
@Composable
fun AudioVisualizer(
    isActive: Boolean = false,
    amplitude: Float = 0f, // 0-1之间的振幅值
    waveType: KimiWaveType = KimiWaveType.BAR, // 波形类型
    modifier: Modifier = Modifier
) {
    when (waveType) {
        KimiWaveType.BAR -> {
            BarWaveVisualizer(
                isActive = isActive,
                amplitude = amplitude,
                modifier = modifier
            )
        }
        KimiWaveType.PARTICLE -> {
            ParticleWaveVisualizer(
                isActive = isActive,
                amplitude = amplitude,
                modifier = modifier
            )
        }
        KimiWaveType.CUSTOM -> {
            CustomWaveView(
                isActive = isActive,
                amplitude = amplitude,
                waveType = CustomWaveType.CIRCULAR,
                modifier = modifier
            )
        }
    }
}

/**
 * 传统条形波形可视化
 */
@Composable
private fun BarWaveVisualizer(
    isActive: Boolean = false,
    amplitude: Float = 0f,
    modifier: Modifier = Modifier
) {
    // 5个条的高度状态
    var barHeights by remember { mutableStateOf(List(5) { 0.3f }) }
    
    // 动画状态
    val infiniteTransition = rememberInfiniteTransition(label = "bar_visualizer")
    
    // 基础动画 - 持续的小幅波动
    val baseAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "base_animation"
    )
    
    // 根据振幅和活跃状态更新条的高度
    LaunchedEffect(isActive, amplitude) {
        if (isActive) {
            while (isActive) {
                val newHeights = List(5) { index ->
                    val baseHeight = 0.2f + baseAnimation * 0.3f
                    val amplitudeEffect = amplitude * 0.8f
                    val randomVariation = Random.nextFloat() * 0.2f
                    
                    // 确保高度在合理范围内
                    (baseHeight + amplitudeEffect + randomVariation).coerceIn(0.1f, 1f)
                }
                barHeights = newHeights
                delay(100) // 更新频率
            }
        } else {
            // 非活跃状态，条高度逐渐降低
            barHeights = List(5) { 0.1f + Random.nextFloat() * 0.1f }
        }
    }
    
    // 主容器 - 完全透明背景，只显示发光条
    Box(
        modifier = modifier
            .width(156.dp)
            .height(60.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            barHeights.forEachIndexed { index, height ->
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height((height * 44).dp)
                        .graphicsLayer {
                            shadowElevation = if (isActive) 12f else 4f
                        }
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Color.White.copy(alpha = if (isActive) 1f else 0.9f)
                        )
                )
            }
        }
    }
}

/**
 * 粒子效果波形可视化 - 参考图片设计
 */
@Composable
private fun ParticleWaveVisualizer(
    isActive: Boolean = false,
    amplitude: Float = 0f,
    modifier: Modifier = Modifier
) {
    // 粒子状态
    var particles by remember { mutableStateOf(List(50) { createParticle() }) }
    
    // 动画状态
    val infiniteTransition = rememberInfiniteTransition(label = "particle_visualizer")
    
    // 主时间动画 - 使用丝滑的缓动函数
    val timeAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse // 使用Reverse模式，更自然
        ),
        label = "time_animation"
    )
    
    // 次级动画 - 不同周期和缓动，增加自然感
    val secondaryAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "secondary_animation"
    )
    
    // 慢速动画 - 长周期，柔和变化
    val slowAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slow_animation"
    )
    
    // 微动画 - 细微的变化，增加生动感
    val microAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micro_animation"
    )
    
    // 更新粒子状态 - 结构化但看似随机的布局
    LaunchedEffect(isActive, amplitude, timeAnimation, secondaryAnimation, slowAnimation, microAnimation) {
        particles = List(150) { index -> // 增加粒子数量到150个
            val particle = createParticle()
            
            // 创建结构化网格位置 (15x10网格)
            val gridX = index % 15 - 7 // -7 到 7
            val gridY = index / 15 - 5 // -5 到 4
            
            // 基础位置（网格）
            val baseX = gridX * 0.12f
            val baseY = gridY * 0.15f
            
            // 多层波浪扰动 - 使用四层动画创建丝滑效果
            val wave1 = sin(baseX * 3f + timeAnimation * PI.toFloat() * 2f) * 0.15f
            val wave2 = cos(baseY * 2.5f + secondaryAnimation * PI.toFloat() * 1.2f) * 0.12f
            val wave3 = sin((baseX + baseY) * 1.8f + slowAnimation * PI.toFloat() * 0.8f) * 0.08f
            val microWave = cos(baseX * 6f + microAnimation * PI.toFloat() * 3f) * 0.05f
            
            // 振幅响应 - 根据距离中心的位置产生不同的响应
            val distanceFromCenter = sqrt(baseX * baseX + baseY * baseY)
            val amplitudeResponse = if (isActive && amplitude > 0.01f) {
                val centerInfluence = (1f - (distanceFromCenter / 1.5f).coerceIn(0f, 1f))
                val amplitudeWave = sin(distanceFromCenter * 3f - timeAnimation * PI.toFloat() * 4f)
                amplitude * amplitudeWave * centerInfluence * 0.6f
            } else {
                0f
            }
            
            // 涟漪效果 - 从中心向外扩散，使用组合动画
            val rippleEffect = if (isActive && amplitude > 0.01f) {
                val ripplePhase1 = timeAnimation * PI.toFloat() * 3f - distanceFromCenter * 6f
                val ripplePhase2 = secondaryAnimation * PI.toFloat() * 2f - distanceFromCenter * 4f
                val ripple1 = sin(ripplePhase1) * amplitude * 0.2f
                val ripple2 = cos(ripplePhase2) * amplitude * 0.1f
                (ripple1 + ripple2) * (1f - distanceFromCenter / 2f).coerceAtLeast(0f)
            } else {
                // 等待状态的柔和波动
                val gentleRipple = sin(slowAnimation * PI.toFloat() - distanceFromCenter * 2f) * 0.05f
                gentleRipple * (1f - distanceFromCenter / 3f).coerceAtLeast(0f)
            }
            
            // 最终位置计算 - 包含四层波浪效果
            val finalX = baseX + wave1 + wave3 + microWave
            val finalY = baseY + wave2 + amplitudeResponse + rippleEffect
            
            // 粒子属性计算
            val centerInfluence = (1f - (distanceFromCenter / 2f).coerceIn(0f, 1f))
            val densityVariation = sin(baseX * 5f) * cos(baseY * 5f) // 密度变化
            
            particle.copy(
                x = finalX,
                y = finalY,
                alpha = if (isActive && amplitude > 0.01f) {
                    // 活跃时：增强可见性，更亮的中心
                    (0.5f + amplitude * 0.8f + centerInfluence * 0.6f + densityVariation * 0.3f).coerceIn(0.3f, 1f)
                } else {
                    // 等待时：增强可见性，更明显的等待状态
                    (0.4f + centerInfluence * 0.7f + densityVariation * 0.2f).coerceIn(0.2f, 0.8f)
                },
                size = if (isActive && amplitude > 0.01f) {
                    // 活跃时：更大的粒子，更明显的效果
                    (1.2f + amplitude * 3.5f + centerInfluence * 1.8f + abs(densityVariation) * 1.2f).coerceIn(0.5f, 6f)
                } else {
                    // 等待时：更大的等待状态粒子
                    (1.2f + centerInfluence * 1.2f + abs(densityVariation) * 0.6f).coerceIn(0.5f, 3f)
                }
            )
        }
    }
    
    // 主容器 - 更大的显示区域，支持复杂粒子布局
    Box(
        modifier = modifier
            .width(280.dp) // 增大宽度
            .height(120.dp), // 增大高度
        contentAlignment = Alignment.Center
    ) {
        // 粒子渲染区域
        Box(
            modifier = Modifier
                .width(260.dp)
                .height(100.dp)
        ) {
            particles.forEach { particle ->
                // 计算屏幕坐标
                val screenX = (particle.x * 130f + 130f).coerceIn(0f, 260f)
                val screenY = (particle.y * 50f + 50f).coerceIn(0f, 100f)
                
                Box(
                    modifier = Modifier
                        .offset(
                            x = screenX.dp,
                            y = screenY.dp
                        )
                        .size(particle.size.dp)
                        .graphicsLayer {
                            alpha = particle.alpha
                            // 动态发光效果
                            shadowElevation = particle.alpha * 10f + if (isActive && amplitude > 0.01f) 8f else 2f
                        }
                        .background(
                            color = if (isActive && amplitude > 0.01f) {
                                // 活跃时：更亮的青色
                                Color(0xFF00D4FF).copy(alpha = particle.alpha)
                            } else {
                                // 等待时：柔和的蓝色
                                Color(0xFF4FC3F7).copy(alpha = particle.alpha)
                            },
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

/**
 * 粒子数据类
 */
private data class Particle(
    val x: Float = 0f,
    val y: Float = 0f,
    val alpha: Float = 0.5f,
    val size: Float = 2f
)

/**
 * 创建随机粒子
 */
private fun createParticle(): Particle {
    return Particle(
        x = Random.nextFloat() * 2f - 1f,
        y = Random.nextFloat() * 2f - 1f,
        alpha = Random.nextFloat() * 0.5f + 0.3f,
        size = Random.nextFloat() * 3f + 1f
    )
}

/**
 * 增强版音频可视化组件 - 半透明设计，不影响消息阅读
 * 支持实时振幅输入，默认等待态动画，支持波形类型切换
 */
@Composable
fun KimiRealTimeAudioVisualizer(
    amplitude: Float = 0f, // 实时振幅 0-1
    isActive: Boolean = false,
    waveType: KimiWaveType = KimiWaveType.BAR, // 波形类型
    modifier: Modifier = Modifier
) {
    // 根据波形类型选择不同的可视化组件
    when (waveType) {
        KimiWaveType.BAR -> {
            BarWaveVisualizer(
                isActive = isActive,
                amplitude = amplitude,
                modifier = modifier
            )
        }
        KimiWaveType.PARTICLE -> {
            ParticleWaveVisualizer(
                isActive = isActive,
                amplitude = amplitude,
                modifier = modifier
            )
        }
        KimiWaveType.CUSTOM -> {
            CustomWaveView(
                isActive = isActive,
                amplitude = amplitude,
                waveType = CustomWaveType.CIRCULAR,
                modifier = modifier
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AudioVisualizerPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Kimi音频可视化组件",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        
        // 条形波形预览
        Text(
            text = "条形波形 (BAR)",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        
        // 静态条形预览
        AudioVisualizer(
            isActive = false,
            amplitude = 0f,
            waveType = KimiWaveType.BAR
        )
        
        // 活跃状态条形预览
        AudioVisualizer(
            isActive = true,
            amplitude = 0.5f,
            waveType = KimiWaveType.BAR
        )
        
        // 粒子波形预览
        Text(
            text = "粒子波形 (PARTICLE)",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        
        // 静态粒子预览
        AudioVisualizer(
            isActive = false,
            amplitude = 0f,
            waveType = KimiWaveType.PARTICLE
        )
        
        // 活跃状态粒子预览
        AudioVisualizer(
            isActive = true,
            amplitude = 0.8f,
            waveType = KimiWaveType.PARTICLE
        )
    }
}
