package com.ks.sexfree1.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.sspacee.common.ui.base.LogActivity;
import com.sspacee.common.util.LogUtil;
import com.sspacee.yewu.ads.base.AdsManager;
import com.sspacee.yewu.ads.youmi.YoumiAds;
import com.sspacee.yewu.um.MobAgent;
import com.ks.myapp.constant.Constants;
import com.ks.myapp.task.LoginTask;
import com.ks.sexfree1.R;

import net.youmi.android.nm.cm.ErrorCode;
import net.youmi.android.nm.sp.SplashViewSettings;
import net.youmi.android.nm.sp.SpotListener;
import net.youmi.android.nm.sp.SpotManager;
import net.youmi.android.nm.sp.SpotRequestListener;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

/**
 * Created by themass on 2016/3/22.
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
        YoumiAds.init(this);
        SpotManager.getInstance(this).requestSpot(new SpotRequestListener(){
            public void onRequestSuccess(){
                LogUtil.i("youmi onRequestSuccess");
            }
            public void onRequestFailed(int var1){
                LogUtil.i("youmi onRequestFailed");
            }
        });

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
        setupSplashAd();
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
    private void setupSplashAd() {

        // 对开屏进行设置
        SplashViewSettings splashViewSettings = new SplashViewSettings();
        //		// 设置是否展示失败自动跳转，默认自动跳转
        splashViewSettings.setAutoJumpToTargetWhenShowFailed(false);
        // 设置跳转的窗口类
        splashViewSettings.setTargetClass(MainFragmentViewPage.class);
        // 设置开屏的容器
        splashViewSettings.setSplashViewContainer(ivAds);

        // 展示开屏广告
        SpotManager.getInstance(this)
                .showSplash(this, splashViewSettings, new SpotListener() {
                    @Override
                    public void onShowSuccess() {
                        LogUtil.i("开屏展示成功");
                    }

                    @Override
                    public void onShowFailed(int errorCode) {
                        LogUtil.i("开屏展示失败");
                        AdsManager.getInstans().showSplashAds(LaunchActivity.this,ivAds,skipView);
                        switch (errorCode) {
                            case ErrorCode.NON_NETWORK:
                                LogUtil.i("网络异常");
                                break;
                            case ErrorCode.NON_AD:
                                LogUtil.i("暂无开屏广告");
                                break;
                            case ErrorCode.RESOURCE_NOT_READY:
                                LogUtil.i("开屏资源还没准备好");
                                break;
                            case ErrorCode.SHOW_INTERVAL_LIMITED:
                                LogUtil.i("开屏展示间隔限制");
                                break;
                            case ErrorCode.WIDGET_NOT_IN_VISIBILITY_STATE:
                                LogUtil.i("开屏控件处在不可见状态");
                                break;
                            default:
                                LogUtil.i("errorCode: "+ errorCode);
                                break;
                        }
                    }

                    @Override
                    public void onSpotClosed() {
                        LogUtil.i("开屏被关闭");
                    }

                    @Override
                    public void onSpotClicked(boolean isWebPage) {
                        LogUtil.i("是否是网页广告？"+(isWebPage ? "是" : "不是"));
                    }
                });
    }

    @Override
    public void onDestroy() {
        unbinder.unbind();
        mHandler.removeMessages(Constants.ADS_JISHI);
        mHandler.removeCallbacks(mStartMainRunnable);
        AdsManager.getInstans().exitSplashAds(this,ivAds);
        SpotManager.getInstance(this).onDestroy();
        super.onDestroy();
    }
}
