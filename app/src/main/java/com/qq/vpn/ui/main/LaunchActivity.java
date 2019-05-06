package com.qq.vpn.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.qq.common.ui.base.LogActivity;
import com.qq.common.util.EventBusUtil;
import com.qq.common.util.LogUtil;
import com.qq.common.util.PermissionHelper;
import com.qq.common.util.PreferenceUtils;
import com.qq.common.util.SystemUtils;
import com.qq.yewu.ads.base.AdsContext;
import com.qq.yewu.ads.base.AdsManager;
import com.qq.yewu.ads.base.GdtOpenManager;
import com.qq.yewu.ads.config.LaunchAdsNext;
import com.qq.yewu.um.MobAgent;
import com.qq.fq2.R;
import com.qq.myapp.constant.Constants;
import com.qq.myapp.task.LoginTask;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

import static com.qq.yewu.ads.adview.AdviewConstant.ADS_ADVIEW_KEY1;

/**
 * Created by dengt on 2016/3/22.
 */
public class LaunchActivity extends LogActivity implements GdtOpenManager.OnGdtOpenListener {
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
    private GdtOpenManager gdtOpenManager;
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
        AdsManager.getInstans().init(this);
        unbinder = ButterKnife.bind(this);
        LoginTask.start(this);
        gdtOpenManager = new GdtOpenManager(this,ivAds,tvJishi,this);
        mHandler.postDelayed(mStartMainRunnable, max);
        boolean gdt = PreferenceUtils.getPrefBoolean(this,Constants.AD_GDT_SWITCH,true);
        EventBusUtil.getEventBus().register(this);
        perm =PermissionHelper.checkPermission(this,PermissionHelper.READ_PHONE_STATE);
        if(SystemUtils.isZH(this) && gdt && perm){
            gdtOpenManager.showAd();
        }else{
            showAdview();
        }

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
        if(perm){
            AdsManager.getInstans().showSplashAds(AdsContext.AdsFrom.ADVIEW,this,ivAds,skipView);
        }else{
            LogUtil.i("MOBVISTA");
            AdsManager.getInstans().showSplashAds(AdsContext.AdsFrom.MOBVISTA,this,ivAds,skipView);
//            onBannerShow(null);
        }
        delay1s();
    }
    private void delay1s() {
        mHandler.sendEmptyMessageDelayed(Constants.ADS_JISHI, 1000);
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onBannerShow(LaunchAdsNext next){
        if(next.from== AdsContext.AdsFrom.ADVIEW){
            AdsManager.getInstans().showSplashAds(AdsContext.AdsFrom.MOBVISTA,this,ivAds,skipView);
        }else {
            llBanner.setVisibility(View.VISIBLE);
            ivAds.setVisibility(View.GONE);
            AdsManager.getInstans().showBannerAds(this, banner1, AdsContext.Categrey.CATEGREY_VPN2);
//            AdsManager.getInstans().showBannerAds(this, banner2, AdsContext.Categrey.CATEGREY_VPN3);
//            AdsManager.getInstans().showBannerAds(this, banner3, AdsContext.Categrey.CATEGREY_VPN1);
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
    }
    public void onADDismissed(){
//        launch();
    }
    public void onNoAD(){
        showAdview();
    }
}
