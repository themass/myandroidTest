package com.openapi.commons.yewu.ads.adview;

import android.os.Handler;
import androidx.fragment.app.FragmentActivity;
import android.view.View;
import android.view.ViewGroup;

import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.commons.yewu.ads.base.BannerInter;

/**
 * Created by openapi on 2017/9/20.
 */

public class BannerAdviewAds extends BannerInter {
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_SPREAD;
    }
    @Override
    public void bannerAds(final FragmentActivity context, final ViewGroup group,final String key, final Handler handler){
    }
    @Override
    public void bannerExit(FragmentActivity context,ViewGroup group,final String key){
        LogUtil.i("bannerExit:"+key);
        group.removeView(group.findViewWithTag(key));
    }


}
