package com.timeline.vpn.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.kyview.manager.AdViewSpreadManager;
import com.sspacee.common.ui.base.LogActivity;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.adview.AdsAdview;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.task.UpdateUserTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

/**
 * Created by themass on 2016/3/22.
 */
public class LaunchActivity extends LogActivity {
    public boolean canJumpImmediately = false;
    @BindView(R.id.rl_spread)
    RelativeLayout ivAds;
    @BindView(R.id.skip_view)
    RelativeLayout skipView;
    @BindView(R.id.tv_jishi)
    TextView tvJishi;
    private int max = Constants.STARTUP_SHOW_TIME_6000;
    private int now = 0;
    private Unbinder unbinder;
    private Runnable mStartMainRunnable = new Runnable() {
        @Override
        public void run() {
            launch();
        }
    };
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("开屏广告handleMessage-" + msg.what);
            switch (msg.what) {
                case Constants.ADS_NO_MSG:
                case Constants.ADS_DISMISS_MSG:
                    launch();
                    break;
                case Constants.ADS_JISHI:
                    now = now + 1000;
                    if (now < max) {
                        tvJishi.setText((max - now) / 1000 + " s");
                        delay1s();
                    } else {
                        tvJishi.setText(R.string.skip);
                    }
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_launch);
        MobAgent.init(this);
        AdsAdview.initConfig(this);
        unbinder = ButterKnife.bind(this);
        UpdateUserTask.start(this);
//        String imei = ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId();
//        LogUtil.i("imei="+imei);
    }

    @OnClick(R.id.skip_view)
    public void skip(View view) {
        launch();
    }

    private void launch() {
        if(!AdViewSpreadManager.hasJumped) {
            Intent intent = new Intent(this, MainFragmentViewPage.class);
            startActivity(intent);
            finish();
        }

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
        canJumpImmediately = true;
        mHandler.postDelayed(mStartMainRunnable, Constants.STARTUP_SHOW_TIME_7000);
        AdsAdview.launchAds(this, ivAds, skipView, mHandler);
        MobAgent.onResume(this);
        delay1s();
    }

    private void delay1s() {
        mHandler.sendEmptyMessageDelayed(Constants.ADS_JISHI, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        canJumpImmediately = false;
        MobAgent.onPause(this);
    }

    @Override
    public void onDestroy() {
        unbinder.unbind();
        mHandler.removeMessages(Constants.ADS_JISHI);
        mHandler.removeCallbacks(mStartMainRunnable);
        AdsAdview.launchAds(this, null, null, null);
        super.onDestroy();
    }
}
