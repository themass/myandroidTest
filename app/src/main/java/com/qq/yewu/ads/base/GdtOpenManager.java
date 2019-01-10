package com.qq.yewu.ads.base;

import android.app.Activity;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.qq.e.ads.splash.SplashAD;
import com.qq.e.ads.splash.SplashADListener;
import com.qq.e.comm.util.AdError;
import com.qq.fq2.R;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.task.ScoreTask;

public class GdtOpenManager {
    private TextView skipView;
    private OnGdtOpenListener listener;
    private Activity activity;
    private ViewGroup adContainer;
    private SplashAD splashAD;
    public GdtOpenManager(Activity activity, ViewGroup adContainer, TextView skip, OnGdtOpenListener listener){
        this.skipView = skip;
        this.listener = listener;
        this.activity = activity;
        this.adContainer = adContainer;
    }
    public void showAd(){
        splashAD = new SplashAD(activity, adContainer, skipView, Constants.APPID, Constants.OpenExpressPosID, mSplashAD, Constants.STARTUP_SHOW_TIME_6000);
    }
    private SplashADListener mSplashAD = new SplashADListener(){
        @Override
        public void onADPresent() {
            Log.i("AD_DEMO", "SplashADPresent");
        }

        @Override
        public void onADClicked() {
            Log.i("AD_DEMO", "SplashADClicked");
            String msg = activity.getResources().getString(R.string.tab_fb_click) + Constants.ADS_SHOW_CLICK;
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
            ScoreTask.start(activity, Constants.ADS_SHOW_CLICK);
        }

        /**
         * 倒计时回调，返回广告还将被展示的剩余时间。
         * 通过这个接口，开发者可以自行决定是否显示倒计时提示，或者还剩几秒的时候显示倒计时
         *
         * @param millisUntilFinished 剩余毫秒数
         */
        @Override
        public void onADTick(long millisUntilFinished) {
            Log.i("AD_DEMO", "SplashADTick " + millisUntilFinished + "ms");
            if (skipView != null) {
                skipView.setText(Math.round(millisUntilFinished / 1000f) + " s");
                if (Math.round(millisUntilFinished / 1000f) == 0) {
                    skipView.setText(R.string.skip);
                }
            }
        }
        @Override
        public void onADExposure() {
            Log.i("AD_DEMO", "SplashADExposure");
        }

        @Override
        public void onADDismissed() {
            Log.i("AD_DEMO", "SplashADDismissed");
            if(listener!=null){
                listener.onADDismissed();
            }
        }

        @Override
        public void onNoAD(AdError error) {
            Log.i("AD_DEMO onNoAD", error.toString());
            if(listener!=null){
                listener.onNoAD();
            }
        }
    };
    public interface OnGdtOpenListener{
        public void onADDismissed();
        public void onNoAD();
    }
}
