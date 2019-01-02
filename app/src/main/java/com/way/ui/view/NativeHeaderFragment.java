//package com.way.ui.view;
//
//import android.os.Bundle;
//import android.support.annotation.Nullable;
//import android.support.v4.app.Fragment;
//import android.view.MotionEvent;
//import android.view.View;
//import android.widget.ImageView;
//import android.widget.RelativeLayout;
//import android.widget.TextView;
//
//import com.ads.base.AdsContext;
//import com.ads.base.AdsManager;
//import com.ads.base.NativeAdsReadyListener;
//import com.kyview.natives.NativeAdInfo;
//
//import java.util.Collections;
//import java.util.List;
//
//
///**
// * Created by dengt on 2016/8/21.
// */
//public class NativeHeaderFragment extends Fragment implements NativeAdsReadyListener {
//    private AdsContext.Categrey ca;
//    private static String CATEGREY = "Categrey";
//    RelativeLayout natvieView;
//    TextView desc;
//    ImageView icon;
//    public static NativeHeaderFragment getNewInstans(AdsContext.Categrey ca){
//        NativeHeaderFragment fragment = new NativeHeaderFragment();
//        return fragment;
//    }
//    @Override
//    protected int getRootViewId() {
//        return R.layout.native_ad_item2;
//    }
//    public boolean onAdRecieved(final List<NativeAdInfo> data){
//        if(data){
//            ImagePhotoLoad.loadCommonImg(getActivity(), data.get(0).getIconUrl(),icon);
//            desc.setText(data.get(0).getDesc());
//            data.get(0).onDisplay(new View(
//                    getActivity()));
//            natvieView.setOnTouchListener(new View.OnTouchListener() {
//
//                @Override
//                public boolean onTouch(View v, MotionEvent event) {
//                    data.get(0).onClick(v, (int) event.getX(), (int) event.getY());
//                    return true;
//                }
//            });
//        }
//        return true;
//    }
//    @Override
//    protected void setupViews(View view, Bundle savedInstanceState) {
//        super.setupViews(view, savedInstanceState);
//        ca = (AdsContext.Categrey)getSerializable();
//        AdsManager.getInstans().showNative(getActivity(),this, ca);
//    }
//    @Override
//    public void onDestroyView() {
//        EventBusUtil.getEventBus().unregister(this);
//        super.onDestroyView();
//
//    }
//}
