# 对于一些基本指令的添加
#
#############################################
# 代码混淆压缩比，在0~7之间，默认为5，一般不做修改
-optimizationpasses 5
# 混合时不使用大小写混合，混合后的类名为小写
-dontusemixedcaseclassnames
# ---------------------------------------------------------------
# DEX 结构加固：把没有被 -keep 的类全部塞进同一个默认包。
#
# 不加这两条时，R8 只改类名不改包名，反编译后看到的是
#   com.openapi.yewu.net.request.a
#   com.openapi.myapp.data.b
# 包名本身就把"这是网络层 / 这是数据层"标好了，找入口几乎不用费劲。
# 加上之后变成 a.a、a.b、a.c，一屏几百个同层类名，静态分析成本上一个台阶。
#
# 代价：任何依赖包路径的反射都会失效。本项目里没有按包名反射的代码，
# 但每加一条 -repackageclasses 之后必须真机回归一遍，不能只看编译通过。
-repackageclasses ''
-allowaccessmodification
# ---------------------------------------------------------------
# 指定不去忽略非公共库的类
-dontskipnonpubliclibraryclasses
# 这句话能够使我们的项目混淆后产生映射文件
# 包含有类名->混淆后类名的映射关系
-verbose
# 指定不去忽略非公共库的类成员
-dontskipnonpubliclibraryclassmembers
# 不做预校验，preverify是proguard的四个步骤之一，Android不需要preverify，去掉这一步能够加快混淆速度。
-dontpreverify
# 保留Annotation不混淆
-keepattributes *Annotation*,InnerClasses
#-dontoptimize
# 避免混淆泛型
-keepattributes Signature
# 抛出异常时保留代码行号
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# 指定混淆是采用的算法，后面的参数是一个过滤器
# 这个过滤器是谷歌推荐的算法，一般不做更改
-optimizations !code/simplification/cast,!field/*,!class/merging/*
#############################################
#
# Android开发中一些需要保留的公共部分
#
#############################################

# 保留我们使用的四大组件，自定义的Application等等这些类不被混淆
# 因为这些子类都有可能被外部调用
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View
# 保留R下面的资源
-keep class **.R$* {*;}
# 保留在Activity中的方法参数是view的方法，
# 这样以来我们在layout中写的onClick就不会被影响
-keepclassmembers class * extends android.app.Activity{
    public void *(android.view.View);
}

# 保留枚举类不被混淆
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留我们自定义控件（继承自View）不被混淆
-keep public class * extends android.view.View{
    *** get*();
    void set*(***);
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
# 保留Parcelable序列化类不被混淆
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
 #保留Serializable序列化的类不被混淆
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 对于带有回调函数的onXXEvent、**On*Listener的，不能被混淆
-keepclassmembers class * {
    void *(**On*Event);
    void *(**On*Listener);
}
-keepattributes EnclosingMethod
#-----------------------jar----------------------------------
-keep class android.webkit.JavascriptInterface
-dontwarn android.webkit.JavascriptInterface
#butterknife
-keep class butterknife.** { *; }
-dontwarn butterknife.internal.**
-keep class **$$ViewBinder { *; }
-dontwarn butterknife.Views$InjectViewProcessor
-keepclasseswithmembernames class * {
    @butterknife.* <fields>;
}
-keepclasseswithmembernames class * {
    @butterknife.* <methods>;
}
# okio
-dontwarn okio.**
-keep class okio.** {*;}
#-dontnote okhttp3.internal.**
-dontwarn okhttp3.**
-dontwarn java.nio.file.Files
-dontwarn java.nio.file.Path
-dontwarn java.nio.file.OpenOption
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
#apache
-keep class org.apache.http.**{ *; }
-dontwarn org.apache.http.**
-dontnote org.apache.http.**
-dontnote android.net.**
-dontnote org.apache.commons.**
-dontwarn javax.annotation.**
-keep class javax.**{ *; }
-dontnote javax.**
-dontwarn sun.**
-dontwarn java.beans.**
-keepattributes JavascriptInterface
#google
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-dontnote com.google.android.gms.**
#location
-keep class android.location.Location.** { *; }
-dontwarn android.location.Location
-dontnote android.location.Location
#--------------------------------------------------------
#eventBus
-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
-keepclassmembers class * extends org.greenrobot.eventbus.util.ThrowableFailureEvent {
    <init>(java.lang.Throwable);
}
#glid
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

-keep class jp.co.cyberagent.android.gpuimage.**{*;}
-dontnote jp.co.cyberagent.android.gpuimage.**
-dontwarn jp.co.cyberagent.android.gpuimage.**

# for DexGuard only
#-keepresourcexmlelements manifest/application/meta-data@value=GlideModule
#-dontnote com.bumptech.glide.**
-keep class androidx.core.app.NotificationCompat**{ *; }

-keepclassmembers class * {
    native <methods>;
}
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclasseswithmembers class * {
    native <methods>;
}

#----------------------project------------
# bean 包（Gson 反序列化的数据模型）。
#
# 这里恢复成完整 keep。尝试过收窄到 allowobfuscation 让类名也被改写，
# 但真机验证失败两次：
#   1. 只保字段 → R8 把没有直接引用的 bean 优化成抽象类，
#      Gson 抛 "Abstract classes can't be instantiated"
#   2. 补上构造函数 → 仍有 bean 反序列化后整体为 null，
#      首页节点列表报 "Attempt to read from field ... on a null object reference"
# 这几类 bean 用了泛型 + 字段初始化器（InfoListVo.voList = new ArrayList<>()），
# Gson 走 Unsafe 创建实例时这些初始化都不执行，字段名又参与反射匹配，
# R8 的重命名/裁剪组合很容易踩到边界。加固的收益不值得冒线上数据解析失败的风险。
#
# 代价：这几十个类的名字会保留在包里，API 的数据结构仍能被看出来。
# 其余 200+ 个类（网络层、业务逻辑、UI）依然会被重打包和重命名。
-keep class com.openapi.myapp.bean.**{ *; }
# 但 Serializable 的类必须连类名一起保留：序列化流里写的是类名，
# 名字变了，升级前存下的 Bundle 状态就反序列化不回来。
-keepnames class com.openapi.myapp.bean.vo.LocationVo
-keepnames class com.openapi.myapp.bean.vo.VipLocationVo
-keepnames class com.openapi.myapp.bean.vo.RecommendVo
-keepnames class com.openapi.myapp.bean.form.CustomeAddForm

-keep class org.strongswan.android.logic.**{ *; }
#-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
#-----------   log   ------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
}

#-------------------greenrobot greendao------------------------
-keep class org.greenrobot.greendao.**{*;}
-keepclassmembers class * extends org.greenrobot.greendao.AbstractDao {
public static java.lang.String TABLENAME;
}
# greenDAO 的 DaoConfig.reflectProperties() 是这么找配置类的：
#   Class.forName(getClass().getName() + "$Properties")
# 也就是说 Dao 类名和它的 $Properties 必须保持同一个前缀。
# 开了 -repackageclasses 之后 ConnLogDao 被改名成 t60，而下面的
#   -keep class **$Properties
# 把 com.openapi.myapp.bean.form.ConnLogDao$Properties 原样留着了，
# 于是运行时去找 t60$Properties，ClassNotFoundException → DAOConfig 初始化失败 → 整个数据库挂掉。
# 修复：Dao 类只保留名字（不改包、不重命名），两边就始终对得上。
-keep class **$Properties
-keepnames class * extends org.greenrobot.greendao.AbstractDao
-dontwarn org.greenrobot.greendao.**

#--------mobvista
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.mbridge.** {*; }
-keep interface com.mbridge.** {*; }
-dontwarn com.mbridge.**
-keepclassmembers class **.R$* { public static final int mbridge*; }

-keep public class com.mbridge.* extends androidx.** { *; }
-keep public class androidx.viewpager.widget.PagerAdapter{*;}
-keep public class androidx.viewpager.widget.ViewPager.OnPageChangeListener{*;}
-keep interface androidx.annotation.IntDef{*;}
-keep interface androidx.annotation.Nullable{*;}
-keep interface androidx.annotation.CheckResult{*;}
-keep interface androidx.annotation.NonNull{*;}
-keep public class androidx.fragment.app.Fragment{*;}
-keep public class androidx.core.content.FileProvider{*;}
-keep public class androidx.core.app.NotificationCompat{*;}
-keep public class androidx.appcompat.widget.AppCompatImageView {*;}
-keep public class androidx.recyclerview.*{*;}
-keep class com.mbridge.msdk.foundation.tools.FastKV{*;}
-keep class com.mbridge.msdk.foundation.tools.FastKV$Builder{*;}
-keepclassmembers class com.openapi.myapp.ui.globe.GlobeWebView$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
