package com.openapi.common.ads.base;

import android.app.Activity;
import android.os.Handler;
import android.view.ViewGroup;

/**
 * Created by dengt on 2017/9/20.
 * <p>
 * 参数从 FragmentActivity 放宽到 Activity（台账 §4/§7）：新的 Compose 页面
 * （放映场、首页标签）宿主是 ComponentActivity，banner 实现只用到 Activity API；
 * 旧的 FragmentActivity 调用方原样编译。
 */

public abstract class BannerInter extends AdsEventAdaptor implements BaseAdsInter {
    public abstract void bannerAds(Activity context, ViewGroup group, String key, Handler handler);

    public abstract void bannerExit(Activity context, ViewGroup group, String key);

}
