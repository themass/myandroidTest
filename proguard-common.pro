# ==============================================

# 统一 ProGuard 规则文件 (优化版)
# ==============================================

# ==============================================
# 1. 基础混淆配置 (R8 优化)
# ==============================================
# R8 自动优化，无需手动配置 optimizationpasses
# -optimizationpasses 5  # R8 自动优化
# 避免类名大小写混合
-dontusemixedcaseclassnames
# 不忽略非公共库类
-dontskipnonpubliclibraryclasses
# 保留混淆映射文件
-verbose
# 不跳过非公共库的类成员
-dontskipnonpubliclibraryclassmembers
# Android 无需 preverify
-dontpreverify
# 保留关键元数据 (优化)
-keepattributes *Annotation*,InnerClasses,Signature,SourceFile,LineNumberTable,EnclosingMethod
# 混淆后保留源文件标识
-renamesourcefileattribute SourceFile
# R8 兼容的优化算法 (优化)
-optimizations !code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable

# ==============================================
# 2. Android 核心组件
# ==============================================
# 保留四大组件
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# 保留 AndroidX 核心类
-keep class androidx.** { *; }
-dontwarn androidx.**

# 保留 View 相关
-keep public class * extends android.view.View
-keep public class * extends android.view.ViewGroup
-keep public class * extends android.widget.*

# ==============================================
# 3. 第三方库保留规则
# ==============================================
# Gson
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# ExoPlayer (androidx.media3)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# RxJava 3
-keep class io.reactivex.rxjava3.** { *; }
-dontwarn io.reactivex.rxjava3.**

# 现代权限管理

# EventBus
-keepattributes *Annotation*
-keepclassmembers class * {
}


# ==============================================
# 4. 项目业务类保留规则
# ==============================================
# 保留项目核心包
-keep class com.openapi.ks.** { *; }
-keep class com.openapi.core.** { *; }
-keep class chat.** { *; }

# 保留数据模型
-keep class * implements java.io.Serializable
-keep class * implements android.os.Parcelable

# 保留枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==============================================
# 5. 反射相关保留
# ==============================================
# 保留通过反射调用的方法
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保留 Native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# ==============================================
# 6. 日志移除
# ==============================================
# Release 版本移除日志
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# ==============================================
# 7. 警告抑制
# ==============================================
# 抑制第三方库警告
-dontwarn com.google.**
-dontwarn com.squareup.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn com.bumptech.**
# SIP/VoIP (javax.sip / gov.nist) 可选 log4j 桥接，Android 无 log4j
-dontwarn org.apache.log4j.**
-dontwarn android.gov.nist.**

-keep class com.openapi.common.** { *; }
