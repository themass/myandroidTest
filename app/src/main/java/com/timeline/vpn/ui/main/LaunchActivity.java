package com.timeline.vpn.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.widget.RelativeLayout;

import com.sspacee.common.ui.base.LogActivity;
import com.sspacee.common.util.LogUtil;
import com.timeline.vpn.R;
import com.timeline.vpn.ads.adview.AdsAdview;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.MobAgent;
import com.timeline.vpn.task.UpdateUserTask;
import com.umeng.message.PushAgent;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by themass on 2016/3/22.
 */
public class LaunchActivity extends LogActivity {
    public boolean canJumpImmediately = false;
    @Bind(R.id.rl_spread)
    RelativeLayout ivAds;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LogUtil.i("handleMessage-" + msg.what);
            switch (msg.what) {
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
        MobAgent.init(this);
        AdsAdview.initConfig();
        mHandler.postDelayed(mStartMainRunnable, Constants.STARTUP_SHOW_TIME_5000);
        ButterKnife.bind(this);
        UpdateUserTask.start(this);
        LogUtil.i("push token=" + PushAgent.getInstance(this).getRegistrationId());

    }

    private void launch() {
        Intent intent = new Intent(this, MainFragment.class);
        startActivity(intent);
        mHandler.removeCallbacks(mStartMainRunnable);
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
        canJumpImmediately = true;
        AdsAdview.launchAds(this, ivAds, mHandler);
        MobAgent.onResume(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        canJumpImmediately = false;
        MobAgent.onPause(this);
    }

    @Override
    public void onDestroy() {
        ButterKnife.unbind(this);
        mHandler.removeCallbacks(mStartMainRunnable);
        AdsAdview.launchAds(this, null, null);
        super.onDestroy();
    }
}
