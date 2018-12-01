//package com.qq.yewu.um;
//
//import android.content.Context;
//import android.os.Handler;
//import android.os.Message;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.RelativeLayout;
//
//import com.kyview.InitConfiguration;
//import com.kyview.interfaces.AdViewInstlListener;
//import com.kyview.interfaces.AdViewNativeListener;
//import com.kyview.interfaces.AdViewSpreadListener;
//import com.kyview.manager.AdViewInstlManager;
//import com.kyview.manager.AdViewNativeManager;
//import com.kyview.manager.AdViewSpreadManager;
//import com.kyview.natives.NativeAdInfo;
//import com.qq.common.util.LogUtil;
//import com.qq.yewu.ads.base.AdsControllerInte;
//import com.qq.yewu.um.AdsContext;
//import com.qq.yewu.ads.base.NativeAdsReadyListener;
//import com.timeline.vpn.R;
//import com.timeline.vpn.ui.main.MainFragmentViewPage;
//
//import java.util.ArrayList;
//import java.util.List;
//
//import static com.qq.yewu.ads.adview.AdviewConstant.ADS_ADVIEW_KEY;
//import static com.qq.yewu.ads.adview.AdviewConstant.adsKeySet;
//import static com.qq.yewu.ads.adview.AdviewConstant.adsKeySet1;
//
///**
// * Created by dengt on 2016/8/19.
// */
//public class AdsAdview implements AdsControllerInte {


//
//    @Override
//    public void exitApp(Context context) {
//
//    }
//
//    @Override
//    public void lanchExit(Context context) {
//
//    }
//
//    @Override
//    public View nativeIntersAds(Context context, Handler handler) {
//        return null;
//    }
//
//    @Override
//    public void nativeVideoAds(Context context, Handler handler, ViewGroup group) {
//
//    }
//
//    @Override
//    public void onStart(Context context) {
//
//    }
//
//    @Override
//    public void onResume(Context context) {
//
//    }
//
//    @Override
//    public void onPause(Context context) {
//
//    }
//
//    @Override
//    public void onStop(Context context) {
//
//    }
//
//    @Override
//    public void onDestroy(Context context) {
//
//    }
//    @Override
//    public  void bannerExit(Context context,ViewGroup view){
//        if(view!=null){
//            view.removeAllViews();
//        }
//    }
//    @Override
//    public void offerAds(Context context,final Handler handler){
//        Message msg = Message.obtain();
//        msg.obj = new AdsContext.AdsMsgObj(AdsContext.AdsType.ADS_TYPE_OFFER, AdsContext.AdsShowStatus.ADS_DISMISS_MSG, AdsContext.AdsFrom.ADVIEW);
//        handler.sendMessage(msg);
//    }
//    @Override
//    public void videoAds(Context context,Handler handler){}
//}
