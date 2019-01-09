package com.sspacee.yewu.ads.base;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import com.qq.e.ads.interstitial.AbstractInterstitialADListener;
import com.qq.e.ads.interstitial.InterstitialAD;
import com.qq.e.comm.util.AdError;
import com.qq.e.comm.util.GDTLogger;
import com.qq.sexfree.R;
import com.sspacee.common.util.Utils;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.task.ScoreTask;
import com.timeline.sexfree1.ui.main.MainFragmentViewPage;

public class GdtInterManger {
    private  InterstitialAD iad;
    private Activity activity;
    private OnGdtInterListener listener;
    public GdtInterManger(Activity activity,OnGdtInterListener listener){
        this.activity = activity;
        iad = new InterstitialAD(activity, Constants.APPID, Constants.InterExpressPosID);
        this.listener = listener;
    }
    public GdtInterManger(Activity activity,OnGdtInterListener listener,String posId) {
        this.activity = activity;
        iad = new InterstitialAD(activity, Constants.APPID, posId);
        this.listener = listener;
    }
        public void showAd(){
        iad.setADListener(new AbstractInterstitialADListener() {

            @Override
            public void onNoAD(AdError error) {
                Log.i(
                        "AD_DEMO",
                        String.format("LoadInterstitialAd Fail, error code: %d, error msg: %s",
                                error.getErrorCode(), error.getErrorMsg()));
                if(listener!=null){
                    listener.onNoAD();
                }else{
                    AdsContext.showNext(activity);
                }
            }
            public void onADClicked() {
                GDTLogger.i("ON InterstitialAD Clicked");
                String msg = activity.getResources().getString(R.string.tab_fb_click) + Constants.ADS_SHOW_CLICK;
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                ScoreTask.start(activity, Constants.ADS_SHOW_CLICK);
            }
            @Override
            public void onADReceive() {
                Log.i("AD_DEMO", "onADReceive");
                iad.show();
            }
        });
        iad.loadAD();
//        Utils.hideSoftInput(activity);
    }
    public interface OnGdtInterListener{
        public void onNoAD();
    }
}
