package com.openapi.ks.myapp.base;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

import com.openapi.ks.moviefree1.R;
import com.openapi.commons.common.util.DensityUtil;
import com.openapi.commons.common.util.DeviceInfoUtils;
import com.openapi.commons.common.util.FileUtils;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.common.util.SystemUtils;
import com.openapi.commons.yewu.ads.base.AdsManager;
import com.openapi.commons.yewu.net.VolleyUtils;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.data.DBManager;
import com.openapi.ks.myapp.data.ImagePhotoLoad;
import com.openapi.ks.myapp.data.VersionUpdater;

import java.io.File;

import butterknife.ButterKnife;
import io.github.inflationx.calligraphy3.CalligraphyConfig;
import io.github.inflationx.calligraphy3.CalligraphyInterceptor;
import io.github.inflationx.viewpump.ViewPump;

import static com.openapi.commons.common.CommonConstants.tmpFilePath;


/**
 * Created by openapi on 2016/3/1.
 */
public class MyApplication extends MultiDexApplication {
    //    private RefWatcher refWatcher;
    public static volatile boolean isDebug = true;
    private static MyApplication instance = null;
    public Typeface typeface;
    private ImagePhotoLoad photoLoad;
    public static final String UPDATE_STATUS_ACTION = "com.openapi.ks.moviefree1.action.UPDATE_STATUS";
    public static boolean isTemp = false;

    //    public static RefWatcher getRefWatcher(Context context) {
//        MyApplication application = (MyApplication) context.getApplicationContext();
//        return application.refWatcher;
//    }
    public static MyApplication getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isDebug = SystemUtils.isApkDebugable(this);
        LogUtil.e("isDebug=" + isDebug);
        typeface = Typeface.SANS_SERIF;
        instance = this;
        long start = System.currentTimeMillis();
//        refWatcher = LeakCanary.install(this);
        ButterKnife.setDebug(isDebug);
        VersionUpdater.init(this);
        VolleyUtils.init();
        initFilePath();
        DBManager.getInstance().init(this);
        String uc = DeviceInfoUtils.getMetaData(this, "UMENG_CHANNEL");
        String ad = DeviceInfoUtils.getMetaData(this, "AdView_CHANNEL");
        Constants.initUserAgent(Constants.AGENT_APP_MYPOOL);
        isTemp = false;
        //欺骗应用市场
//        if(Constants.APP_MYPOOL.equals(uc)){
//            Constants.initUserAgent(Constants.AGENT_APP_MYPOOL);
//        }else{
//            Constants.initUserAgent(Constants.AGENT_APP_GOOGLE);
//            isTemp = true;
//        }

        LogUtil.i("uc=" + uc + "; ad=" + ad);
        if (MyApplication.isDebug) {
            Constants.initUserAgent(Constants.AGENT_APP_MYPOOL);
            DensityUtil.logDensity(this);
            DBManager.getInstance().setDebug();
            isTemp =  false;
        }
        long cost = System.currentTimeMillis() - start;
        LogUtil.i("cpu=" + SystemUtils.getCpuType());
        LogUtil.e("app start cost:" + cost);
        photoLoad = new ImagePhotoLoad(this);
        AdsManager.getInstans().init(this);
//        CalligraphyConfig.initDefault(
//                new CalligraphyConfig.Builder()
//                        .setDefaultFontPath("fonts/Roboto-Monospace-Regular.ttf")
//                        .setFontAttrId(R.attr.fontPath)
//                        .build()
//        );
        ViewPump.init(ViewPump.builder()
                .addInterceptor(new CalligraphyInterceptor(new CalligraphyConfig.Builder()
                        .setDefaultFontPath("fonts/Roboto-Monospace-Regular.ttf")
                        .setFontAttrId(R.attr.fontPath)
                        .build())).build());
        ijkInit();
        initChat();
    }

    public ImagePhotoLoad getPhotoLoad() {
        return photoLoad;
    }

    private void initFilePath() {
        tmpFilePath = FileUtils.getWriteFilePath(this)+ File.separator+"log";
        LogUtil.i("tmpFilePath=" + tmpFilePath);
        FileUtils.ensureFile(this, tmpFilePath);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    private static SharedPreferences mPreferences, metaData, eqPref;
    public static SharedPreferences getmPreferences() {
        return mPreferences;
    }

    public static SharedPreferences getMetaData() {
        return metaData;
    }

    public static SharedPreferences getEqPref() {
        return eqPref;
    }

    private void ijkInit(){
        //EXOPlayer内核，支持格式更多
//        PlayerFactory.setPlayManager(Exo2PlayerManager.class);
//        //系统内核模式
//        PlayerFactory.setPlayManager(SystemPlayerManager.class);
        //ijk内核，默认模式
//        PlayerFactory.setPlayManager(IjkPlayerManager.class);


        //exo缓存模式，支持m3u8，只支持exo
//        CacheFactory.setCacheManager(ExoPlayerCacheManager.class);
        //代理缓存模式，支持所有模式，不支持m3u8等，默认
//        CacheFactory.setCacheManager(ProxyCacheManager.class);
    }

    public void initChat(){
//        //初始化工具类
//        Utils.init(this);
//        GsonUtils.setGsonDelegate(new Gson());
//        // 应用程序入口处调用，避免手机内存过小，杀死后台进程后通过历史intent进入Activity造成SpeechUtility对象为null
//        // 如在Application中调用初始化，需要在Mainifest中注册该Applicaiton
//        // 注意：此接口在非主进程调用会返回null对象，如需在非主进程使用语音功能，请增加参数：SpeechConstant.FORCE_LOGIN+"=true"
//        // 参数间使用半角“,”分隔。
//        // 设置你申请的应用appid,请勿在'='与appid之间添加空格及空转义符
//        // 注意： appid 必须和下载的SDK保持一致，否则会出现10407错误
//        LogUtil.e("初始化讯飞");
//        SpeechUtility createUtility = SpeechUtility.createUtility(this, "appid=" + "");
//        LogUtil.e("初始化讯飞 " + createUtility);
    }
}
