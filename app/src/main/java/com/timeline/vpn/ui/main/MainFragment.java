package com.timeline.vpn.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTabHost;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

import com.sspacee.common.util.EventBusUtil;
import com.sspacee.common.util.LogUtil;
import com.sspacee.common.util.PreferenceUtils;
import com.sspacee.yewu.ads.adview.AdsAdview;
import com.sspacee.yewu.um.MobAgent;
import com.timeline.vpn.R;
import com.timeline.vpn.constant.Constants;
import com.timeline.vpn.data.UserLoginUtil;
import com.timeline.vpn.data.config.ConfigActionJump;
import com.timeline.vpn.data.config.LogAddTofile;
import com.timeline.vpn.service.LogUploadService;
import com.timeline.vpn.ui.base.app.BaseDrawerActivity;
import com.timeline.vpn.ui.inte.OnBackKeyUpListener;
import com.timeline.vpn.ui.maintab.TabVipFragment;
import com.timeline.vpn.ui.maintab.TabVpnFragment;

import org.strongswan.android.logic.CharonVpnService;

/**
 * Created by themass on 2016/3/1.
 */
public class MainFragment extends BaseDrawerActivity implements TabHost.OnTabChangeListener {
    private static final String ADS_TAG = "ADS_TAG";
    private FragmentTabHost mTabHost;
    private TabWidget mainTab;
    private long firstTime = 0;
    private boolean pendingIntroAnimation;
    private OnBackKeyUpListener keyListener;
    private ConfigActionJump jump = new ConfigActionJump();
    private LogAddTofile logAdd = new LogAddTofile();
    private Toast destoryToast = null;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_fragment);
        EventBusUtil.getEventBus().register(jump);
        EventBusUtil.getEventBus().register(logAdd);
        EventBusUtil.getEventBus().register(this);
        AdsAdview.initConfig(this);
        AdsAdview.init(this);
        UserLoginUtil.initData(this);
        boolean uploadLog = PreferenceUtils.getPrefBoolean(this, Constants.LOG_UPLOAD_CONFIG, false);
        if (uploadLog) {
            startService(new Intent(this, LogUploadService.class));
        }
        destoryToast = Toast.makeText(this, R.string.close_over, Toast.LENGTH_SHORT);
    }

    public void setListener(OnBackKeyUpListener keyListener) {
        this.keyListener = keyListener;
    }

    public void setupView() {
        mTabHost = (FragmentTabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup(this, getSupportFragmentManager(), R.id.realtabcontent);
        mTabHost.setOnTabChangedListener(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        addTab(inflater, R.string.tab_tag_index, TabVpnFragment.class,
                R.drawable.ac_bg_tab_index, R.string.tab_index, null);
        addTab(inflater, R.string.tab_tag_vip, TabVipFragment.class,
                R.drawable.ac_bg_tab_index, R.string.tab_vip, null);
        mTabHost.getTabWidget().setDividerDrawable(null);
        mainTab = mTabHost.getTabWidget();
        startService(CharonVpnService.class);
    }

    private View addTab(LayoutInflater inflater, int tag, Class clss,
                        int icon, int title, Bundle args) {
        View indicator = inflater.inflate(R.layout.main_tab_widget_item_layout,
                mTabHost.getTabWidget(), false);
        ImageView imgView = (ImageView) indicator.findViewById(R.id.navi_icon);
        TextView titleView = (TextView) indicator.findViewById(R.id.navi_title);
        imgView.setImageResource(icon);
        titleView.setText(title);
        mTabHost.addTab(mTabHost.newTabSpec(getString(tag)).setIndicator(indicator), clss,
                args);
        return indicator;
    }

    @Override
    public void onTabChanged(String tabId) {

    }

    @Override
    public void onDestroy() {
        LogUtil.i("main destory");
        stopService(CharonVpnService.class);
        stopService(LogUploadService.class);
        EventBusUtil.getEventBus().unregister(jump);
        EventBusUtil.getEventBus().unregister(logAdd);
        super.onDestroy();
        MobAgent.killProcess(this);
        System.exit(0);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (keyListener != null) {
                keyListener.onkeyBackUp();
            }
            if (CharonVpnService.VPN_STATUS_NOTIF) {
                moveTaskToBack(true);
                return true;
            }
            long secondTime = System.currentTimeMillis();
            if (secondTime - firstTime > 2000) {
                destoryToast.show();
                firstTime = secondTime;//更新firstTime
                return true;
            } else {
                //两次按键小于2秒时，退出应用
                destoryToast.cancel();
                super.onKeyUp(keyCode, event);
            }
        }
        return super.onKeyUp(keyCode, event);
    }
}
