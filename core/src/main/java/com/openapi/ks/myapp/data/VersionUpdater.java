package com.openapi.ks.myapp.data;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.openapi.core.CommonConstants;
import com.openapi.common.util.log.LogUtil;
import com.openapi.common.util.android.PreferenceUtils;
import com.openapi.common.util.lang.StringUtils;
import com.openapi.common.util.android.ToastUtil;
import com.openapi.common.net.HttpDNSUtil;
import com.openapi.common.net.HttpClient;
import com.openapi.common.net.request.CommonResponse;
import com.openapi.common.net.request.GsonRequest;
import com.openapi.core.app.AppContext;
import com.openapi.core.model.VersionVo;
import com.openapi.core.config.Constants;
import com.openapi.core.R;

/**
 * 应用版本：读取本机版本号，检查服务端是否有新版本，并应用服务端下发的配置。
 * 更新提示框见 {@link UpdateDialogs}，下载安装见 {@link ApkDownloader}。
 */
public class VersionUpdater {
    private static final String VERSION_TAG = "REQUEST_VERSION_CHECK";
    private static String version;
    private static int build;
    private static boolean isInitVersionSuccess = false;

    /** 检查新版本；有新版本时弹出更新提示，needToast 为 true 时无新版本也提示。 */
    public static void checkUpdate(final Activity context, final boolean needToast) {
        if (!isInitVersionSuccess()) {
            return;
        }
        checkNewVersion(context, new CommonResponse.ResponseOkListener<VersionVo>() {
            @Override
            public void onResponse(final VersionVo vo) {
                setNewVersion(context, vo.maxBuild);
                PreferenceUtils.setPrefString(AppContext.get(), Constants.D_URL, vo.url);
                PreferenceUtils.setPrefBoolean(AppContext.get(), CommonConstants.ADS_SHOW_CONFIG, vo.adsShow);
                PreferenceUtils.setPrefBoolean(AppContext.get(), Constants.LOG_UPLOAD_CONFIG, vo.logUp);
                PreferenceUtils.setPrefBoolean(AppContext.get(), Constants.NEED_DNSPOD_CONFIG, vo.needDnspod);
                PreferenceUtils.setPrefBoolean(AppContext.get(), Constants.NEED_NATIVE_ADS_CONFIG, vo.needNative);
                if (StringUtils.hasText(vo.dnspodIp)) {
                    HttpDNSUtil.DNS_POD_IP = vo.dnspodIp;
                }
                if (isNewVersion(vo.maxBuild) && StringUtils.hasText(vo.url)) {
                    UpdateDialogs.showGoogleUpdate(context, vo, true);
                } else if (needToast) {
                    ToastUtil.showShort(R.string.about_version_update_to_date);
                }
            }
        }, new CommonResponse.ResponseErrorListener() {

        }, VERSION_TAG);
    }

    /** 初始化版本信息（应用启动时调用一次）。 */
    public static void init(Context context) {
        PackageManager manager = context.getPackageManager();
        try {
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            version = info.versionName;
            // 兼容性处理：API 28+ 使用新方法，否则使用旧方法
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                build = (int) info.getLongVersionCode();
            } else {
                build = info.versionCode;
            }
            isInitVersionSuccess = true;
        } catch (Exception e) {
            LogUtil.e(e);
        }
    }

    /** 是否初始化成功，防止读取到错误数据。 */
    public static boolean isInitVersionSuccess() {
        return isInitVersionSuccess;
    }

    /** 是否是新版本（比本机 build 号大）。 */
    public static boolean isNewVersion(int newVersionBuild) {
        return newVersionBuild > build;
    }

    public static String getVersion() {
        return version;
    }

    /** 请求服务端版本信息。 */
    public static void checkNewVersion(Context context, CommonResponse.ResponseOkListener listener,
                                       CommonResponse.ResponseErrorListener errorListener, String tag) {
        GsonRequest request = new GsonRequest(context, Constants.getUrl(Constants.API_VERSION_URL),
                VersionVo.class, null, listener, errorListener);
        request.setTag(tag);
        HttpClient.addRequest(request);
    }

    public static void setNewVersion(Context context, int build) {
        PreferenceUtils.setPrefInt(context, Constants.VERSION_APP_INCOMING, build);
    }
}
