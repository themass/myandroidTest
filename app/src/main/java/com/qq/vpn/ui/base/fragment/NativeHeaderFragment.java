package com.qq.vpn.ui.base.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.kyview.natives.NativeAdInfo;
import com.qq.ads.base.AdsContext;
import com.qq.ads.base.AdsManager;
import com.qq.ads.base.NativeAdsReadyListener;
import com.qq.ext.util.CollectionUtils;
import com.qq.ext.util.EventBusUtil;
import com.qq.network.R;
import com.qq.vpn.support.ImagePhotoLoad;

import java.util.List;

import butterknife.BindView;

/**
 * Created by dengt on 2016/8/21.
 */
public class NativeHeaderFragment extends BaseFragment implements NativeAdsReadyListener {
    private AdsContext.Categrey ca;
    private static String CATEGREY = "Categrey";
    @Nullable
    @BindView(R.id.natvieView)
    RelativeLayout natvieView;
    @Nullable
    @BindView(R.id.desc)
    TextView desc;
    @Nullable
    @BindView(R.id.icon)
    ImageView icon;
    public static NativeHeaderFragment getNewInstans(AdsContext.Categrey ca){
        NativeHeaderFragment fragment = new NativeHeaderFragment();
        fragment.putSerializable(ca);
        return fragment;
    }
    @Override
    protected int getRootViewId() {
        return R.layout.native_ad_item2;
    }
    public boolean onAdRecieved(final List<NativeAdInfo> data){
        if(!CollectionUtils.isEmpty(data)){
            ImagePhotoLoad.loadCommonImg(getActivity(), data.get(0).getIconUrl(),icon);
            desc.setText(data.get(0).getDesc());
            data.get(0).onDisplay(new View(
                    getActivity()));
            natvieView.setOnTouchListener(new View.OnTouchListener() {

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    data.get(0).onClick(v, (int) event.getX(), (int) event.getY());
                    return true;
                }
            });
        }
        return true;
    }
    @Override
    protected void setupViews(View view, Bundle savedInstanceState) {
        super.setupViews(view, savedInstanceState);
        ca = (AdsContext.Categrey)getSerializable();
        AdsManager.getInstans().showNative(getActivity(),this, ca);
    }
    @Override
    public void onDestroyView() {
        EventBusUtil.getEventBus().unregister(this);
        super.onDestroyView();

    }
}
