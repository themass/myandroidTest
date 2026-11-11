package com.openapi.myapp.base;

import android.app.Application;
import android.graphics.Typeface;



import com.openapi.myapp.data.ImagePhotoLoad;
import com.openapi.yewu.ads.base.AdsManager;
import com.openapi.yewu.net.VolleyUtils;
import com.openapi.common.util.DensityUtil;
import com.openapi.common.util.DeviceInfoUtils;
import com.openapi.common.util.FileUtils;
import com.openapi.common.util.LogUtil;
import com.openapi.common.util.SystemUtils;
import com.openapi.myapp.data.DBManager;
import com.openapi.myapp.data.VersionUpdater;

import java.io.File;

import static com.openapi.common.util.FileUtils.GLIDE_PATH;
import static com.openapi.common.util.FileUtils.LOG_PATH;
import static com.openapi.common.util.FileUtils.VOLLEY_PATH;

/**
 * Created by dengt on 2016/3/1.
 */
public class MyApplication extends Application {
    //    private RefWatcher refWatcher;
    public static final String UPDATE_STATUS_ACTION = "com.openapi.free.action.UPDATE_STATUS";
    public static volatile boolean isDebug = true;
    private static MyApplication instance = null;
    public Typeface typeface;
    private ImagePhotoLoad photoLoad;
    public String uc = null;

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
        VersionUpdater.init(this);
        VolleyUtils.init();
        initFilePath();
        DBManager.getInstance().init(this);
        uc = DeviceInfoUtils.getMetaData(this, "UMENG_CHANNEL");
        String ad = DeviceInfoUtils.getMetaData(this, "AdView_CHANNEL");
        if (MyApplication.isDebug) {
            LogUtil.i("uc=" + uc + "; ad=" + ad);
            DensityUtil.logDensity(this);
            DBManager.getInstance().setDebug();
        }
        long cost = System.currentTimeMillis() - start;
        LogUtil.i("cpu=" + SystemUtils.getCpuType());
        LogUtil.e("app start cost:" + cost);
        com.openapi.common.util.CrashHandler.install();
        AppContainer.init(this);
        // 每天打开 App 就把最近几天的使用汇总补发一次：用户可能连着几天不点断开，
        // 只在断开时上报会让时长数据一直空着。做过节流，这里不需要判断。
        com.openapi.myapp.data.UsageReport.requestFlush(this);
        photoLoad = new ImagePhotoLoad(this);
        // 广告 SDK 初始化放到首帧之后，不占用启动的主线程时间（开屏广告在启动页结束后才请求，来得及）
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> AdsManager.getInstans().init(this));
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
