package com.timeline.vpn.ads.launch;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.ui.main.MainFragment;

import net.youmi.android.AdManager;
import net.youmi.android.spot.SplashView;
import net.youmi.android.spot.SpotDialogListener;
import net.youmi.android.spot.SpotManager;

/**
 * Created by gqli on 2016/3/23.
 */
public class YoumiLaunchController extends LaunchAdsController{

    public YoumiLaunchController(Activity context,ViewGroup adsGroup,Handler handler,ImageButton ibSkip){
        super(context,adsGroup,handler,ibSkip);
    }
    @Override
    public void showAds(){
        ibSkip.setVisibility(View.GONE);
        AdManager.getInstance(mContext).init(Constants.YOUMI_APPID, Constants.YOUMI_APPSECRET, Constants.IS_DEBUG_OPEN);
        SplashView splashView = new SplashView(mContext, null);
        // 设置是否显示倒计时，默认显示
        splashView.setShowReciprocal(true);
        // 设置是否显示关闭按钮，默认不显示
        splashView.hideCloseBtn(true);
        //传入跳转的intent，若传入intent，初始化时目标activity应传入null
        Intent intent = new Intent(mContext, MainFragment.class);
        splashView.setIntent(intent);
        //展示失败后是否直接跳转，默认直接跳转
        splashView.setIsJumpTargetWhenFail(false);
        //获取开屏视图
        View splash = splashView.getSplashView();
        //添加开屏视图到布局中
        adsGroup.addView(splash);
        //显示开屏
        SpotManager.getInstance(mContext)
                .showSplashSpotAds(mContext, splashView, new SpotDialogListener() {
                    @Override
                    public void onShowSuccess() {
                        mHandler.sendEmptyMessage(Constants.ADS_PRESENT_MSG);
                    }

                    @Override
                    public void onShowFailed() {
                        mHandler.sendEmptyMessage(Constants.ADS_NO_MSG);
                    }

                    @Override
                    public void onSpotClosed() {
                        mHandler.sendEmptyMessage(Constants.ADS_CLOSE_MSG);
                    }

                    @Override
                    public void onSpotClick(boolean isWebPath) {
                        mHandler.sendEmptyMessage(Constants.ADS_CLICK_MSG);
                    }
                });
    }
}
