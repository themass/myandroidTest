package com.qq;

import android.graphics.Typeface;
import android.support.multidex.MultiDexApplication;

import com.qq.ads.base.AdsManager;
import com.qq.ext.network.HttpUtils;
import com.qq.ext.network.VolleyUtils;
import com.qq.ext.util.DensityUtil;
import com.qq.ext.util.DeviceInfoUtils;
import com.qq.ext.util.FileUtils;
import com.qq.ext.util.LogUtil;
import com.qq.ext.util.StringUtils;
import com.qq.ext.util.SystemUtils;
import com.qq.vpn.support.DBManager;
import com.qq.vpn.support.ImagePhotoLoad;
import com.qq.vpn.support.VersionUpdater;

import java.io.File;

import butterknife.ButterKnife;

import static com.qq.ext.util.FileUtils.GLIDE_PATH;
import static com.qq.ext.util.FileUtils.LOG_PATH;
import static com.qq.ext.util.FileUtils.VOLLEY_PATH;


/**
 * Created by dengt on 2016/3/1.
 */
public class MyApplication extends MultiDexApplication {
    public static volatile boolean isDebug = true;
    private static MyApplication instance = null;
    public Typeface typeface;
    private ImagePhotoLoad photoLoad;
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
        ButterKnife.setDebug(isDebug);
        VolleyUtils.init();
        initFilePath();
        DBManager.getInstance().init(this);
        if(!StringUtils.hasText(Constants.NetWork.uc)){
            Constants.NetWork.uc = DeviceInfoUtils.getMetaData(this, "UMENG_CHANNEL");
        }
        if (Constants.NetWork.UA_APP_SUFFIX == null) {
            Constants.NetWork.UA_APP_SUFFIX = HttpUtils.getUserAgentSuffix(this);
        }
        if (MyApplication.isDebug) {
            LogUtil.i("uc=" + Constants.NetWork.uc);
            DensityUtil.logDensity(this);
            DBManager.getInstance().setDebug();
        }
        long cost = System.currentTimeMillis() - start;
        LogUtil.i("cpu=" + SystemUtils.getCpuType());
        LogUtil.e("app start cost:" + cost);
        photoLoad = new ImagePhotoLoad(this);
        AdsManager.getInstans().init(this);
        VersionUpdater.init(this);
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
