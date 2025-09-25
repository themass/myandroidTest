package com.livekit.voiceassistant.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livekit.voiceassistant.R
import com.livekit.voiceassistant.component.*
import kotlinx.coroutines.delay

/**
 * Kimi波纹效果演示页面
 * 展示所有可用的波纹效果类型
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KimiWaveDemoScreen(
    onBack: () -> Unit
) {
    // 状态管理
    var isActive by remember { mutableStateOf(true) }
    var amplitude by remember { mutableStateOf(0.5f) }
    var selectedWaveType by remember { mutableStateOf(KimiWaveType.CUSTOM) }
    var selectedCustomType by remember { mutableStateOf(KimiCustomWaveType.CIRCULAR) }
    
    // 自动振幅变化
    LaunchedEffect(Unit) {
        while (true) {
            amplitude = (amplitude + 0.1f) % 1f
            delay(200)
        }
    }
    
    // Kimi主题
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = colorResource(id = R.color.kimi_primary),
            secondary = colorResource(id = R.color.kimi_secondary),
            surface = colorResource(id = R.color.kimi_surface),
            background = colorResource(id = R.color.kimi_background)
        )
    ) {
        // 深紫色渐变背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colorResource(id = R.color.kimi_bg_top),
                            colorResource(id = R.color.kimi_bg_middle),
                            colorResource(id = R.color.kimi_bg_bottom)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 顶部标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = colorResource(id = R.color.kimi_text_primary)
                        )
                    }
                    
                    Text(
                        text = "自定义波纹效果演示",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(id = R.color.kimi_text_primary),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                
                // 控制面板
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colorResource(id = R.color.kimi_white_alpha_10)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "控制面板",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorResource(id = R.color.kimi_text_primary),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        // 活跃状态开关
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "活跃状态:",
                                color = colorResource(id = R.color.kimi_text_secondary),
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isActive,
                                onCheckedChange = { isActive = it }
                            )
                        }
                        
                        // 振幅滑块
                        Text(
                            text = "振幅: ${String.format("%.2f", amplitude)}",
                            color = colorResource(id = R.color.kimi_text_secondary),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Slider(
                            value = amplitude,
                            onValueChange = { amplitude = it },
                            valueRange = 0f..1f,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // 波形类型选择
                        Text(
                            text = "波形类型:",
                            color = colorResource(id = R.color.kimi_text_secondary),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KimiWaveType.values().forEach { type ->
                                FilterChip(
                                    onClick = { selectedWaveType = type },
                                    label = { Text(type.name) },
                                    selected = selectedWaveType == type,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        
                        // 自定义波纹类型选择
                        if (selectedWaveType == KimiWaveType.CUSTOM) {
                            Text(
                                text = "自定义波纹类型:",
                                color = colorResource(id = R.color.kimi_text_secondary),
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                KimiCustomWaveType.values().forEach { type ->
                                    FilterChip(
                                        onClick = { selectedCustomType = type },
                                        label = { Text(type.name) },
                                        selected = selectedCustomType == type,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 波纹效果展示区域
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = colorResource(id = R.color.kimi_white_alpha_05)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (selectedWaveType) {
                            KimiWaveType.BAR -> {
                                KimiAudioVisualizer(
                                    isActive = isActive,
                                    amplitude = amplitude,
                                    waveType = KimiWaveType.BAR,
                                    modifier = Modifier.size(200.dp)
                                )
                            }
                            KimiWaveType.PARTICLE -> {
                                KimiAudioVisualizer(
                                    isActive = isActive,
                                    amplitude = amplitude,
                                    waveType = KimiWaveType.PARTICLE,
                                    modifier = Modifier.size(200.dp)
                                )
                            }
                            KimiWaveType.CUSTOM -> {
                                KimiCustomWaveView(
                                    isActive = isActive,
                                    amplitude = amplitude,
                                    waveType = selectedCustomType,
                                    modifier = Modifier.size(300.dp)
                                )
                            }
                        }
                    }
                }
                
                // 底部信息
                Text(
                    text = "当前振幅: ${String.format("%.2f", amplitude)} | 状态: ${if (isActive) "活跃" else "静止"}",
                    color = colorResource(id = R.color.kimi_text_hint),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

// ============ Preview Functions ============

@Preview(showBackground = true, backgroundColor = 0xFF1A0B2E, name = "波纹效果演示")
@Composable
fun KimiWaveDemoScreenPreview() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = colorResource(id = R.color.kimi_primary),
            secondary = colorResource(id = R.color.kimi_secondary),
            surface = colorResource(id = R.color.kimi_surface),
            background = colorResource(id = R.color.kimi_background)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colorResource(id = R.color.kimi_bg_top),
                            colorResource(id = R.color.kimi_bg_middle),
                            colorResource(id = R.color.kimi_bg_bottom)
                        )
                    )
                )
        ) {
            // 预览用的简化版本
            KimiWaveDemoScreenPreviewContent()
        }
    }
}

@Composable
private fun KimiWaveDemoScreenPreviewContent() {
    // 状态管理
    var isActive by remember { mutableStateOf(true) }
    var amplitude by remember { mutableStateOf(0.7f) }
    var selectedWaveType by remember { mutableStateOf(KimiWaveType.CUSTOM) }
    var selectedCustomType by remember { mutableStateOf(KimiCustomWaveType.CIRCULAR) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = colorResource(id = R.color.kimi_text_primary)
                )
            }
            
            Text(
                text = "自定义波纹效果演示",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(id = R.color.kimi_text_primary),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        // 控制面板
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorResource(id = R.color.kimi_white_alpha_10)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "控制面板",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(id = R.color.kimi_text_primary),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // 活跃状态开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "活跃状态:",
                        color = colorResource(id = R.color.kimi_text_secondary),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it }
                    )
                }
                
                // 振幅滑块
                Text(
                    text = "振幅: ${String.format("%.2f", amplitude)}",
                    color = colorResource(id = R.color.kimi_text_secondary),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Slider(
                    value = amplitude,
                    onValueChange = { amplitude = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 波形类型选择
                Text(
                    text = "波形类型:",
                    color = colorResource(id = R.color.kimi_text_secondary),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KimiWaveType.values().forEach { type ->
                        FilterChip(
                            onClick = { selectedWaveType = type },
                            label = { Text(type.name) },
                            selected = selectedWaveType == type,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // 自定义波纹类型选择
                if (selectedWaveType == KimiWaveType.CUSTOM) {
                    Text(
                        text = "自定义波纹类型:",
                        color = colorResource(id = R.color.kimi_text_secondary),
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KimiCustomWaveType.values().forEach { type ->
                            FilterChip(
                                onClick = { selectedCustomType = type },
                                label = { Text(type.name) },
                                selected = selectedCustomType == type,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        
        // 波纹效果展示区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = colorResource(id = R.color.kimi_white_alpha_05)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when (selectedWaveType) {
                    KimiWaveType.BAR -> {
                        KimiAudioVisualizer(
                            isActive = isActive,
                            amplitude = amplitude,
                            waveType = KimiWaveType.BAR,
                            modifier = Modifier.size(200.dp)
                        )
                    }
                    KimiWaveType.PARTICLE -> {
                        KimiAudioVisualizer(
                            isActive = isActive,
                            amplitude = amplitude,
                            waveType = KimiWaveType.PARTICLE,
                            modifier = Modifier.size(200.dp)
                        )
                    }
                    KimiWaveType.CUSTOM -> {
                        KimiCustomWaveView(
                            isActive = isActive,
                            amplitude = amplitude,
                            waveType = selectedCustomType,
                            modifier = Modifier.size(300.dp)
                        )
                    }
                }
            }
        }
        
        // 底部信息
        Text(
            text = "当前振幅: ${String.format("%.2f", amplitude)} | 状态: ${if (isActive) "活跃" else "静止"}",
            color = colorResource(id = R.color.kimi_text_hint),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}
