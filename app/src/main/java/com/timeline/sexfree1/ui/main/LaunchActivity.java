package com.timeline.sexfree1.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.qq.sexfree.R;
import com.sspacee.common.ui.base.LogActivity;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.common.util.SystemUtils;
import com.sspacee.yewu.ads.base.AdsContext;
import com.sspacee.yewu.ads.base.AdsManager;
import com.sspacee.yewu.ads.base.GdtOpenManager;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.myapp.constant.Constants;
import com.timeline.myapp.task.LoginTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

/**
 * Created by themass on 2016/3/22.
 */
public class LaunchActivity extends LogActivity implements GdtOpenManager.OnGdtOpenListener {
    @BindView(R.id.rl_spread)
    RelativeLayout ivAds;
    @BindView(R.id.skip_view)
    RelativeLayout skipView;
    @BindView(R.id.tv_shu)
    TextView tvJishi;
    private int max = Constants.STARTUP_SHOW_TIME_6000+1000;
    private int now = 0;
    private Unbinder unbinder;
    private GdtOpenManager gdtOpenManager;
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
        gdtOpenManager = new GdtOpenManager(this,ivAds,tvJishi,this);
        boolean gdt = PreferenceUtils.getPrefBoolean(this,Constants.AD_GDT_SWITCH,true);
        if(SystemUtils.isZH(this) && gdt){
            gdtOpenManager.showAd();
        }else{
            showAdview();
        }
        mHandler.postDelayed(mStartMainRunnable, max);
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
        AdsManager.getInstans().showSplashAds(AdsContext.AdsFrom.ADVIEW,this,ivAds,skipView);
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
    public void onADDismissed(){
//        launch();
    }
    public void onNoAD(){
        showAdview();
    }
}
