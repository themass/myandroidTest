#############################################
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
-dontoptimize
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
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
-keepattributes EnclosingMethod
-dontoptimize
#-----------------------jar----------------------------------
-keep class android.webkit.JavascriptInterface
-dontwarn android.webkit.JavascriptInterface
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
-dontnote okhttp3.internal.**
-dontwarn java.nio.file.Files
-dontwarn java.nio.file.Path
-dontwarn java.nio.file.OpenOption
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontnote  dfs.eww.ere.**
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
-keep class com.squareup.** {*;}
-keepattributes JavascriptInterface
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-dontnote com.google.android.gms.**
# Proguard config for project using GMS

#-keepnames @com.google.android.gms.common.annotation.KeepName class
#    com.google.android.gms.**,
#    com.google.ads.**
#
#-keepclassmembernames class
#    com.google.android.gms.**,
#    com.google.ads.** {
#        @com.google.android.gms.common.annotation.KeepName *;
#    }
#-keep class
#    com.google.android.gms.**,
#    com.google.ads.**
#    extends java.util.ListResourceBundle {
#         protected java.lang.Object[][] getContents();
#    }
#-keepnames class
#    com.google.android.gms.**,
#    com.google.ads.**
#    implements android.os.Parcelable {
#        public static final ** CREATOR;
#    }
-dontnote com.facebook.Session
-dontnote com.facebook.FacebookSdk
-keepnames class com.facebook.Session {}
-keepnames class com.facebook.FacebookSdk {}

# android.app.Notification.setLatestEventInfo() was removed in
# Marsmallow, but is still referenced (safely)
-keep class android.location.Location.** { *; }
-dontwarn android.location.Location
-dontnote android.location.Location
-keep class com.squareup.leakcanary.** { *; }
-keep class com.tencent.** { *; }
-dontnote com.tencent.**
-keep class cn.domob.android.** { *; }
-dontwarn cn.domob.android.**
-dontwarn oauth.**
-dontwarn com.android.auth.TwitterHandle.**

-keep class oauth.** { *; }
-keep class com.android.auth.TwitterHandle.** { *; }
#--------------------------------------------------------
# Only required if you use AsyncExecutor
-keepclassmembers class * extends org.greenrobot.eventbus.util.ThrowableFailureEvent {
    <init>(java.lang.Throwable);
}
-dontwarn net.youmi.android.**
-keep class net.youmi.android.** {
    *;
}
-keep class com.umeng.message.** { *; }
-dontnote com.umeng.analytics.**
-dontnote com.umeng.message.**
-dontnote com.bumptech.glide.**
-keep class com.baidu.** { *;}
-keep class android.support.v4.app.NotificationCompat**{ *; }
-keep class MTT.ThirdAppInfoNew { *; }
-keep public class com.kyview.** {*;}
-dontwarn com.kyview.**
-dontnote com.kyview.**
-keep public class com.kuaiyou.** {*;}

-keepclassmembers class * {public *;}
-keep public class * {public *;}
-keep public class com.wooboo.** {*;}
-keep public class cn.aduu.android.**{*;}
-keep public class com.wqmobile.** {*;}
-keep class com.qq.e.** {public protected *;}
-keep class com.mobisage.android.** {*;}
-keep interface com.mobisage.android.** {*;}
-keep class com.msagecore.** {*;}
-keep interface com.msagecore.** {*;}
-keep class com.five.adwoad.** {*;}
-keep class com.emar.escore.sdk.ui.**{*;}
-keep class com.inmobi.**{ *; }
-keep public class cn.waps.** {*;}
-keep public interface cn.waps.** {*;}
-keep class com.adzhidian.** { *; }
-keep public class cn.immob.sdk.** {*;}
-keep class com.guohead.mix.*{ *; }
-keep public class cn.aduu.android.**{*;}
-keep class com.otomod.ad.** {*;}
-keep class org.OpenUDID.** {*;}
-keepattributes Exceptions
-keepattribute Signature
-keepattribute Deprecated
-keepattributes *Annotation*
-dontwarn com.chance.**
-dontwarn com.android.volley.NetworkDispatcher
-flattenpackagehierarchy com.chance.v4
-keep class * extends android.app.Service {public *;}
-keep class com.chance.** {*;}
-keep class com.chukong.android.crypto.** {*;}
-keep class com.suizong.mobile.** {*;}
-keep class com.go2map.mapapi.** {*;}
-keep public class cn.Immob.sdk.** {*;}
-keep public class cn.Immob.sdk.controller.** {*;}
-keep class net.youmi.android.** {*;}
-keeppackagenames cn.smartmad.ads.android
-keeppackagenames I
-keep class cn.smartmad.ads.android.* {*;}
-keep class I.* {*;}
-keep public class MobWin.*
-keep public class MobWin.cnst.*
-keep class LBSAPIProtocol.*
-keeppackagenames com.adchina.android.ads
-keeppackagenames com.adchina.android.ads.controllers
-keeppackagenames com.adchina.android.ads.views
-keeppackagenames com.adchina.android.ads.animations
-keep class com.adchina.android.ads.*{*;}
-keep class com.adchina.android.ads.controllers.*{*;}
-keep class com.adchina.android.ads.views.*{*;}
-keep class com.adchina.android.ads.animations.*{*;}
-keep class com.mediav.** {*;}
-keep class org.adver.score.**{*;}
-keep class com.easou.ecom.mads.** {public protected *;}
-keep class com.adroi.sdk.** {public protected *;}
-keep class com.mopanspot.sdk.**{*;}
-keep class com.imopan.plugin.spot.** {*;}
-keep class com.jd.**{*;}
-keep class cn.pro.ad.sdk.*
-dontwarn com.dropbox.**
-dontwarn com.inmobi.**
-keep class com.moat.analytics.**{*;}
-dontwarn com.moat.analytics.**
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
-keep class com.timeline.vpn.bean.**{ *; }
-keep class org.strongswan.android.logic.**{ *; }
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

#feedback sdk
-keep class com.alibaba.sdk.android.feedback.impl.FeedbackServiceImpl {*;}
-keep class com.alibaba.sdk.android.feedback.impl.FeedbackAPI {*;}
-keep class com.alibaba.sdk.android.feedback.util.IWxCallback {*;}
-keep class com.alibaba.sdk.android.feedback.FeedbackService{*;}
-keep class com.alibaba.sdk.android.feedback.util.IUnreadCountCallback{*;}
-keep public class com.alibaba.mtl.log.model.LogField {public *;}
-keep class com.taobao.securityjni.**{*;}
-keep class com.taobao.wireless.security.**{*;}
-keep class com.ut.secbody.**{*;}
-keep class com.taobao.dp.**{*;}
-keep class com.alibaba.wireless.security.**{*;}
-keep class com.ta.utdid2.device.**{*;}
-keep class com.ut.mini.**{*;}
