package com.openapi.yewu.ads.mobvista;

import android.os.Handler;
import androidx.fragment.app.FragmentActivity;

import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.mbridge.msdk.out.BannerAdListener;
import com.mbridge.msdk.out.BannerSize;
import com.mbridge.msdk.out.MBBannerView;
import com.mbridge.msdk.out.MBridgeIds;
import com.openapi.common.util.LogUtil;
import com.openapi.myapp.constant.Constants;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.BannerInter;

/**
 * Created by dengt on 2017/9/20.
 */

public class MobivistaBannerAds extends BannerInter {
    @Override
    protected AdsContext.AdsType getAdsType(){
        return AdsContext.AdsType.ADS_TYPE_BANNER;
    }

    @Override
    public void bannerAds(final FragmentActivity context, final ViewGroup group, final String key, final Handler handler){
        try{
            LogUtil.i("mobvi banner"+Constants.Mob_UNIT_BANNER_PLACE+"---"+Constants.adviewToMobvBanner.get(key));
            MBBannerView mbBannerView = new MBBannerView(context);
            mbBannerView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            mbBannerView.init(new BannerSize(BannerSize.LARGE_TYPE,1294,720), Constants.Mob_UNIT_BANNER_PLACE,Constants.adviewToMobvBanner.get(key));
            mbBannerView.setAllowShowCloseBtn(false);
            mbBannerView.setRefreshTime(15);
//            if (mbBannerView != null) {
//                ViewGroup parent = (ViewGroup) mbBannerView.getParent();
//                if (parent != null) {
//                    parent.removeAllViews();
//                }
//                mbBannerView.setTag(key);
//            }else{
//                LogUtil.e("mobvi banner  is error :"+key);
//                return ;
//            }
//            group.addView(mbBannerView);
            mbBannerView.setBannerAdListener(new BannerAdListener() {
                @Override
                public void onLoadFailed(MBridgeIds ids,String errorMsg) {
                    LogUtil.i("mobvi banner onLoadFailed is error :"+errorMsg);
                    noAds(context,handler, AdsContext.AdsFrom.MOBVISTA,0);
                }

                @Override
                public void onLoadSuccessed(MBridgeIds ids) {
                    LogUtil.i("mobvi banner onLoadSuccessed ");
                    readyAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
                }

                @Override
                public void onClick(MBridgeIds ids) {
                    LogUtil.i("mobvi banner onClick "+ids);
                    clickAds(context, handler, AdsContext.AdsFrom.MOBVISTA);
                }

                @Override
                public void onLeaveApp(MBridgeIds ids) {
                    LogUtil.i("mobvi banner onLeaveApp "+ids);
                }

                @Override
                public void showFullScreen(MBridgeIds ids) {
                    LogUtil.i("mobvi banner showFullScreen "+ids);
                    displayAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
                }

                @Override
                public void closeFullScreen(MBridgeIds ids) {
                    LogUtil.i("mobvi banner closeFullScreen "+ids);
                    closeAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
                }

                @Override
                public void onLogImpression(MBridgeIds ids) {
                    LogUtil.i("mobvi banner onLogImpression "+ids);
                }

                @Override
                public void onCloseBanner(MBridgeIds ids) {
                    LogUtil.i("mobvi banner onCloseBanner "+ids);
                    closeAds(context,handler, AdsContext.AdsFrom.MOBVISTA);
                }
            });
            mbBannerView.load();
            LogUtil.i("mobvi banner load");
        } catch (Throwable e) {
            noAds(context,handler, AdsContext.AdsFrom.MOBVISTA,0);
            LogUtil.e(e);
         }
    }
    @Override
    public void bannerExit(FragmentActivity context, ViewGroup group, final String key){
        LogUtil.i("bannerExit:"+key);
        group.removeView(group.findViewWithTag(key));
    }


}
