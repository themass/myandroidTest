package com.timeline.vpn.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.timeline.vpn.R;
import com.timeline.vpn.ads.launch.LaunchAdsController;
import com.timeline.vpn.ads.launch.LaunchProxy;
import com.timeline.vpn.common.util.LogUtil;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.MobAgent;
import com.timeline.vpn.ui.base.LogActivity;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by gqli on 2016/3/22.
 */
public class LaunchActivity extends LogActivity {
    @Bind(R.id.launch_skip)
    ImageButton ibSkip;
    @Bind(R.id.lauch_ads)
    RelativeLayout ivAds;
    LaunchProxy proxy;
    public boolean canJumpImmediately = false;
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-" + msg.what);
            switch (msg.what){
                case Constants.ADS_NO_MSG:
                    next();
                    break;
                case Constants.ADS_CLICK_MSG:
                    break;
                case Constants.ADS_PRESENT_MSG:
                    ivAds.setVisibility(View.VISIBLE);
                    ivAds.startAnimation(AnimationUtils.loadAnimation(LaunchActivity.this, R.anim.anim_splash_enter));
                    break;
                case Constants.ADS_DISMISS_MSG:
                    launch();
                    break;
                default:
                    break;
            }
        }
    };
    private Runnable mStartMainRunnable = new Runnable() {
        @Override
        public void run() {
            launch();
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_launch);
        mHandler.postDelayed(mStartMainRunnable, Constants.STARTUP_SHOW_TIME_8000);
        ButterKnife.bind(this);
        proxy = new LaunchProxy(LaunchProxy.DEAFULT_STRATEGY,this,ivAds,mHandler,ibSkip);
    }
    @OnClick(R.id.launch_skip)
    public void onSkip(View view){
        launch();
    }
    private void launch() {
        proxy.init();
        Intent intent = new Intent(this,MainFragment.class);
        startActivity(intent);
        finish();
    }
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        next();
        canJumpImmediately = true;
        MobAgent.onResume(this);
    }
    private void next(){
        LogUtil.i("next");
        LaunchAdsController controller = proxy.getController();
        if (controller != null) {
            LogUtil.i("show");
            controller.showAds();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        canJumpImmediately = false;
        MobAgent.onPause(this);
    }
}
