package com.sspacee.yewu.ads.mobvista;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.mintegral.msdk.MIntegralConstans;
import com.mintegral.msdk.MIntegralSDK;
import com.mintegral.msdk.nativex.view.mtgfullview.StarLevelLayoutView;
import com.mintegral.msdk.out.Campaign;
import com.mintegral.msdk.out.Frame;
import com.mintegral.msdk.out.MIntegralSDKFactory;
import com.mintegral.msdk.out.MtgNativeHandler;
import com.mintegral.msdk.out.MtgWallHandler;
import com.mintegral.msdk.out.NativeListener;
import com.qq.sexfree.R;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.data.ImagePhotoLoad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by dengt on 2017/9/20.
 */

public class FeedImgMobvAds {
    private MtgNativeHandler nativeHandle;
    public void init(Context context) {
        MIntegralSDK sdk = MIntegralSDKFactory.getMIntegralSDK();
        Map<String, Object> preloadMap = new HashMap<String, Object>();
        preloadMap.put(MIntegralConstans.PROPERTIES_LAYOUT_TYPE, MIntegralConstans.LAYOUT_NATIVE);
        preloadMap.put(MIntegralConstans.PROPERTIES_UNIT_ID, Constants.Mob_UNIT_FEED);

        List<NativeListener.Template> list = new ArrayList<NativeListener.Template>();
        list.add(new NativeListener.Template(MIntegralConstans.TEMPLATE_BIG_IMG, 1));
        preloadMap.put(MIntegralConstans.NATIVE_INFO, MtgNativeHandler.getTemplateString(list));
        sdk.preload(preloadMap);
    }

    public void load(final FragmentActivity context, final ViewGroup viewGroup) {
        try {
            Map<String, Object> properties = MtgNativeHandler.getNativeProperties(Constants.Mob_UNIT_FEED);
            nativeHandle = new MtgNativeHandler(properties, context);
            nativeHandle.addTemplate(new NativeListener.Template(MIntegralConstans.TEMPLATE_BIG_IMG, 1));
            nativeHandle.setAdListener(new NativeListener.NativeAdListener() {

                @Override
                public void onAdLoaded(List<Campaign> campaigns, int template) {
                    fillFeedsImageLayout(context,viewGroup,campaigns);
                    init(context);
                    LogUtil.i("mob onAdLoaded:"+campaigns.size());
                }

                @Override
                public void onAdLoadError(String message) {
                    LogUtil.i("mob onAdLoadError:" + message);
//                    AdsManager.getInstans().showBannerAds(context, viewGroup, AdsContext.Categrey.CATEGREY_VPN2);
                }

                @Override
                public void onAdFramesLoaded(List<Frame> list) {
                    LogUtil.i("mob onAdFramesLoaded:"+list.size());

                }

                @Override
                public void onLoggingImpression(int adsourceType) {
                    LogUtil.i("mob onLoggingImpression:"+adsourceType);

                }

                @Override
                public void onAdClick(Campaign campaign) {
                    LogUtil.e("onAdClick");
                }
            });
            nativeHandle.load();
        } catch (Throwable e) {
            LogUtil.e(e);
        }
    }

    protected void fillFeedsImageLayout(Context context,ViewGroup group,List<Campaign> campaigns) {
        View view = LayoutInflater.from(context).inflate(R.layout.mobv_feed_view,null);
        LinearLayout mLl_Root = (LinearLayout) view.findViewById(R.id.mintegral_feeds_ll_root);
        ImageView mIvIcon = (ImageView)view.findViewById(R.id.mintegral_feeds_icon);
        ImageView mIvImage = (ImageView) view.findViewById(R.id.mintegral_feeds_image);
        TextView mTvAppName = (TextView) view.findViewById(R.id.mintegral_feeds_app_name);
        TextView mTvCta = (TextView) view.findViewById(R.id.mintegral_feeds_tv_cta);
        TextView mTvAppDesc = (TextView) view.findViewById(R.id.mintegral_feeds_app_desc);
        StarLevelLayoutView mStarLayout = (StarLevelLayoutView) view.findViewById(R.id.mintegral_feeds_star);
        ImageButton ib = (ImageButton) view.findViewById(R.id.mintegral_feeds_ib_close);
        ib.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                group.setVisibility(View.GONE);
            }
        });
        if (campaigns != null && campaigns.size() > 0) {
            Campaign campaign = campaigns.get(0);
            group.setVisibility(View.VISIBLE);
            if (!TextUtils.isEmpty(campaign.getIconUrl())) {
                ImagePhotoLoad.loadCommonImg(context,campaign.getIconUrl(),mIvIcon);
            }
            if (!TextUtils.isEmpty(campaign.getImageUrl())) {
                ImagePhotoLoad.loadCommonImg(context,campaign.getImageUrl(),mIvImage);
            }
            mTvAppName.setText(campaign.getAppName() + "");
            mTvAppDesc.setText(campaign.getAppDesc() + "");
            mTvCta.setText(campaign.getAdCall());
            int rating = (int) campaign.getRating();
            mStarLayout.setRating(rating);
            nativeHandle.registerView(mLl_Root, campaign);
        }
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) {
            parent.removeAllViews();
        }
        group.addView(view);
    }

}
