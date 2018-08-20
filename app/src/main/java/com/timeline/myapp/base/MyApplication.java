package com.timeline.myapp.base;

import android.graphics.Typeface;
import android.support.multidex.MultiDexApplication;

import com.sspacee.common.util.DensityUtil;
import com.sspacee.common.util.DeviceInfoUtils;
import com.sspacee.common.util.FileUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.yewu.ads.base.AdsManager;
import com.sspacee.yewu.net.VolleyUtils;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.DBManager;
import com.timeline.myapp.data.ImagePhotoLoad;
import com.timeline.myapp.data.VersionUpdater;

import java.io.File;

import butterknife.ButterKnife;

import static com.sspacee.common.util.FileUtils.GLIDE_PATH;
import static com.sspacee.common.util.FileUtils.LOG_PATH;
import static com.sspacee.common.util.FileUtils.VOLLEY_PATH;

/**
 * Created by themass on 2016/3/1.
 */
public class MyApplication extends MultiDexApplication {
    //    private RefWatcher refWatcher;
    public static final String UPDATE_STATUS_ACTION = "com.timeline.vpn.action.UPDATE_STATUS";
    public static volatile boolean isDebug = true;
    private static MyApplication instance = null;
    public Typeface typeface;
    private ImagePhotoLoad photoLoad;
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
        }else{
            isTemp = true;
        }
        if (MyApplication.isDebug) {
            LogUtil.i("uc=" + uc + "; ad=" + ad);
            DensityUtil.logDensity(this);
            DBManager.getInstance().setDebug();
        }
        long cost = System.currentTimeMillis() - start;
        LogUtil.i("cpu=" + SystemUtils.getCpuType());
        LogUtil.e("app start cost:" + cost);
        photoLoad = new ImagePhotoLoad(this);
        AdsManager.getInstans().init(this);
    }

    public ImagePhotoLoad getPhotoLoad() {
        return photoLoad;
    }

    public void initFilePath() {
        FileUtils.ensureFile(this, FileUtils.getWriteFilePath(this)+File.separator+LOG_PATH);
        FileUtils.ensureFile(this, FileUtils.getWriteFilePath(this)+File.separator+GLIDE_PATH);
        FileUtils.ensureFile(this, FileUtils.getWriteFilePath(this)+File.separator+VOLLEY_PATH);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }
}