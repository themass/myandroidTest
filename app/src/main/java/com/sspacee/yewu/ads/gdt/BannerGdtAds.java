package com.sspacee.yewu.ads.gdt;

import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.ViewGroup;

import com.qq.e.ads.banner.ADSize;
import com.qq.e.ads.banner.AbstractBannerADListener;
import com.qq.e.ads.banner.BannerView;
import com.qq.e.comm.util.AdError;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.BannerInter;

/**
 * Created by themass on 2017/9/20.
 */

public class BannerGdtAds extends BannerInter {
    private static final String BANNER_TAG="BANNER_TAG";
    public void bannerAds(final FragmentActivity context, final ViewGroup group, final Handler handler){
        final BannerView bv = new BannerView(context, ADSize.BANNER, GdtConstants.APPID, GdtConstants.bannerId);
        bv.setRefresh(8);
        bv.setADListener(new AbstractBannerADListener() {
            @Override
            public void onNoAD(AdError error) {
                noAds(context,handler, AdsContext.AdsFrom.GDT);
            }

            @Override
            public void onADReceiv() {
                readyAds(context,handler, AdsContext.AdsFrom.GDT);
                bv.setTag("BANNER_TAG");
                if(group!=null){
                    group.removeAllViews();
                }
                group.addView(bv);
            }
        });
        bv.loadAD();
    }
    public void bannerExit(FragmentActivity context,ViewGroup group){
        View view = group.findViewWithTag(BANNER_TAG);
        if(view!=null && view instanceof BannerView){
            ((BannerView)view).destroy();
        }
    }

    public void errorHandle(AdError error){
       LogUtil.e(String.format("gdt Banner onNoAD，eCode = %d, eMsg = %s", error.getErrorCode(),
                        error.getErrorMsg()));
    }
}
