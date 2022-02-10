package com.qq.myapp.ui.base;

import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.Nullable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.qq.common.ui.base.BaseFragment;
import com.qq.common.util.CollectionUtils;
import com.qq.common.util.EventBusUtil;
import com.qq.common.util.LogUtil;
import com.qq.ks.free1.R;
import com.qq.myapp.bean.vo.LocationVo;
import com.qq.myapp.bean.vo.NativeAdInfo;
import com.qq.myapp.data.ImagePhotoLoad;
import com.qq.myapp.data.UserLoginUtil;
import com.qq.myapp.data.config.HindBannerEvent;
import com.qq.yewu.ads.base.AdsContext;
import com.qq.yewu.ads.base.AdsManager;
import com.qq.yewu.ads.base.NativeAdsReadyListener;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

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
//            ImagePhotoLoad.loadCommonImg(getActivity(), data.get(0).getIconUrl(),icon);
////            desc.setText(data.get(0).getDesc());
////            data.get(0).onDisplay(new View(
////                    getActivity()));
////            natvieView.setOnTouchListener(new View.OnTouchListener() {
////
////                @Override
////                public boolean onTouch(View v, MotionEvent event) {
////                    data.get(0).onClick(v, (int) event.getX(), (int) event.getY());
////                    return true;
////                }
////            });
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
