package com.openapi.commons.yewu.ads.adview;

import android.content.Context;
import android.os.Handler;
import androidx.fragment.app.FragmentActivity;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.openapi.commons.yewu.ads.base.AdsContext;
import com.openapi.commons.yewu.ads.base.SplashAdsInter;
import com.openapi.ks.moviefree1.R;

import static com.openapi.commons.yewu.ads.adview.AdviewConstant.ADS_ADVIEW_KEY2;
import static com.openapi.commons.yewu.ads.adview.AdviewConstant.adsKeySet;

/**
 * Created by openapi on 2017/9/20.
 */

public class SplashAdviewAds extends SplashAdsInter {
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_SPREAD;
    }
    @Override
    public  void lanchExit(Context context,RelativeLayout group){
    }
    @Override
    public  void launchAds(final FragmentActivity context, RelativeLayout group, RelativeLayout skipView, final Handler handler){
    }
}
