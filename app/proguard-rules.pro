# ==============================================
# 1. 基础混淆配置（R8 兼容版）
# ==============================================
# 代码混淆压缩比（R8 已自动优化，保留参数无需调整）
-optimizationpasses 5
# 避免类名大小写混合（防止系统类加载冲突）
-dontusemixedcaseclassnames
# 不忽略非公共库类（保留第三方库核心类）
-dontskipnonpubliclibraryclasses
# 保留混淆映射文件（便于崩溃日志反混淆）
-verbose
# 不跳过非公共库的类成员
-dontskipnonpubliclibraryclassmembers
# Android 无需 preverify，加速混淆流程
-dontpreverify
# 保留关键元数据（避免注解、泛型、行号信息丢失）
-keepattributes *Annotation*,InnerClasses,Signature,SourceFile,LineNumberTable
# 混淆后保留源文件标识（便于定位崩溃行号）
-renamesourcefileattribute SourceFile
# R8 兼容的优化算法（移除 ProGuard 专属优化项，避免冲突）
-optimizations !code/simplification/cast,!field/*,!class/merging/*
# 临时禁用 R8 全量优化（规避并发修改异常，稳定后可删除）
#-android.enableR8.fullMode=false


# ==============================================
# 2. Android 核心组件与系统类（去重+AndroidX 优先）
# ==============================================
# 保留四大组件、Application 等关键系统类
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# 保留 AndroidX 核心类（覆盖生命周期、页面、绑定组件）
-keep class androidx.** { *; }
-dontwarn androidx.**
# 保留 AndroidX ViewModel/LiveData（避免数据观察逻辑崩溃）
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.LiveData { *; }
# 保留 AndroidX 权限回调方法（Fragment/ComponentActivity）
-keep class * extends androidx.fragment.app.Fragment {
    public void onRequestPermissionsResult(int, java.lang.String[], int[]);
}
-keep class * extends androidx.activity.ComponentActivity {
    public void onRequestPermissionsResult(int, java.lang.String[], int[]);
}
# 保留 ViewBinding（避免布局绑定类被混淆）
-keep public class * implements androidx.viewbinding.ViewBinding { *; }

# 保留 Parcelable/Serializable 序列化类（避免跨页面传值崩溃）
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保留 R 资源类（避免资源引用找不到）
-keep class **.R$* { *; }

# 保留 View 子类与构造方法（自定义控件不被混淆）
-keep public class * extends android.view.View {
    *** get*();
    void set*(***);
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
# 保留 Activity 中 View 点击方法（layout 中定义的 onClick）
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

# 保留枚举类（避免 values()/valueOf() 方法丢失）
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留 native 方法（避免 JNI 调用崩溃）
-keepclassmembers class * {
    native <methods>;
}


# ==============================================
# 3. 第三方库混淆规则（精准匹配+警告抑制）
# ==============================================
# ---------------------- 网络/IO 相关 ----------------------
# OkHttp/Okio（保留域名解析类，抑制无关警告）
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Retrofit（保留核心请求类）
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# ---------------------- 多媒体相关 ----------------------
# GSYVideoPlayer + IJKPlayer（核心播放类不混淆）
# 保留 GSYVideoPlayer 的 video 子包（视频核心逻辑）
-keep class com.shuyu.gsyvideoplayer.video.** { *; }
# 保留 GSYVideoPlayer 的 utils 子包（工具类，如格式转换、屏幕适配）
-keep class com.shuyu.gsyvideoplayer.utils.** { *; }
# 保留 GSYVideoPlayer 的 player 子包（播放器核心，如 IJKPlayer 封装）
-keep class com.shuyu.gsyvideoplayer.player.** { *; }
-keep class com.shuyu.aliplay.** { *; }
-dontwarn com.shuyu.gsyvideoplayer.**
-keep class tv.danmaku.ijk.media.player.** { *; }
-dontwarn tv.danmaku.ijk.**
-keep class com.alivc.**{*;}
-keep class com.aliyun.**{*;}
-keep class com.cicada.**{*;}
-dontwarn com.alivc.**
-dontwarn com.aliyun.**
-dontwarn com.cicada.**
# Glide（图片加载库，保留模块配置类）
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-dontwarn com.bumptech.glide.**

# ---------------------- 事件/数据库相关 ----------------------
# EventBus（保留订阅方法与线程模式）
-keepattributes *Annotation*
-keepclassmembers class ** {
}

# 不使用 SQLCipher 时抑制加密库警告
-dontwarn net.sqlcipher.database.**

# ---------------------- UI/工具相关 ----------------------

# PictureSelector + UCrop（图片选择与裁剪，保留核心类）
-keep class com.luck.picture.lib.** { *; }
-keep class com.luck.lib.camerax.** { *; }
-keep class com.yalantis.ucrop** { *; }
-dontwarn com.yalantis.ucrop**

# jaudiotagger（本地音乐标签库）引用了桌面 JVM 才有的 API，Android 上不存在也不会被调用
-dontwarn java.awt.image.BufferedImage
-dontwarn javax.imageio.ImageIO
-dontwarn javax.imageio.stream.ImageInputStream

# ---------------------- 广告/SDK 相关 ----------------------
# 广点通（GDT 广告，保留核心交互类）
-keep class com.qq.e.** { *; }
-keep class com.tencent.lbsapi.** { *; }
-dontwarn com.qq.e.**

# 火山引擎语音（保留语音引擎实现类）
-keep class com.bytedance.speech.speechengine.SpeechEngineImpl { *; }

# ChatWaifu + Sherpa（AI 相关，保留 JNI 交互类）
-keep class com.chatwaifu.** { *; }
-keep class com.k2fsa.sherpa.ncnn.** { *; }
-keep class com.chatwaifu.lipsync.LipSyncJNI$* { *; }

# ==============================================
# 4. 项目业务类（精准保留，避免业务逻辑崩溃）
# ==============================================
# 项目自己的类由 proguard-common.pro 的 -keep class com.openapi.** 统一保留

# 保留 ping 相关业务类
-keep class com.ping.** { *; }



# ==============================================
# 5. 辅助配置（日志移除+冗余警告抑制）
# ==============================================
# Release 版本移除日志（减少 APK 体积，避免敏感信息泄露）
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# 抑制非必要警告（避免构建日志冗余，不影响功能）
-dontwarn javax.annotation.**
-dontwarn sun.**
-dontwarn java.beans.**
-dontwarn org.xmlpull.v1.**
-dontwarn org.kxml2.io.**
-dontwarn rx.** # 不使用 RxJava 时抑制
-dontwarn com.squareup.picasso.** # 不使用 Picasso 时抑制
