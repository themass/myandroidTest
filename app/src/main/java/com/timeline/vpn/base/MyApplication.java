package com.timeline.vpn.base;

import android.graphics.Typeface;
import android.support.multidex.MultiDexApplication;

import com.sspacee.common.util.DensityUtil;
import com.sspacee.common.util.DeviceInfoUtils;
import com.sspacee.common.util.FileUtils;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.yewu.ads.base.AdsManager;
import com.sspacee.yewu.net.VolleyUtils;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.DBManager;
import com.timeline.vpn.data.ImagePhotoLoad;
import com.timeline.vpn.data.VersionUpdater;

import butterknife.ButterKnife;

import static com.sspacee.common.CommonConstants.tmpFilePath;

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
        if (MyApplication.isDebug) {
            String uc = DeviceInfoUtils.getMetaData(this, "UMENG_CHANNEL");
            String ad = DeviceInfoUtils.getMetaData(this, "AdView_CHANNEL");
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

    private void initFilePath() {
        tmpFilePath = FileUtils.getWriteFilePath(this, Constants.FILE_TMP_PATH);
        LogUtil.i("tmpFilePath=" + tmpFilePath);
        FileUtils.ensureFile(this, tmpFilePath);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }
}
