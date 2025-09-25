# Kimi 语音助手 UI 完整指南

## 🎯 项目概述

这是一个基于 LiveKit 的语音助手应用，采用 Kimi 风格的深紫色渐变设计，支持音频同步的视觉效果。

## 📱 主 Fragment 说明

### 🎯 **Fragment 架构说明**

#### **两个并列的 Fragment：**

##### 1. **MainFragment.kt** - 完整版
```kotlin
// 文件位置: livekitvoice/src/main/java/com/livekit/voiceassistant/MainFragment.kt
// 作用: 完整功能的 UI 容器，包含导航逻辑
// 特点: 使用 NavHost 管理多个页面，功能完整
```

##### 2. **KimiFragment.kt** - 精简版 ⭐ 推荐
```kotlin
// 文件位置: livekitvoice/src/main/java/com/livekit/voiceassistant/KimiFragment.kt
// 作用: 精简版的 Kimi 风格 UI，单页面
// 特点: 直接使用 KimiSimpleConnectScreen，界面简洁
```

#### **Fragment 使用方式：**

##### **📱 FragmentManager 是多版本切换入口** ✅
```kotlin
// 1. 显示 MainFragment (完整版，推荐)
LiveKitFragmentManager.showMainFragment(
    activity = this,
    containerId = R.id.fragment_container,
    url = "wss://your-server.com",  // LiveKit 服务器 URL
    token = "your_jwt_token",       // LiveKit JWT Token
    addToBackStack = true
)

// 2. 显示 KimiFragment (精简版)  
LiveKitFragmentManager.showKimiFragment(
    activity = this,
    containerId = R.id.fragment_container,
    url = "wss://your-server.com",  // LiveKit 服务器 URL
    token = "your_jwt_token",       // LiveKit JWT Token
    addToBackStack = true
)

// 3. 兼容原来的引用方式
fragment = LiveKitFragmentManager.createMainFragment(
    getIntent().getStringExtra("URL"),    // 原来的方式
    getIntent().getStringExtra("TOKEN")   // 原来的方式
)
```

##### **🔗 URL 和 TOKEN 参数说明** ⭐ 重要
- **URL**: LiveKit 服务器的 WebSocket 地址 (例: `wss://your-server.livekit.cloud`)
- **TOKEN**: LiveKit JWT Token，包含房间权限和用户信息
- **默认值**: 如果不传参数，会使用内置的测试服务器和 Token
- **房间连接**: 这两个参数是连接 LiveKit 房间的必需参数

## 🏗️ 项目架构

### 📁 **库模块结构 (livekitvoice)**
```
livekitvoice/src/main/java/com/livekit/voiceassistant/
├── 🧩 Fragment 层 (库的主要接口)
│   ├── KimiFragment.kt                   # 精简版 Fragment ⭐ 推荐
│   ├── MainFragment.kt                   # 完整版 Fragment
│   └── FragmentManager.kt                # Fragment 管理器
└── 🎨 Screen 层 (Compose UI)
    ├── KimiSimpleConnectScreen.kt        # 精简连接界面 ⭐
    ├── KimiStyleConnectScreen.kt         # 完整连接界面  
    ├── KimiStyleVoiceAssistantScreen.kt  # 语音助手界面
    └── 🧱 Components (UI 组件)
        ├── AudioSyncEffects.kt           # 音频同步效果 ⭐
        ├── KimiUnifiedInput.kt           # 统一输入框
        ├── KimiControlButton.kt          # 控制按钮
        └── KimiVideoComponent.kt         # 视频组件
```

### 🎯 **库模块的使用方式**
```kotlin
// 在宿主应用中使用 (推荐)
LiveKitFragmentManager.showKimiFragment(activity, containerId)

// 或者使用完整版
LiveKitFragmentManager.showMainFragment(activity, containerId)
```

## 🎨 UI 设计系统

### 🌈 **Kimi 颜色系统**
```xml
<!-- 主色调 -->
<color name="kimi_primary">#6A4C93</color>        <!-- 深紫色 -->
<color name="kimi_secondary">#8E44AD</color>       <!-- 亮紫色 -->
<color name="kimi_accent">#4A2C5A</color>          <!-- 辅助紫色 -->

<!-- 功能色彩 -->
<color name="kimi_button_danger">#E74C3C</color>   <!-- 红色-用户说话/危险操作 -->
<color name="kimi_status_connected">#2ECC71</color> <!-- 绿色-连接成功 -->

<!-- 背景渐变 -->
<color name="kimi_bg_top">#2C1810</color>          <!-- 深棕色 -->
<color name="kimi_bg_middle">#1A0B2E</color>       <!-- 深紫色 -->
<color name="kimi_bg_bottom">#0F0511</color>       <!-- 深黑色 -->
```

### 📏 **尺寸系统**
```xml
<!-- 字体大小 -->
<dimen name="kimi_text_size_title">40sp</dimen>    <!-- Kimi 标题 -->
<dimen name="kimi_text_size_lg">16sp</dimen>       <!-- 状态文字 -->
<dimen name="kimi_text_size_md">14sp</dimen>       <!-- 普通文字 -->

<!-- 组件尺寸 -->
<dimen name="kimi_avatar_size_md">80dp</dimen>     <!-- AI 头像 -->
<dimen name="kimi_button_height_lg">56dp</dimen>   <!-- 主按钮高度 -->

<!-- 间距系统 -->
<dimen name="kimi_padding_sm">8dp</dimen>          <!-- 小间距 -->
<dimen name="kimi_padding_md">16dp</dimen>         <!-- 中间距 -->
<dimen name="kimi_padding_lg">24dp</dimen>         <!-- 大间距 -->
```

## 🎵 音频同步效果

### 🎯 **AudioWaveformCalculator 最佳使用位置**

#### **⭐⭐⭐ 强烈推荐**
1. **主头像音频同步** (`AudioSyncEffects.kt`)
   ```kotlin
   // 替换模拟数据，用真实音频驱动波纹效果
   AudioWaveformCalculator(audioTrackRef, barCount=1, listener)
   ```

2. **语音条实时数据** (`AudioSyncEffects.kt`)
   ```kotlin
   // 8个语音条对应8个频段的实时数据
   AudioWaveformCalculator(audioTrackRef, barCount=8, listener)
   ```

3. **自动状态检测** (`KimiSimpleConnectScreen.kt`)
   ```kotlin
   // 自动检测用户/AI说话状态，无需手动切换
   AudioWaveformCalculator(userTrack, barCount=1, userListener)
   AudioWaveformCalculator(aiTrack, barCount=1, aiListener)
   ```

#### **⭐⭐ 可选使用**
4. **波形可视化** - 显示连续的音频波形
5. **录音音量指示** - 录音时的音量反馈

### 🎭 **音频同步视觉效果**
| 状态 | 头像效果 | 图标 | 颜色 | 语音条 | 文字提示 |
|------|----------|------|------|--------|----------|
| 用户说话 | 🌊 红色波纹扩散 | 🎤 麦克风 | 红色 | 红色快跳 | "正在听取您的语音..." |
| AI说话 | ✨ 紫色光晕跳动 | 🔊 音量 | 紫色 | 紫色节拍 | "AI正在思考回应..." |
| 对话中 | 📞 轻微缩放 | 📞 电话 | 白色 | 半透明 | "对话进行中" |
| 静默 | 💨 呼吸效果 | 🎤 麦克风 | 白色 | 隐藏 | "内容由AI生成" |

## 🔧 UI 修改快速指南

### 📝 **想修改什么** → **编辑哪个文件**

| 修改目标 | 主要文件 | 预览功能 | 说明 |
|----------|----------|----------|------|
| **整体界面布局** | `KimiSimpleConnectScreen.kt` | ✅ 3个预览 | 连接界面的主要布局 |
| **音频视觉效果** | `AudioSyncEffects.kt` | ✅ 6个预览 | 头像动画、语音条、波形 |
| **输入框样式** | `KimiUnifiedInput.kt` | ✅ 3个预览 | 文字/语音输入切换 |
| **按钮样式** | `KimiControlButton.kt` | ✅ 3个预览 | 暂停、聊天等控制按钮 |
| **颜色主题** | `res/values/colors.xml` | ❌ | 整个应用的颜色系统 |
| **尺寸间距** | `res/values/dimens.xml` | ❌ | 所有组件的大小和间距 |
| **文字内容** | `res/values/strings.xml` | ❌ | 所有显示的文字 |

### 🎯 **常见修改场景**

#### **修改主色调**
```xml
<!-- colors.xml -->
<color name="kimi_primary">#YOUR_NEW_COLOR</color>
```
影响：主按钮、AI说话效果、语音条颜色

#### **调整头像大小**
```xml
<!-- dimens.xml -->
<dimen name="kimi_avatar_size_md">100dp</dimen>
```
影响：中央AI头像的大小

#### **修改动画速度**
```kotlin
// AudioSyncEffects.kt
animation = tween(500) // 改为更快的动画
```
影响：波纹、跳动、语音条的动画速度

## 📱 预览系统

### 🖼️ **所有可预览的组件**
- **KimiSimpleConnectScreen**: 3个状态预览（默认、对话中、用户说话）
- **AudioSyncEffects**: 6个效果预览（头像3种状态 + 语音条2种 + 波形1种）
- **KimiUnifiedInput**: 3个模式预览（文字、语音、录音中）
- **KimiControlButton**: 3个按钮预览（暂停、聊天、组合）

### 🎯 **使用 Preview 的方法**
1. 打开对应的 `.kt` 文件
2. 点击右侧 "Preview" 选项卡
3. 选择想要预览的状态
4. 实时编辑代码查看效果

## 🚀 开发工作流

### 📝 **标准修改流程**
1. **确定修改目标** - 明确要改什么
2. **选择对应文件** - 根据上表选择文件
3. **使用 Preview 预览** - 实时查看效果
4. **编译测试** - `./gradlew :livekitvoice:compileDebugKotlin`
5. **真机验证** - 在设备上测试最终效果

### 🎯 **推荐的开发顺序**
1. **先改资源文件** (colors.xml, dimens.xml, strings.xml)
2. **再改 Compose 代码** (布局、动画、交互)
3. **最后集成音频** (AudioWaveformCalculator)

## 🎵 音频集成建议

### 🔧 **集成 AudioWaveformCalculator 的步骤**

#### **步骤1: 在主界面添加音频轨道参数**
```kotlin
@Composable
fun KimiSimpleConnectScreen(
    userAudioTrack: TrackReference? = null,  // 添加用户音频轨道
    aiAudioTrack: TrackReference? = null,    // 添加AI音频轨道
    // ... 其他参数
)
```

#### **步骤2: 集成音频检测**
```kotlin
// 用户音频检测
if (userAudioTrack != null) {
    AudioWaveformCalculator(
        audioTrackRef = userAudioTrack,
        barCount = 1,
        listener = object : AudioSizeChangeListener {
            override fun onAudioSizeChanged(amplitude: Float) {
                isUserSpeaking = amplitude > 50f
            }
        }
    )
}
```

#### **步骤3: 替换模拟动画**
```kotlin
// 移除模拟的 LaunchedEffect，使用真实音频数据
// 删除这部分代码：
// LaunchedEffect(isConversationActive) { ... }
```

## 📋 快速参考

### 🎯 **核心组件关系**
```
KimiLauncherActivity (启动器)
└── KimiSimpleConnectScreen (主界面) ⭐
    ├── AudioSyncAvatarIcon (音频同步头像)
    ├── AudioSyncVoiceBars (语音可视化条)
    └── KimiUnifiedInput (统一输入框)
```

### 🎨 **样式修改快捷方式**
- **颜色**: `colors.xml` → 全局生效
- **尺寸**: `dimens.xml` → 全局生效  
- **文字**: `strings.xml` → 全局生效
- **布局**: 对应的 `.kt` 文件 → 局部生效
- **动画**: `AudioSyncEffects.kt` → 音频相关动画

### 📱 **预览使用**
- 每个 UI 组件都有多个状态的 Preview
- 在 Android Studio 中打开 `.kt` 文件即可预览
- 支持实时编辑和效果查看

## ✅ MainFragment 版本优化总结

### 📋 **您的历史UI要求 vs 实际优化状态：**

| 要求 | 状态 | 实现方式 |
|------|------|----------|
| ✅ "元素布局和排版更合理一些" | 已优化 | 使用 `SpaceEvenly` 均匀分布，增加 `kimi_section_spacing_large` |
| ✅ "颜色写到 values文件中" | 已完成 | 所有颜色都在 `colors.xml` 中，使用 `colorResource()` |
| ✅ "位置不对,放到中间,半透明,不要遮挡文字" | 已修复 | 状态指示器居中，使用 `kimi_white_alpha_10` 半透明背景 |
| ✅ "布局太紧凑了" | 已修复 | 改用 `kimi_padding_xl` 和 `kimi_section_spacing_large` |
| ✅ "开始对话和结束使用一个按钮" | 已完成 | 主按钮动态切换文字和颜色 |
| ✅ "状态不够明显" | 已优化 | 20dp 状态圆点，18sp 字体，脉冲动画 |
| ✅ "不展示信息按钮,重复了" | 已修复 | 移除了重复的控制按钮，只保留核心功能 |
| ✅ "精简一些，优化一下" | 已完成 | 语音可视化仅在活跃时显示，减少视觉干扰 |

### 🎯 **当前 MainFragment 使用的界面：**
```
MainFragment.kt 
└── KimiStyleConnectScreen (已按要求优化) ⭐
    ├── 顶部：居中半透明状态指示器
    ├── 中央：AI头像 + 音频同步效果
    ├── 语音：智能显示（仅活跃时）
    └── 底部：统一主按钮 + 输入框
```

## 🚀 完整使用说明

### 📱 **在宿主应用中使用 MainFragment**

#### **方法1: 使用 FragmentManager（推荐）**
```kotlin
// 在您的 Activity 中
class YourActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.your_activity) // 需要包含 fragment_container
        
        // 显示 MainFragment
        LiveKitFragmentManager.showMainFragment(
            activity = this,
            containerId = R.id.fragment_container,
            addToBackStack = false
        )
    }
}
```

#### **方法2: 带参数使用**
```kotlin
// 带 URL 和 Token 参数
LiveKitFragmentManager.showMainFragment(
    activity = this,
    containerId = R.id.fragment_container,
    url = "wss://your-livekit-server.com",
    token = "your_jwt_token",
    addToBackStack = true
)
```

#### **方法3: 直接创建 Fragment**
```kotlin
// 直接使用 Fragment
val mainFragment = MainFragment.newInstance()
supportFragmentManager.beginTransaction()
    .replace(R.id.fragment_container, mainFragment)
    .commit()

// 或者带参数
val mainFragment = MainFragment.newInstance(url, token)
supportFragmentManager.beginTransaction()
    .replace(R.id.fragment_container, mainFragment)
    .commit()
```

### 📋 **必需的布局文件**
```xml
<!-- 在宿主应用的 Activity 布局中 -->
<FrameLayout
    android:id="@+id/fragment_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### 🎨 **必需的依赖**
```kotlin
// 在宿主应用的 build.gradle 中添加
dependencies {
    implementation project(':livekitvoice')
    
    // Compose 相关依赖
    implementation platform('androidx.compose:compose-bom:2025.09.00')
    implementation 'androidx.activity:activity-compose:1.10.1'
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.navigation:navigation-compose:2.9.4'
    
    // Fragment 支持
    implementation 'androidx.fragment:fragment-ktx:1.8.6'
}
```

### 🎯 **MainFragment 的优化特性：**

#### **1. 布局优化** ✅
- **更宽松的间距** - 使用 `kimi_padding_xl` 和 `kimi_section_spacing_large`
- **合理的排版** - `SpaceEvenly` 均匀分布
- **居中半透明状态** - 状态指示器居中，半透明背景，不遮挡文字

#### **2. 视觉优化** ✅  
- **状态更明显** - 20dp 圆点，18sp 字体，脉冲动画
- **颜色资源化** - 所有颜色都在 `colors.xml` 中
- **统一按钮** - 开始/结束使用同一个按钮，动态切换

#### **3. 交互优化** ✅
- **移除重复按钮** - 不再有重复的信息按钮
- **智能显示** - 语音可视化仅在活跃时显示
- **音频同步** - 用户说话红色波纹，AI说话紫色光晕

### 📱 **完整使用示例**

#### **标准使用方式（完全兼容原来的引用）**
```java
// Java 版本 - 完全兼容原来的方式
public class YourActivity extends FragmentActivity {
    private static final String URL = "URL";
    private static final String TOKEN = "TOKEN";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        if (savedInstanceState == null) {
            // 原来: MainFragment.Companion.newInstance(...)
            // 现在: LiveKitFragmentManager.Companion.newInstance(...)
            Fragment fragment = LiveKitFragmentManager.Companion.newInstance(
                getIntent().getStringExtra(URL), 
                getIntent().getStringExtra(TOKEN)
            );
            
            getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment, fragment)  // 注意：使用您布局中实际的 ID
                .commitAllowingStateLoss();
        }
    }
}
```

```kotlin
// Kotlin 版本
class YourActivity : FragmentActivity() {
    companion object {
        const val URL = "URL"
        const val TOKEN = "TOKEN"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        if (savedInstanceState == null) {
            val fragment = LiveKitFragmentManager.newInstance(
                intent.getStringExtra(URL),
                intent.getStringExtra(TOKEN)
            )
            
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment, fragment)
                .commitAllowingStateLoss()
        }
    }
}
```

#### **示例2: 使用新的 FragmentManager 方式**
```kotlin
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 获取 Intent 参数
        val url = intent.getStringExtra("URL")
        val token = intent.getStringExtra("TOKEN")
        
        if (savedInstanceState == null) {
            if (url != null && token != null) {
                // 使用传入的参数
                LiveKitFragmentManager.showMainFragment(
                    activity = this,
                    containerId = R.id.fragment_container,
                    url = url,
                    token = token,
                    addToBackStack = false
                )
            } else {
                // 使用默认参数
                LiveKitFragmentManager.showMainFragment(
                    activity = this,
                    containerId = R.id.fragment_container,
                    addToBackStack = false
                )
            }
        }
    }
    
    // 运行时切换版本
    fun switchToKimiVersion() {
        LiveKitFragmentManager.showKimiFragment(
            activity = this,
            containerId = R.id.fragment_container,
            addToBackStack = true
        )
    }
}
```

#### **示例3: 多版本切换控制**
```kotlin
class FragmentSwitchActivity : FragmentActivity() {
    
    fun showMainFragment(url: String, token: String) {
        LiveKitFragmentManager.showMainFragment(this, R.id.fragment_container, url, token)
    }
    
    fun showKimiFragment(url: String, token: String) {
        LiveKitFragmentManager.showKimiFragment(this, R.id.fragment_container, url, token)
    }
    
    fun showOldFragment(url: String, token: String) {
        LiveKitFragmentManager.showOldFragment(this, R.id.fragment_container, url, token)
    }
    
    // 检查当前显示的版本
    fun getCurrentFragmentType(): String {
        return when {
            LiveKitFragmentManager.isShowingNewStyle(this) -> "MainFragment"
            LiveKitFragmentManager.isShowingKimiFragment(this) -> "KimiFragment"
            else -> "OldFragment"
        }
    }
}
```

### 🔗 **URL 和 TOKEN 参数的重要性**

#### **LiveKit 房间连接必需参数：**
- **URL**: `wss://your-server.livekit.cloud` - LiveKit 服务器地址
- **TOKEN**: JWT Token - 包含房间名、用户权限、过期时间等
- **作用**: 这两个参数决定了连接到哪个 LiveKit 房间进行语音通话

#### **参数传递流程：**
```
宿主应用 Activity
├── Intent 传入 URL + TOKEN
├── 通过 FragmentManager 传递给 Fragment  
├── Fragment 将参数传递给 ConnectScreen
└── ConnectScreen 使用参数连接 LiveKit 房间
```

#### **如果参数缺失：**
- ✅ **有默认值** - 使用内置的测试服务器和 Token
- ✅ **仍可连接** - 可以连接到测试房间进行功能验证
- ⚠️ **生产环境** - 建议使用您自己的 LiveKit 服务器参数

现在的 MainFragment 版本已经完全按照您的历史要求优化，并且保持了与原来引用方式的兼容性！
