package com.openapi.ks.moviefree1.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.openapi.commons.common.ui.base.LogActivity;
import com.openapi.commons.common.util.LogUtil;
import com.openapi.commons.yewu.ads.base.AdsManager;
import com.openapi.commons.yewu.um.MobAgent;
import com.openapi.ks.myapp.constant.Constants;
import com.openapi.ks.myapp.task.LoginTask;
import com.openapi.ks.moviefree1.R;


import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

/**
 * Created by openapi on 2016/3/22.
 */
public class LaunchActivity extends LogActivity {
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
        Intent intent = new Intent(this, MainFragmentViewPage.class);
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
        mHandler.postDelayed(mStartMainRunnable, Constants.STARTUP_SHOW_TIME_6000);
        MobAgent.onResume(this);
        AdsManager.getInstans().showSplashAds(this,ivAds,skipView);
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
    /**
     * 设置开屏广告
     */


    @Override
    public void onDestroy() {
        unbinder.unbind();
        mHandler.removeMessages(Constants.ADS_JISHI);
        mHandler.removeCallbacks(mStartMainRunnable);
        AdsManager.getInstans().exitSplashAds(this,ivAds);
        super.onDestroy();
    }
}
