//package com.sspacee.yewu.ads.base;
//
//import android.content.Context;
//import android.os.Handler;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.RelativeLayout;
//
///**
// * Created by themass on 2017/9/15.
// */
//
//public interface AdsControllerInte {
//    public  void init(Context context);
//    public  void exitApp(Context context);
//
//    public  void lanchExit(Context context);
//    public  void launchAds( Context mContext, RelativeLayout group, RelativeLayout skipView,Handler handler);
//
//    public  void bannerAds(Context context,  ViewGroup group, Handler handler);
//    public  void bannerExit(Context context,ViewGroup view);
//    public  void interstitialAds( Context context,Handler handler,final boolean score) ;
//    public  View nativeIntersAds( Context context, Handler handler) ;
//    public  void nativeVideoAds(Context context,  Handler handler, ViewGroup group) ;
//    public  void nativeAds( Context context,  Handler handler,  NativeAdsReadyListener listener);
//    public void videoAds(Context context,Handler handler);
//    public void offerAds(Context context,Handler handler);
//
//    public void onStart(Context context) ;
//    public void onResume(Context context) ;
//    public void onPause(Context context) ;
//    public void onStop(Context context);
//    public void onDestroy(Context context) ;
//}
