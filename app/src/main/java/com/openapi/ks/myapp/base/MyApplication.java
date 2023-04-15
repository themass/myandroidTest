package com.openapi.ks.myapp.base;

import android.content.Context;
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
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

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
        if(Constants.APP_MYPOOL.equals(uc)){
            Constants.initUserAgent(Constants.AGENT_APP_MYPOOL);
        }else{
            Constants.initUserAgent(Constants.AGENT_APP_GOOGLE);
            isTemp = true;
        }

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
        CalligraphyConfig.initDefault(
                new CalligraphyConfig.Builder()
                        .setDefaultFontPath("fonts/Roboto-Monospace-Regular.ttf")
                        .setFontAttrId(R.attr.fontPath)
                        .build()
        );
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
    }
