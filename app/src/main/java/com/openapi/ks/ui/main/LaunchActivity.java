package com.openapi.ks.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.openapi.common.ui.base.LogActivity;
import com.openapi.common.util.EventBusUtil;
import com.openapi.common.util.LogUtil;
import com.openapi.myapp.data.config.SplashAdDissmisEvent;
import com.openapi.yewu.ads.base.AdsContext;
import com.openapi.yewu.ads.base.AdsManager;
import com.openapi.yewu.ads.config.LaunchAdsNext;
import com.openapi.yewu.um.MobAgent;
import com.openapi.ks.free1.R;
import com.openapi.myapp.constant.Constants;
import com.openapi.myapp.task.LoginTask;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

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
    @BindView(R.id.rl_banner1)
    RelativeLayout banner1;
    @BindView(R.id.rl_banner2)
    RelativeLayout banner2;
    @BindView(R.id.rl_banner3)
    RelativeLayout banner3;
    @BindView(R.id.ll_banner)
    LinearLayout llBanner;
    private int max = Constants.STARTUP_SHOW_TIME_6000+1000;
    private int now = 0;
    private Unbinder unbinder;
    boolean perm = true;
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
        mHandler.postDelayed(mStartMainRunnable, max);
        EventBusUtil.getEventBus().register(this);
        showAdview();

    }

    @OnClick(R.id.skip_view)
    public void skip(View view) {
        if(getResources().getText(R.string.skip).equals(tvJishi.getText()))
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
        MobAgent.onResume(this);
    }
    private void showAdview(){
        AdsManager.getInstans().showSplashAds(AdsContext.AdsFrom.MOBVISTA,this,ivAds,skipView);
        delay1s();
    }
    private void delay1s() {
        mHandler.sendEmptyMessageDelayed(Constants.ADS_JISHI, 1000);
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onBannerShow(LaunchAdsNext next){
        LogUtil.i("LaunchAdsNext "+next.from);
        if(next.from== AdsContext.AdsFrom.MOBVISTA){
            AdsManager.getInstans().showSplashAds(AdsContext.AdsFrom.ADMOB,this,ivAds,skipView);
        }
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
        EventBusUtil.getEventBus().unregister(this);
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onADDismissed(SplashAdDissmisEvent event){
        launch();
    }
}
