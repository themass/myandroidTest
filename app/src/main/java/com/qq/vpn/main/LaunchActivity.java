package com.qq.vpn.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.kyview.manager.AdViewSpreadManager;
import com.qq.MobAgent;
import com.qq.ads.base.AdsManager;
import com.qq.vpn.support.task.LoginTask;
import com.qq.vpn.ui.base.actvity.LogActivity;
import com.qq.fq3.R;
import com.qq.Constants;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

/**
 * Created by dengt on 2016/3/22.
 */
public class LaunchActivity extends LogActivity {
    @BindView(R.id.rl_spread)
    RelativeLayout ivAds;
    @BindView(R.id.skip_view)
    RelativeLayout skipView;
    @BindView(R.id.tv_shu)
    TextView tvJishi;
    private int max = Constants.STARTUP_SHOW_TIME_3000;
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
            now = now + 1000;
            if (now < max) {
                tvJishi.setText((max - now) / 1000 + " s");
                delay1s();
            } else {
                tvJishi.setText(R.string.skip);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_launch);
        MobAgent.init(this);
        unbinder = ButterKnife.bind(this);
        LoginTask.start(this);
    }

    @OnClick(R.id.skip_view)
    public void skip(View view) {
        launch();
    }

    private void launch() {
        if(!AdViewSpreadManager.hasJumped) {
            Intent intent = new Intent(this, MainActivity.class);
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
        mHandler.postDelayed(mStartMainRunnable, Constants.STARTUP_SHOW_TIME_3000);
        AdsManager.getInstans().showSplashAds(this,ivAds,skipView);
        MobAgent.onResume(this);
        AdsManager.getInstans().reqVideo(this);
        delay1s();
    }

    private void delay1s() {
        mHandler.sendEmptyMessageDelayed(Constants.ADS_JISHI, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobAgent.onPause(this);
    }

    @Override
    public void onDestroy() {
        unbinder.unbind();
        mHandler.removeMessages(Constants.ADS_JISHI);
        mHandler.removeCallbacks(mStartMainRunnable);
        AdsManager.getInstans().exitSplashAds(this,ivAds);
        super.onDestroy();
    }
}
