#
# 对于一些基本指令的添加
#
#############################################
# 代码混淆压缩比，在0~7之间，默认为5，一般不做修改
-optimizationpasses 5
# 混合时不使用大小写混合，混合后的类名为小写
-dontusemixedcaseclassnames
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
-keep public class * extends android.app.Appliction
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View
# 保留support下的所有类及其内部类
-keep class android.support.** {*;}
# 保留继承的
-keep public class * extends android.support.v4.**
-dontnote android.support.v4.**
-keep public class * extends android.support.v7.**
-keep public class * extends android.support.annotation.**
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
#umeng
-dontnote com.umeng.analytics.**
-dontnote com.umeng.message.**
-dontwarn com.umeng.**
-keep class com.umeng.**{*;}
-keep class u.aly.**{*;}
-keep class com.google.**{*;}
-dontnote u.aly.**
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
#-keep public class * implements com.bumptech.glide.module.GlideModule
#-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
#  **[] $VALUES;
#  public *;
#}
#-keep class com.bumptech.glide.** { *; }
##-keepresourcexmlelements manifest/application/meta-data@value=GlideModule
-keep class com.baidu.** { *;}
-keep class android.support.v4.app.NotificationCompat**{ *; }
-keep class MTT.ThirdAppInfoNew { *; }

# Preserve all native method names and the names of their classes.
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
-keep class com.qq.vpn.domain.**{ *; }
-keep class org.strongswan.android.logic.**{ *; }
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
#-----------   log   ------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
}
#------------adview-----------------------
#-----------------gdt---------------------
-keep class com.qq.e.** {*; }
-keep class com.tencent.**{*; }
-keep class com.tencent.lbsapi.*
-keep class com.tencent.lbsapi.core.*
-keep class android.support.v4.app.NotificationCompat**{
    public *;
}
-keep class android.support.v4.**{ *;}
#-----------------end---------------------
#竞价相关
-keep public class com.kyview.** {*;}
-keep public class com.kuaiyou.** {*;}
#以下为部分聚合相关的，如果有相关包名的警告，根据提示log添加dontwarn即可
-keepclassmembers class * {public *;}
-keep public class * {public *;}
-keep class com.baidu.mobads.** {
public protected *;
}
-keep class com.qq.e.** {
public protected *;
}
-keep class com.tencent.gdt.**{
public protected *;
}
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose
-keep class com.moat.analytics.**{*;}
-dontwarn com.moat.analytics.**
-dontwarn com.dropbox.**
-keep class LBSAPIProtocol.*
-keeppackagenames I
-keep class I.* {*;}
-keepattributes Exceptions
-keepattribute Signature
-keepattribute Deprecated
-keepattributes *Annotation*
-keep public class com.kyview.** {*;}
-dontwarn com.kyview.**
-dontnote com.kyview.**
-dontwarn com.kuaiyou.**
-dontnote com.kuaiyou.**
-dontwarn android.app.**
-dontnote android.app.**
-dontwarn pl.droidsonroids.**
-dontnote pl.droidsonroids.**
-dontwarn com.androidquery.**
-dontnote com.androidquery.**
-dontwarn com.bytedance.**
-dontnote com.bytedance.**
-dontwarn org.greenrobot.**
-dontnote org.greenrobot.**
-dontwarn okhttp3.internal.**
-dontnote okhttp3.internal.**
-dontwarn com.bumptech.**
-dontnote com.bumptech.**
-dontwarn com.android.org.**
-dontnote com.android.org.**
-dontwarn com.google.gson.**
-dontnote com.google.gson.**
-dontwarn com.qq.**
-dontnote com.qq.**
-dontwarn android.arch.**
-dontnote android.arch.**
-dontwarn com.amazon.**
-dontnote com.amazon.**
-dontwarn com.etiennelawlor.**
-dontnote com.etiennelawlor.**
-keep public class com.google.android.gms.**
-dontwarn com.google.android.gms.**
-dontwarn com.squareup.picasso.**
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient{
     public *;
}
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient$Info{
     public *;
}
-keep public class com.bytedance.** {*;}
-keep public class com.kuaiyou.** {*;}
-keep public class com.google.android.gms.ads.** {
   public *;
}
-keep public class com.google.ads.** {
   public *;
}

-keepattributes SourceFile,LineNumberTable
-keep public class com.google.android.gms.**
-dontwarn com.google.android.gms.**
-dontwarn com.squareup.picasso.**
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient{
     public *;
}
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient$Info{
     public *;
}
# skip the Picasso library classes
-keep class com.squareup.picasso.** {*;}
-dontwarn com.squareup.picasso.**
-dontwarn com.squareup.okhttp.**
# skip Moat classes
-keep class com.moat.** {*;}
-dontwarn com.moat.**
# skip AVID classes
-keep class com.integralads.avid.library.* {*;}



-keepattributes SourceFile,LineNumberTable
-keep class com.inmobi.** { *; }
-dontwarn com.inmobi.**
-keep public class com.google.android.gms.**
-dontwarn com.google.android.gms.**
-dontwarn com.squareup.picasso.**
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient{public *;}
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient$Info{public *;}
#skip the Picasso library classes
-keep class com.squareup.picasso.** {*;}
-dontwarn com.squareup.picasso.**
-dontwarn com.squareup.okhttp.**
#skip Moat classes
-keep class com.moat.** {*;}
-dontwarn com.moat.**
#skip AVID classes
-keep class com.integralads.avid.library.** {*;}

-keepattributes Signature
-keepattributes *Annotation*
-keep class com.mintegral.** {*; }
-keep interface com.mintegral.** {*; }
-keep class android.support.v4.** { *; }
-dontwarn com.mintegral.**
-keep class **.R$* { public static final int mintegral*; }
-keep class com.alphab.** {*; }
-keep interface com.alphab.** {*; }
#-------------------greenrobot greendao------------------------
-keep class org.greenrobot.greendao.**{*;}
-keepclassmembers class * extends org.greenrobot.greendao.AbstractDao {
public static java.lang.String TABLENAME;
}
-keep class **$Properties
-dontwarn org.greenrobot.greendao.**
-keep class net.sqlcipher.database.**{*;}
-keep class rx.**{*;}
